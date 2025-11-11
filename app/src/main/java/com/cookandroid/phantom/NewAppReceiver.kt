// NewAppReceiver.kt
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
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

class NewAppReceiver : BroadcastReceiver() {

    private val CHANNEL_ID = "phantom_scan_alerts"
    private val NOTIFICATION_ID = 101 // 알림 ID
    private val TAG = "NewAppReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val msg = "🔔 NewAppReceiver onReceive 호출됨 - Action: ${intent.action}"
        Log.d(TAG, msg)
        System.out.println(msg)

        // 새로 설치된 앱의 패키지명을 가져옵니다.
        val packageName = intent.data?.schemeSpecificPart
        if (packageName.isNullOrEmpty()) {
            Log.e(TAG, "❌ 패키지명을 가져올 수 없음")
            return
        }

        Log.d(TAG, "📱 감지된 패키지: $packageName")

        // ⭐️ 자체 앱 설치 또는 업데이트 시 알림 방지
        if (packageName == context.packageName) {
            Log.d(TAG, "⏭️ Phantom 앱 자신이므로 무시")
            return
        }

        // ✅ Android 13+ POST_NOTIFICATIONS 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "POST_NOTIFICATIONS 권한: $hasPermission")
            if (!hasPermission) {
                Log.w(TAG, "⚠️ POST_NOTIFICATIONS 권한 없음")
                return
            }
        }

        if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
            val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            Log.d(TAG, "EXTRA_REPLACING: $isReplacing")

            if (!isReplacing) {
                // 앱이 새로 설치되었을 때만 알림을 표시
                Log.d(TAG, "✅ 새 앱 설치 감지 - 알림 표시 시작")
                showNotification(context, packageName)
            } else {
                Log.d(TAG, "⏭️ 기존 앱 업데이트이므로 무시")
            }
        }
    }

    private fun showNotification(context: Context, packageName: String) {
        val appName = try {
            // 설치된 앱의 사용자 친화적인 이름을 가져옵니다.
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            Log.e(TAG, "앱 이름 가져오기 실패: ${e.message}")
            packageName // 실패 시 패키지명 사용
        }

        Log.d(TAG, "앱 이름: $appName, 패키지: $packageName")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0 (Oreo) 이상에서는 알림 채널 생성 필수
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "팬텀 보안 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "새로운 앱 설치 시 검사 필요 알림"
                enableVibration(true)
                enableLights(true)
                importance = NotificationManager.IMPORTANCE_MAX
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "✅ 알림 채널 생성 완료")
        }

        // 알림 클릭 시 AppScanActivity로 이동하는 Intent 설정
        val mainIntent = Intent(context, AppScanActivity::class.java).apply {
            putExtra("TARGET_PACKAGE_NAME", packageName) // 설치된 앱 패키지 전달
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        // PendingIntent 생성 (알림 클릭 시 실행될 Intent)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(
            context,
            packageName.hashCode(), // 각 앱마다 고유한 request code
            mainIntent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_ghost) // 👻 유령 아이콘
            .setContentTitle("🛡️ [팬텀] 새로운 앱 설치 감지")
            .setContentText("새 앱 '${appName}'이 설치되었습니다. 악성코드 검사가 필요합니다.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("새 앱 '${appName}'이 설치되었습니다. 안전한 사용을 위해 팬텀 앱을 열어 악성코드 검사를 진행해주세요."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(pendingIntent) // 알림 클릭 시 이동
            .setAutoCancel(true) // 클릭 시 알림 제거
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            .build()

        try {
            notificationManager.notify(packageName.hashCode(), notification)
            Log.d(TAG, "✅ 알림 표시 완료: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 알림 표시 중 오류: ${e.message}", e)
        }
    }
}