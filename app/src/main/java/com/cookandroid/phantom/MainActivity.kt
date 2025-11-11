package com.cookandroid.phantom

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val splashDelayMs = 2500L // 2~3초 범위에서 조절 가능
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ✅ Android 13 이상에서 알림 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            } else {
                // 권한이 이미 있으면 서비스 시작
                startScanMonitoringService()
            }
        } else {
            // Android 12 이하는 권한 불필요, 바로 서비스 시작
            startScanMonitoringService()
        }

        // ✅ 지연 후 메인 페이지로 이동
        lifecycleScope.launch {
            delay(splashDelayMs)
            startActivity(Intent(this@MainActivity, MainPageActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    /**
     * ScanMonitorService를 시작하여 앱 설치 감지를 시작합니다.
     */
    private fun startScanMonitoringService() {
        try {
            val intent = Intent(this, ScanMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d(TAG, "✅ ScanMonitorService 시작됨")
        } catch (e: Exception) {
            Log.e(TAG, "❌ ScanMonitorService 시작 실패: ${e.message}", e)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "✅ 알림 권한 허용됨")
                startScanMonitoringService()
            } else {
                Log.w(TAG, "⚠️ 알림 권한 거부됨 - 앱 설치 알림이 표시되지 않습니다")
            }
        }
    }
}

