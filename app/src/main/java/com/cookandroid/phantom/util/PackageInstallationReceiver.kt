package com.cookandroid.phantom.util

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
import com.cookandroid.phantom.R
import com.cookandroid.phantom.AppScanActivity

/**
 * 새 앱 설치 이벤트를 감지하고 사용자에게 검사 알림을 띄우는 BroadcastReceiver입니다.
 */
class PackageInstallationReceiver : BroadcastReceiver() {

    private val CHANNEL_ID_ALERT = "ScanAlertChannel"
    private val TAG = "PkgInstallReceiver"
    private val PHANTOM_PACKAGE_NAME = "com.cookandroid.phantom"

    override fun onReceive(context: Context, intent: Intent) {
        val msg = "🔔 === onReceive 호출됨 === Action: ${intent.action}"
        Log.d(TAG, msg)
        System.out.println(msg)

        // ACTION_PACKAGE_ADDED 또는 PACKAGE_REPLACED 이벤트인지 확인
        if (intent.action != Intent.ACTION_PACKAGE_ADDED && intent.action != Intent.ACTION_PACKAGE_REPLACED) {
            Log.d(TAG, "❌ PACKAGE_ADDED/REPLACED가 아님: ${intent.action}")
            return
        }

        // 설치된 앱의 패키지 이름 가져오기
        val packageName = intent.data?.schemeSpecificPart
        if (packageName.isNullOrEmpty()) {
            Log.e(TAG, "❌ 패키지명을 가져올 수 없음")
            return
        }

        val logMsg = "📱 새 앱 설치/업데이트 감지: $packageName"
        Log.d(TAG, logMsg)
        System.out.println(logMsg)

        // 우리 앱 자신이 설치된 경우는 무시
        if (packageName == PHANTOM_PACKAGE_NAME) {
            Log.d(TAG, "⏭️ Phantom 앱 자신이므로 무시")
            return
        }

        // 앱 업데이트인 경우만 EXTRA_REPLACING 체크
        // ACTION_PACKAGE_REPLACED는 업데이트, ACTION_PACKAGE_ADDED는 신규 설치
        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        if (intent.action == Intent.ACTION_PACKAGE_ADDED && isReplacing) {
            Log.d(TAG, "⏭️ 기존 앱 업데이트이므로 무시")
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

        try {
            val startMsg = "✅ 알림 표시 프로세스 시작: $packageName"
            Log.d(TAG, startMsg)
            System.out.println(startMsg)
            showScanNotification(context, packageName)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 알림 표시 중 오류 발생: ${e.message}", e)
            e.printStackTrace()
        }
    }

    /**
     * 사용자에게 검사 필요 알림을 띄우는 함수
     */
    private fun showScanNotification(context: Context, packageName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val NOTIFICATION_ID = packageName.hashCode()

        Log.d(TAG, "알림 채널 생성 시작 (ID: $NOTIFICATION_ID)")

        // 1. 알림 채널 생성 (Android 8.0 이상 필수)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_ALERT,
                "검사 필요 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "새로 설치된 앱의 악성코드 검사 알림"
                enableVibration(true)
                enableLights(true)
                importance = NotificationManager.IMPORTANCE_MAX
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "✅ 알림 채널 생성 완료")
        }

        // 2. 알림 클릭 시 실행될 Intent 설정 (AppScanActivity로 직접 이동)
        val scanIntent = Intent(context, AppScanActivity::class.java).apply {
            putExtra("TARGET_PACKAGE_NAME", packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        // 3. PendingIntent 생성
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            scanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.d(TAG, "PendingIntent 생성 완료")

        // 4. 알림 빌드
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
            .setContentTitle("새 앱 설치됨")
            .setContentText("$packageName 앱에 대한 악성코드 검사를 시작하시겠어요?")
            .setSmallIcon(R.drawable.ic_notification_ghost) // 👻 유령 아이콘
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            .build()

        // 5. 알림 표시
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            val completeMsg = "✅ 알림 표시 완료 (ID: $NOTIFICATION_ID, Package: $packageName)"
            Log.d(TAG, completeMsg)
            System.out.println(completeMsg)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 알림 notify 호출 중 오류: ${e.message}", e)
        }
    }
}