package com.cookandroid.phantom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class ScanMonitorService : Service() {

    private val SERVICE_NOTIFICATION_ID = 1
    private val CHANNEL_ID_SERVICE = "PhantomMonitorChannel"
    private val CHANNEL_ID_ALERT = "ScanAlertChannel"

    private lateinit var packageReceiver: PackageInstallationReceiver

    override fun onCreate() {
        super.onCreate()

        // 1. 포그라운드 서비스 알림 채널 생성 및 서비스 시작
        createServiceNotificationChannel()
        val notification = createServiceNotification()
        startForeground(SERVICE_NOTIFICATION_ID, notification)

        // 2. 앱 설치 이벤트를 받을 리시버 등록
        registerPackageReceiver()

        Log.d("MonitorService", "ScanMonitorService started and receiver registered.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 서비스가 강제 종료돼도 시스템이 가능한 경우 다시 시작하도록 요청
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // 서비스 종료 시 리시버 등록 해제
        unregisterReceiver(packageReceiver)
        Log.d("MonitorService", "ScanMonitorService destroyed and receiver unregistered.")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null // 이 서비스는 바인딩되지 않습니다.
    }

    /**
     * 포그라운드 서비스의 영구 알림 채널을 생성합니다.
     */
    private fun createServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Phantom 백그라운드 모니터링",
                NotificationManager.IMPORTANCE_DEFAULT // 낮은 중요도로 설정하여 방해를 최소화
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * 포그라운드 서비스 시작 시 사용할 알림 객체를 생성합니다.
     */
    private fun createServiceNotification(): Notification {
        // 알림 클릭 시 메인 페이지로 이동하도록 설정
        val notificationIntent = Intent(this, MainPageActivity::class.java)
        // 포그라운드 알림은 인텐트 데이터가 필요 없으므로 기존 FLAG_IMMUTABLE 유지
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        // NotificationCompat.Builder를 사용하여 알림 생성
        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("Phantom 보안 감시 중")
            .setContentText("앱 설치 상태를 실시간으로 감시하고 있습니다.")
            // ⚠️ R.drawable.ic_stat_name 대신 프로젝트에 존재하는 작은 아이콘 리소스 ID를 사용해야 합니다.
            .setSmallIcon(R.drawable.ghost_angry)
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * ACTION_PACKAGE_ADDED 이벤트를 수신할 리시버를 등록합니다.
     */
    private fun registerPackageReceiver() {
        packageReceiver = PackageInstallationReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED) // 앱 설치 시 이벤트
            // 반드시 'package' 스키마를 추가해야 패키지 이벤트 수신 가능
            addDataScheme("package")
        }
        // NOTE: Android 8.0 (API 26) 이상부터는 암시적 브로드캐스트 리시버는 Context.registerReceiver()로만 등록 가능합니다.
        registerReceiver(packageReceiver, filter)
    }

    /**
     * 새 앱 설치 이벤트를 처리하는 BroadcastReceiver 내부 클래스입니다.
     */
    inner class PackageInstallationReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
                // 설치된 앱의 패키지 이름 가져오기
                val packageName = intent.data?.schemeSpecificPart

                // 우리 앱 자신이 설치된 경우(업데이트 등)와 시스템 패키지 이벤트 처리 방지
                if (packageName != null && packageName != context.packageName) {
                    Log.i("MonitorService", "New app installed: $packageName - Showing scan alert.")
                    showScanNotification(context, packageName)
                }
            }
        }

        private fun showScanNotification(context: Context, packageName: String) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // 각 패키지별로 고유한 ID를 사용해야 알림이 덮어쓰이지 않습니다.
            val NOTIFICATION_ID = packageName.hashCode()

            // ⭐️ 알림 ID를 PendingIntent의 요청 코드(requestCode)로도 사용합니다.
            val REQUEST_CODE = NOTIFICATION_ID

            // Android 8.0 (Oreo) 이상에서 알림 채널 생성 (경고 채널)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_ALERT,
                    "검사 필요 알림",
                    NotificationManager.IMPORTANCE_HIGH // 높은 중요도로 설정하여 사용자에게 눈에 띄게 함
                )
                notificationManager.createNotificationChannel(channel)
            }

            val scanIntent = Intent(context, MainPageActivity::class.java).apply {
                putExtra("PACKAGE_TO_SCAN", packageName)

                // 🚨 수정: NEW_TASK와 CLEAR_TOP을 함께 사용해 Activity를 재사용하도록 강력하게 지시
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_CANCEL_CURRENT
            }

            // ⭐️ requestCode에 고유한 ID(REQUEST_CODE)를 사용합니다.
            val pendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE, // 🚨 수정: 알림마다 고유한 REQUEST_CODE 사용
                scanIntent,
                flag // 🚨 수정: FLAG_CANCEL_CURRENT 사용
            )

            // 알림 빌드
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
                .setContentTitle("🚨 새로운 앱 설치 감지!")
                .setContentText("새로 설치된 앱 ($packageName)의 보안 검사가 필요합니다. 클릭하여 검사하세요.")
                .setSmallIcon(R.drawable.ghost_angry) // ⚠️ 적절한 아이콘으로 변경
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
}