// NewAppReceiver.kt
package com.cookandroid.phantom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class NewAppReceiver : BroadcastReceiver() {

    private val CHANNEL_ID = "phantom_scan_alerts"
    private val NOTIFICATION_ID = 101 // 알림 ID

    override fun onReceive(context: Context, intent: Intent) {
        // 새로 설치된 앱의 패키지명을 가져옵니다.
        val packageName = intent.data?.schemeSpecificPart ?: return

        // ⭐️ 자체 앱 설치 또는 업데이트 시 알림 방지
        if (packageName == context.packageName) return

        if (intent.action == Intent.ACTION_PACKAGE_ADDED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            // 앱이 새로 설치되었을 때만 알림을 표시
            showNotification(context, packageName)
        }
    }

    private fun showNotification(context: Context, packageName: String) {
        val appName = try {
            // 설치된 앱의 사용자 친화적인 이름을 가져옵니다.
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName // 실패 시 패키지명 사용
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0 (Oreo) 이상에서는 알림 채널 생성 필수
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "팬텀 보안 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "새로운 앱 설치 시 검사 필요 알림"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 알림 클릭 시 AppScanActivity로 이동하는 Intent 설정
        val mainIntent = Intent(context, AppScanActivity::class.java).apply {
            // 새 작업(Task)으로 Activity를 시작하고 기존 Activity를 모두 제거
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // PendingIntent 생성 (알림 클릭 시 실행될 Intent)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ghost_shield) // ⭐️ ghost_shield 아이콘 사용
            .setContentTitle("🛡️ [팬텀] 새로운 앱 설치 감지")
            .setContentText("새 앱 '${appName}' (${packageName})이 설치되었습니다. 악성코드 검사가 필요합니다.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("새 앱 '${appName}'이 설치되었습니다. 안전한 사용을 위해 팬텀 앱을 열어 악성코드 검사를 진행해주세요."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // 알림 클릭 시 이동
            .setAutoCancel(true) // 클릭 시 알림 제거
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}