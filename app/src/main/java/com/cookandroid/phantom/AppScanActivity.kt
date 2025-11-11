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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageButton
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
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AnimationUtils
import android.view.animation.RotateAnimation
import android.view.animation.LinearInterpolator
import android.view.animation.Animation

class AppScanActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var scanButton: Button
    private lateinit var adapter: AppListAdapter
    private lateinit var loadingGhost: ImageView
    private lateinit var tvScanStatus: TextView
    private lateinit var btnBack: ImageButton
    private var rotateAnimation: RotateAnimation? = null

    private val selectedPackages = mutableSetOf<String>()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_scan)

        // 1. 뒤로가기 버튼 설정
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        // 2. 뷰 초기화
        loadingGhost = findViewById(R.id.loading_ghost)
        tvScanStatus = findViewById(R.id.tvScanStatus)
        recyclerView = findViewById(R.id.rvAppList)
        scanButton = findViewById(R.id.btnStartScan)

        // 3. 유령 애니메이션 시작
        rotateAnimation = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1000
            repeatCount = Animation.INFINITE
            interpolator = LinearInterpolator()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)

        // 4. 알림 인텐트 처리 로직
        val targetPackageName = intent.getStringExtra("TARGET_PACKAGE_NAME")

        Log.d("AppScanActivity", "TARGET_PACKAGE_NAME: $targetPackageName")

        if (targetPackageName != null) {
            Log.d("AppScanActivity", "타겟 앱 검사 시작: $targetPackageName")
            startTargetedScan(targetPackageName)
        } else {
            Log.d("AppScanActivity", "전체 앱 목록 로드")
            startFullScan()
        }

        scanButton.setOnClickListener {
            if (selectedPackages.isEmpty()) {
                Toast.makeText(this, "검사할 앱을 1개 이상 선택해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startSecurityScan(selectedPackages.toList())
        }

        showLoading(false)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_scan_options, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                startFullScan()
                Toast.makeText(this, "앱 목록 새로고침", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startFullScan() {
        CoroutineScope(Dispatchers.IO).launch {
            val appInfos = getInstalledApps(this@AppScanActivity)
            withContext(Dispatchers.Main) {
                recyclerView.visibility = View.VISIBLE
                scanButton.visibility = View.VISIBLE

                adapter = AppListAdapter(appInfos) { packageName, isChecked ->
                    if (isChecked) {
                        selectedPackages.add(packageName)
                    } else {
                        selectedPackages.remove(packageName)
                    }
                    scanButton.text = "선택된 앱 검사 시작 (${selectedPackages.size}개)"
                }
                recyclerView.adapter = adapter
                scanButton.text = "선택된 앱 검사 시작 (0개)"
            }
        }
    }

    private fun startTargetedScan(packageName: String) {
        showLoading(true, "검사 대상 앱 목록 확인 중...")

        CoroutineScope(Dispatchers.IO).launch {
            val allApps = getInstalledAppsInternal(this@AppScanActivity)
            val targetAppInfo = allApps.firstOrNull {
                it.packageName == packageName
            }

            withContext(Dispatchers.Main) {
                if (targetAppInfo != null) {
                    val packagesToScan = listOf(packageName)
                    showLoading(true, "${targetAppInfo.appName} 검사 준비 중...")
                    startSecurityScan(packagesToScan)
                } else {
                    Toast.makeText(this@AppScanActivity, "검사할 앱을 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
                    showLoading(false)
                    startFullScan()
                }
            }
        }
    }

    private fun showLoading(show: Boolean, message: String? = null) {
        if (show) {
            loadingGhost.visibility = View.VISIBLE
            tvScanStatus.visibility = View.VISIBLE
            tvScanStatus.text = message ?: "앱 분석 시작..."

            recyclerView.visibility = View.GONE
            scanButton.visibility = View.GONE
            scanButton.isEnabled = false

            loadingGhost.startAnimation(rotateAnimation)
        } else {
            if (this::loadingGhost.isInitialized && !isFinishing && !isDestroyed) {
                loadingGhost.clearAnimation()
                loadingGhost.visibility = View.GONE
            }

            tvScanStatus.visibility = View.GONE
            scanButton.isEnabled = true
        }
    }

    private fun getInstalledApps(context: Context): List<AppInfo> {
        return getInstalledAppsInternal(context).sortedBy { it.appName }
    }

    private fun getInstalledAppsInternal(context: Context): List<AppInfo> {
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
        return appList
    }

    private fun startSecurityScan(packagesToScan: List<String>) {
        showLoading(true, "앱 분석 준비 중...")

        CoroutineScope(Dispatchers.IO).launch {
            val allApps = getInstalledAppsInternal(this@AppScanActivity)
            val selectedAppList = allApps.filter { packagesToScan.contains(it.packageName) }

            val totalApps = selectedAppList.size
            val scanResults = mutableListOf<ScanResult>()

            selectedAppList.forEachIndexed { index, appInfo ->
                val currentCount = index + 1

                withContext(Dispatchers.Main) {
                    val status = "앱 분석 중: ${appInfo.appName} (${currentCount}/${totalApps})"
                    showLoading(true, status)
                }

                val result = ApkExtractor.analyzeApp(this@AppScanActivity, appInfo)
                scanResults.add(result)
            }

            withContext(Dispatchers.Main) {
                showLoading(false)

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
            val app = appList[position]
            holder.bind(app)
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

                itemView.setOnClickListener {
                    cbSelect.isChecked = !cbSelect.isChecked
                }
            }
        }
    }
}