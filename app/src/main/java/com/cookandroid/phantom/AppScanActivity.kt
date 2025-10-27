package com.cookandroid.phantom

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookandroid.phantom.model.AppInfo
import com.cookandroid.phantom.model.ScanResult
import com.cookandroid.phantom.util.ApkExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppScanActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var scanButton: Button
    private lateinit var adapter: AppListAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatusMessage: TextView
    private lateinit var btnBack: ImageButton

    private val selectedPackages = mutableSetOf<String>()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_scan)

        // 뷰 초기화
        progressBar = findViewById(R.id.progressBarScan)
        tvStatusMessage = findViewById(R.id.tvScanStatus)
        recyclerView = findViewById(R.id.rvAppList)
        scanButton = findViewById(R.id.btnStartScan)
        btnBack = findViewById(R.id.btnBack)

        recyclerView.layoutManager = LinearLayoutManager(this)
        loadInstalledApps()

        scanButton.setOnClickListener {
            if (selectedPackages.isEmpty()) {
                Toast.makeText(this, "검사할 앱을 1개 이상 선택해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startSecurityScan()
        }

        // 상단 뒤로가기 버튼 → 메인페이지
        btnBack.setOnClickListener { navigateBackToMain() }

        // 물리/제스처 백 버튼 → 메인페이지
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBackToMain()
        })
    }

    private fun navigateBackToMain() {
        val intent = Intent(this, MainPageActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun loadInstalledApps() {
        CoroutineScope(Dispatchers.IO).launch {
            val appInfos = getInstalledApps(this@AppScanActivity)
            withContext(Dispatchers.Main) {
                adapter = AppListAdapter(appInfos) { packageName, isChecked ->
                    if (isChecked) selectedPackages.add(packageName) else selectedPackages.remove(packageName)
                    scanButton.text = "선택된 앱 검사 시작 (${selectedPackages.size}개)"
                }
                recyclerView.adapter = adapter
                scanButton.text = "선택된 앱 검사 시작 (0개)"
            }
        }
    }

    private fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val appList = mutableListOf<AppInfo>()
        for (packageInfo in packages) {
            if (packageInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                val appName = pm.getApplicationLabel(packageInfo).toString()
                val appIcon = pm.getApplicationIcon(packageInfo)
                appList.add(
                    AppInfo(
                        appName = appName,
                        packageName = packageInfo.packageName,
                        appIcon = appIcon,
                        sourceDir = packageInfo.publicSourceDir
                    )
                )
            }
        }
        return appList.sortedBy { it.appName }
    }

    private fun startSecurityScan() {
        scanButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatusMessage.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            val selectedAppList = adapter.getAppList().filter { it.packageName in selectedPackages }
            val totalApps = selectedAppList.size
            val scanResults = mutableListOf<ScanResult>()

            selectedAppList.forEachIndexed { index, appInfo ->
                val currentCount = index + 1
                withContext(Dispatchers.Main) {
                    tvStatusMessage.text = "앱 분석 중: ${appInfo.appName} (${currentCount}/${totalApps})"
                    progressBar.progress = (currentCount * 100) / totalApps
                }
                val result = ApkExtractor.analyzeApp(this@AppScanActivity, appInfo)
                scanResults.add(result)
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                tvStatusMessage.visibility = View.GONE

                val totalScanned = totalApps
                val dangerousApps = scanResults.count { it.isMalicious }

                val intent = Intent(this@AppScanActivity, ScanResultActivity::class.java).apply {
                    putParcelableArrayListExtra("SCAN_RESULTS_LIST", ArrayList(scanResults))
                    putExtra("TOTAL_SCANNED", totalScanned)
                    putExtra("DANGEROUS_APPS", dangerousApps)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    // RecyclerView Adapter
    class AppListAdapter(
        private val appList: List<AppInfo>,
        private val onAppSelected: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

        fun getAppList(): List<AppInfo> = appList

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_selection, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            holder.bind(appList[position])
        }

        override fun getItemCount(): Int = appList.size

        inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
            private val tvName: TextView = itemView.findViewById(R.id.tvAppName)
            private val tvPackage: TextView = itemView.findViewById(R.id.tvAppPackage)
            private val cbSelect: CheckBox = itemView.findViewById(R.id.cbAppSelect)

            fun bind(app: AppInfo) {
                tvName.text = app.appName
                tvPackage.text = app.packageName
                ivIcon.setImageDrawable(app.appIcon)

                cbSelect.setOnCheckedChangeListener(null)
                cbSelect.isChecked = false

                cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    onAppSelected(app.packageName, isChecked)
                }

                itemView.setOnClickListener { cbSelect.isChecked = !cbSelect.isChecked }
            }
        }
    }
}
