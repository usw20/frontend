package com.cookandroid.phantom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 새 앱 설치 이벤트를 감지하고 사용자에게 검사 알림을 띄우는 BroadcastReceiver입니다.
 */
class PackageInstallationReceiver : BroadcastReceiver() {

    private val CHANNEL_ID_ALERT = "ScanAlertChannel"

    override fun onReceive(context: Context, intent: Intent) {
        // ACTION_PACKAGE_ADDED 이벤트인지 확인
        if (intent.action == Intent.ACTION_PACKAGE_ADDED) {

            // 설치된 앱의 패키지 이름 가져오기
            val packageName = intent.data?.schemeSpecificPart

            // 우리 앱 자신이 설치된 경우가 아닐 때만 처리
            if (packageName != null && packageName != context.packageName) {
                Log.i("PackageReceiver", "New app installed detected: $packageName. Showing scan alert.")
                showScanNotification(context, packageName)
            }
        }
    }

    /**
     * 사용자에게 검사 필요 알림을 띄우는 함수
     */
    private fun showScanNotification(context: Context, packageName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 각 패키지별로 고유한 ID를 사용하여 알림이 덮어쓰이지 않도록 합니다.
        val NOTIFICATION_ID = packageName.hashCode()

        // 1. 알림 채널 생성 (Android 8.0 이상 필수)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_ALERT,
                "검사 필요 알림",
                NotificationManager.IMPORTANCE_HIGH // 높은 중요도로 설정하여 사용자에게 눈에 띄게 함
            )
            notificationManager.createNotificationChannel(channel)
        }

        // 2. 알림 클릭 시 실행될 Intent 설정
        val scanIntent = Intent(context, MainPageActivity::class.java).apply {
            // 알림 클릭 시 스캔 대상으로 지정할 수 있도록 패키지 이름 전달
            putExtra("PACKAGE_TO_SCAN", packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // 3. PendingIntent 생성
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, scanIntent, PendingIntent.FLAG_IMMUTABLE)

        // 4. 알림 빌드
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
            .setContentTitle("🚨 새로운 앱 설치 감지!")
            .setContentText("새로 설치된 앱 ($packageName)의 보안 검사가 필요합니다. 클릭하여 검사하세요.")
            // ⚠️ R.drawable.ic_launcher_foreground는 프로젝트의 실제 아이콘으로 변경해야 합니다.
            .setSmallIcon(R.drawable.ghost_angry)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // 5. 알림 표시
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}