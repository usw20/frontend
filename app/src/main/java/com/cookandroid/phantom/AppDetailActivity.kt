package com.cookandroid.phantom

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cookandroid.phantom.model.ScanResult
import android.content.pm.PackageManager
import android.view.MenuItem
import android.util.Log

class AppDetailActivity : AppCompatActivity() {

    private lateinit var result: ScanResult

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_detail)

        // ⭐️ 1. Toolbar 설정 및 뒤로가기 버튼 활성화
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // 뒤로가기 화살표 표시
        supportActionBar?.title = "앱 상세 정보"

        // 1. Intent에서 Parcelable 데이터 수신
        result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("SCAN_RESULT_DETAIL", ScanResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("SCAN_RESULT_DETAIL")
        } ?: run {
            Toast.makeText(this, "오류: 앱 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2. UI 업데이트
        updateUI(result)

        // 3. 앱 제거 버튼 설정 및 리스너 연결
        setupDeleteButton(result)
    }

    // ⭐️ 2. 뒤로가기 버튼 클릭 이벤트 처리
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed() // 시스템 뒤로가기 호출
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateUI(result: ScanResult) {
        val pm = packageManager

        val ivIcon: ImageView = findViewById(R.id.ivDetailAppIcon)
        val tvAppName: TextView = findViewById(R.id.tvDetailAppName)
        val tvPackageName: TextView = findViewById(R.id.tvDetailPackageName)
        val tvStatus: TextView = findViewById(R.id.tvDetailThreatStatus)
        val tvConfidence: TextView = findViewById(R.id.tvDetailConfidence)
        val tvPermissions: TextView = findViewById(R.id.tvDetailPermissions)
        val llBackground: LinearLayout = findViewById(R.id.llResultBackground)

        // 아이콘 로드
        try {
            val icon = pm.getApplicationIcon(result.appInfo.packageName)
            ivIcon.setImageDrawable(icon)
        } catch (e: PackageManager.NameNotFoundException) {
            ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        // 기본 정보 설정
        tvAppName.text = result.appInfo.appName
        tvPackageName.text = result.appInfo.packageName
        tvConfidence.text = String.format("예측 신뢰도: %.1f%%", result.confidence * 100)

        // 결과에 따른 UI 스타일 변경
        if (result.isMalicious) {
            tvStatus.text = "❌ 위험 요소 감지 (${result.threatType})"
            tvStatus.setTextColor(Color.RED)
            llBackground.setBackgroundColor(Color.parseColor("#FFEEEE")) // 연한 붉은색 배경
        } else if (result.threatType.contains("Error") || result.threatType.contains("Timeout")) {
            tvStatus.text = "⚠️ 검사 오류 (${result.threatType})"
            tvStatus.setTextColor(Color.GRAY)
            llBackground.setBackgroundColor(Color.parseColor("#EEEEEE")) // 회색 배경
        } else {
            tvStatus.text = "✅ 안전"
            tvStatus.setTextColor(Color.parseColor("#00AA00"))
            llBackground.setBackgroundColor(Color.parseColor("#EEFFEE")) // 연한 초록색 배경
        }

        // 상세 권한 정보 (모든 요청 권한을 가져와 표시)
        try {
            val packageInfo = pm.getPackageInfo(result.appInfo.packageName, PackageManager.GET_PERMISSIONS)
            val requestedPermissions = packageInfo.requestedPermissions?.joinToString(",\n") ?: "요청된 권한 없음"
            tvPermissions.text = "요청 권한 목록:\n$requestedPermissions"
        } catch (e: Exception) {
            tvPermissions.text = "요청 권한 목록을 불러올 수 없습니다."
        }
    }

    /**
     * 앱 제거 버튼을 설정하고 클릭 리스너를 연결합니다.
     */
    private fun setupDeleteButton(result: ScanResult) {
        val btnDelete: Button = findViewById(R.id.btnDeleteApp)

        // ⭐️ 2. 버튼이 활성화되지 않는 경우를 확인하기 위해 리스너를 else 블록 밖으로 이동시킵니다.
        btnDelete.setOnClickListener {
            // ⭐️ 3. 클릭 시 로그를 출력하여 리스너가 호출되는지 확인
            Log.d("DeleteCheck", "Delete button clicked! Is Malicious: ${result.isMalicious}")

            if (result.isMalicious) {
                uninstallApp(this, result.appInfo.packageName)
            } else {
                // 버튼이 비활성화 상태에서 클릭될 일은 없지만, 로그 확인용
                Toast.makeText(this, "제거 대상이 아닙니다.", Toast.LENGTH_SHORT).show()
            }
        }

        if (result.isMalicious) {
            // 악성 앱일 경우에만 버튼 활성화 및 스타일 변경
            btnDelete.isEnabled = true
            btnDelete.text = "앱 제거 (권장 조치)"
            btnDelete.setBackgroundColor(Color.RED)
        } else {
            // 안전하거나 오류 상태일 경우
            btnDelete.isEnabled = false
            btnDelete.text = "안전함"
        }
    }

    companion object {
        private const val UNINSTALL_REQUEST_CODE = 42
    }

    /**
     * 특정 패키지 이름의 앱을 제거하는 시스템 인텐트를 호출합니다.
     */
    private fun uninstallApp(context: Context, packageName: String) {

        // 1. Uri.fromParts를 사용하여 'package' 스키마와 패키지 이름을 연결합니다.
        val packageUri = Uri.fromParts("package", packageName, null)

        // 2. ACTION_DELETE 인텐트를 생성하고 데이터를 설정합니다.
        val intent = Intent(Intent.ACTION_DELETE).apply {
            // data = packageUri와 동일한 역할을 합니다.
            data = packageUri
        }

        // ⭐️ [변경] startActivityForResult 호출
        startActivityForResult(intent, UNINSTALL_REQUEST_CODE)
    }

    // AppDetailActivity에 onActivityResult 콜백 추가
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UNINSTALL_REQUEST_CODE) {
            // 앱 삭제를 시도한 후 돌아왔다면, AppDetailActivity의 역할은 끝났으므로 종료합니다.
            // 앱이 실제로 삭제되었는지 여부(resultCode)와 관계없이 상세 화면은 닫아야 합니다.
            finish()
        }
    }

}