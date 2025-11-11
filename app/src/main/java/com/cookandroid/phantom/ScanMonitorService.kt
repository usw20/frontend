package com.cookandroid.phantom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cookandroid.phantom.util.PackageInstallationReceiver

class ScanMonitorService : Service() {

    companion object {
        private const val TAG = "ScanMonitorService"
        private const val SERVICE_NOTIFICATION_ID = 1
        private const val CHANNEL_ID_SERVICE = "PhantomMonitorChannel"
        private const val CHANNEL_ID_NEW_APP = "PhantomNewAppChannel"
        private const val POLLING_INTERVAL = 3000L // 3초마다 확인
    }

    private lateinit var packageReceiver: PackageInstallationReceiver
    private val handler = Handler(Looper.getMainLooper())
    private var previousPackages = setOf<String>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "📱 ScanMonitorService onCreate 호출됨")

        // 1. 알림 채널 미리 생성
        initNotificationChannels()

        // 2. 포그라운드 서비스 시작
        val notification = createServiceNotification()
        startForeground(SERVICE_NOTIFICATION_ID, notification)

        // 3. 브로드캐스트 리시버 등록
        registerPackageReceiver()

        // 4. 폴링 시작
        startPackagePolling()

        Log.d(TAG, "✅ ScanMonitorService started successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 ScanMonitorService onDestroy 호출됨")

        try {
            unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "리시버 등록 해제 오류: ${e.message}")
        }

        handler.removeCallbacksAndMessages(null)
        previousPackages = emptySet()

        Log.d(TAG, "✅ ScanMonitorService destroyed")
    }

    override fun onBind(intent: Intent): IBinder? = null

    /**
     * Android 8+ 알림 채널을 미리 생성합니다.
     * - 채널을 미리 생성하면 알림 표시 시 성능 향상
     * - 리소스 ID 오류 방지
     */
    private fun initNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = getSystemService(NotificationManager::class.java)

        // 1️⃣ 포그라운드 서비스 채널 (낮은 우선순위)
        val serviceChannel = NotificationChannel(
            CHANNEL_ID_SERVICE,
            "🛡️ Phantom 백그라운드 모니터링",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "앱 설치 감시 중"
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        // 2️⃣ 새로운 앱 감지 채널 (높은 우선순위)
        val newAppChannel = NotificationChannel(
            CHANNEL_ID_NEW_APP,
            "🚨 보안 경고",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "새로운 앱 설치 감지 시 경고"
            enableLights(true)
            lightColor = Color.RED
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500)
            setShowBadge(true)
        }

        notificationManager?.apply {
            createNotificationChannel(serviceChannel)
            createNotificationChannel(newAppChannel)
            Log.d(TAG, "✅ 알림 채널 2개 생성 완료")
        }
    }

    /**
     * 포그라운드 서비스용 알림을 생성합니다.
     * 사용자가 제거할 수 없는 고정 알림입니다.
     */
    private fun createServiceNotification(): Notification {
        val intent = Intent(this, MainPageActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_notification_ghost) // 👻 유령 아이콘
            .setContentTitle("🛡️ Phantom 보안 감시 중")
            .setContentText("앱 설치를 실시간으로 감시하고 있습니다.")
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 사용자가 스와이프로 제거 불가
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * 브로드캐스트 리시버를 등록합니다.
     * ACTION_PACKAGE_ADDED 이벤트를 감지합니다.
     */
    private fun registerPackageReceiver() {
        try {
            packageReceiver = PackageInstallationReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(packageReceiver, filter)
            }

            Log.d(TAG, "✅ 브로드캐스트 리시버 등록 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 리시버 등록 실패: ${e.message}")
        }
    }

    /**
     * 3초마다 설치된 앱 목록을 확인하여 새로운 앱을 감지합니다.
     * (브로드캐스트 리시버 백업 역할)
     */
    private fun startPackagePolling() {
        Log.d(TAG, "📊 폴링 시작")
        previousPackages = getCurrentInstalledPackages()

        handler.postDelayed(object : Runnable {
            override fun run() {
                try {
                    val currentPackages = getCurrentInstalledPackages()
                    val newPackages = currentPackages - previousPackages

                    if (newPackages.isNotEmpty()) {
                        Log.d(TAG, "📱 새로운 앱 감지: $newPackages")
                        newPackages.forEach { packageName ->
                            if (packageName != this@ScanMonitorService.packageName) {
                                showNewAppNotification(packageName)
                            }
                        }
                    }

                    previousPackages = currentPackages
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 폴링 중 오류: ${e.message}")
                }

                handler.postDelayed(this, POLLING_INTERVAL)
            }
        }, POLLING_INTERVAL)
    }

    /**
     * 현재 설치된 모든 사용자 앱의 패키지명 집합을 반환합니다.
     * (시스템 앱 제외)
     */
    private fun getCurrentInstalledPackages(): Set<String> {
        return try {
            packageManager.getInstalledApplications(0)
                .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { it.packageName }
                .toSet()
        } catch (e: Exception) {
            Log.e(TAG, "설치된 앱 목록 조회 실패: ${e.message}")
            emptySet()
        }
    }

    /**
     * 새로운 앱 설치 감지 알림을 표시합니다.
     * 클릭 시 해당 앱을 자동으로 검사합니다.
     */
    private fun showNewAppNotification(packageName: String) {
        if (packageName == this.packageName) {
            Log.d(TAG, "⏭️ Phantom 앱 자신이므로 무시")
            return
        }

        try {
            // 앱 이름 조회
            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (e: Exception) {
                Log.w(TAG, "앱 이름 조회 실패: $packageName")
                packageName
            }

            Log.d(TAG, "🔔 알림 표시: $appName ($packageName)")

            // 클릭 시 AppScanActivity로 이동
            val scanIntent = Intent(this, AppScanActivity::class.java).apply {
                putExtra("TARGET_PACKAGE_NAME", packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val notificationId = packageName.hashCode()
            val pendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                scanIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 알림 빌드
            val notification = NotificationCompat.Builder(this, CHANNEL_ID_NEW_APP)
                .setSmallIcon(R.drawable.ic_notification_ghost) // 👻 유령 아이콘
                .setContentTitle("🛡️ 새로운 앱 설치 감지")
                .setContentText("'$appName'이 설치되었습니다")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("앱 '$appName'의 악성코드 검사를 시작하시겠어요?")
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            // 알림 표시
            NotificationManagerCompat.from(this).notify(notificationId, notification)
            Log.d(TAG, "✅ 알림 표시 완료")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 알림 표시 중 오류: ${e.message}", e)
        }
    }
}

