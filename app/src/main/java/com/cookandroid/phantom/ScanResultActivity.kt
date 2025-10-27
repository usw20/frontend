package com.cookandroid.phantom

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookandroid.phantom.model.ScanResult

class ScanResultActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvScanSummary: TextView
    private lateinit var tvDangerousCount: TextView
    private lateinit var btnResolve: Button
    private lateinit var btnBack: ImageButton

    // 결과 리스트는 갱신 가능하게 var로
    private var finalResults: List<ScanResult> = emptyList()
    private lateinit var scanAdapter: ScanResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_result)

        // 뷰 초기화
        tvScanSummary = findViewById(R.id.tvScanSummary)
        tvDangerousCount = findViewById(R.id.tvDangerousCount)
        recyclerView = findViewById(R.id.rvScanResults)
        btnResolve = findViewById(R.id.btnResolveAction)
        btnBack = findViewById(R.id.btnBack)  // activity_scan_result.xml 의 상단 바에 존재해야 함

        // 뒤로가기(상단 버튼)
        btnBack.setOnClickListener { navigateBackToAppScan() }

        // 뒤로가기(물리/제스처)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBackToAppScan()
        })

        // Intent 수신
        val initialTotalScanned = intent.getIntExtra("TOTAL_SCANNED", 0)
        finalResults = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("SCAN_RESULTS_LIST", ScanResult::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("SCAN_RESULTS_LIST") ?: emptyList()
        }

        // 초기 UI/리스트 세팅
        val initialDangerousApps = finalResults.count { it.isMalicious }
        val initialErrorCount = calculateErrorCount(finalResults)
        updateSummary(initialTotalScanned, initialDangerousApps, initialErrorCount)
        setupRecyclerView(finalResults)

        // 해결 버튼
        btnResolve.setOnClickListener {
            val currentDangerousApps = finalResults.filter { it.isMalicious }
            if (currentDangerousApps.isNotEmpty()) {
                val firstDangerousApp = currentDangerousApps.first()
                val detailIntent = Intent(this, AppDetailActivity::class.java).apply {
                    putExtra("SCAN_RESULT_DETAIL", firstDangerousApp)
                }
                startActivity(detailIntent)
            } else {
                // 위험 앱이 없으면 AppScanActivity로 복귀
                navigateBackToAppScan()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshResults()
    }

    /** AppScanActivity로 복귀 */
    private fun navigateBackToAppScan() {
        val intent = Intent(this, AppScanActivity::class.java).apply {
            // 이미 스택에 있으면 그 위를 정리하고 해당 액티비티로 복귀
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    /** 삭제된 앱 제거 후 UI 갱신 */
    private fun refreshResults() {
        val pm = packageManager
        val updatedResults = finalResults.filter { result ->
            try {
                pm.getPackageInfo(result.appInfo.packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
        if (updatedResults.size != finalResults.size) {
            finalResults = updatedResults
            val totalScanned = finalResults.size
            val dangerousApps = finalResults.count { it.isMalicious }
            val errorCount = calculateErrorCount(finalResults)
            updateSummary(totalScanned, dangerousApps, errorCount)
            scanAdapter.updateData(finalResults)
        }
    }

    private fun calculateErrorCount(results: List<ScanResult>): Int =
        results.count {
            it.threatType.contains("Error") ||
                    it.threatType.contains("Timeout") ||
                    it.threatType.contains("Unknown Host") ||
                    it.threatType.contains("Connection Error")
        }

    /** 상단 요약 갱신 */
    private fun updateSummary(total: Int, dangerous: Int, errors: Int) {
        tvScanSummary.text = "총 ${total}개 앱 검사 완료"

        val (message, color, buttonText, buttonColor) = when {
            dangerous > 0 -> {
                Pair("{$dangerous}개 앱에서 위험 요소가 발견되었습니다.", Color.RED) to
                        Pair("위험 앱 확인 및 해결", Color.parseColor("#FF4081"))
            }
            errors > 0 -> {
                Pair("${errors}개 앱 검사 중 오류가 발생했습니다.", Color.parseColor("#FFA500")) to
                        Pair("일부 앱 재검사 시도", Color.parseColor("#660099"))
            }
            else -> {
                Pair("모든 앱이 안전합니다. 👍", Color.parseColor("#00AA00")) to
                        Pair("홈으로 돌아가기", Color.parseColor("#660099"))
            }
        }.let { (mc, bc) -> Quad(mc.first, mc.second, bc.first, bc.second) }

        tvDangerousCount.text = message
        tvDangerousCount.setTextColor(color)
        btnResolve.text = buttonText
        btnResolve.setBackgroundColor(buttonColor)
    }

    /** RecyclerView 연결 */
    private fun setupRecyclerView(results: List<ScanResult>) {
        recyclerView.layoutManager = LinearLayoutManager(this)
        scanAdapter = ScanResultAdapter(results) { selectedResult ->
            val detailIntent = Intent(this, AppDetailActivity::class.java).apply {
                putExtra("SCAN_RESULT_DETAIL", selectedResult)
            }
            startActivity(detailIntent)
        }
        recyclerView.adapter = scanAdapter
    }

    /** 간단한 데이터 홀더 */
    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
