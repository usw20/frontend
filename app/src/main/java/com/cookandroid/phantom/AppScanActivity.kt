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
    private var rotateAnimation: RotateAnimation? = null // 회전 애니메이션 변수 추가

    private val selectedPackages = mutableSetOf<String>()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_scan)

        // 1. Toolbar 설정
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "설치된 앱 검사"

        // 2. 뷰 초기화
        loadingGhost = findViewById(R.id.loading_ghost)
        tvScanStatus = findViewById(R.id.tvScanStatus)
        recyclerView = findViewById(R.id.rvAppList)
        scanButton = findViewById(R.id.btnStartScan)

        // 3. 유령 애니메이션 시작
        rotateAnimation = RotateAnimation(
            0f, 360f, // 0도에서 360도로 회전
            Animation.RELATIVE_TO_SELF, 0.5f, // X 축 중심
            Animation.RELATIVE_TO_SELF, 0.5f  // Y 축 중심
        ).apply {
            duration = 1000 // 1초
            repeatCount = Animation.INFINITE // 무한 반복
            interpolator = LinearInterpolator() // 일정한 속도
        }

        recyclerView.layoutManager = LinearLayoutManager(this)

        // 4. ⭐️ 알림 인텐트 처리 로직
        val targetPackageName = intent.getStringExtra("TARGET_PACKAGE_NAME")

        if (targetPackageName != null) {
            // 알림 클릭을 통해 진입: 특정 앱만 검사
            startTargetedScan(targetPackageName)
        } else {
            // 일반 버튼을 통해 진입: 전체 앱 목록 로드 후 사용자 선택 대기
            startFullScan()
        }

        scanButton.setOnClickListener {
            if (selectedPackages.isEmpty()) {
                Toast.makeText(this, "검사할 앱을 1개 이상 선택해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startSecurityScan(selectedPackages.toList())
        }

        // 초기 상태: 로딩 숨김, ProgressBar 숨김
        showLoading(false)
    }

    // ⭐️ 메뉴(새로고침 아이콘) 생성 (전체 스캔 모드에서만 유효)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_scan_options, menu)
        return true
    }

    // ⭐️ 메뉴 클릭 이벤트 처리 (새로고침)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                // 전체 스캔 모드로 전환하여 목록 새로고침
                startFullScan()
                Toast.makeText(this, "앱 목록 새로고침", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 일반적인 전체 앱 목록 로딩 및 사용자 선택 대기 모드 시작
     */
    private fun startFullScan() {
        // 앱 목록 로드 (백그라운드에서 실행)
        CoroutineScope(Dispatchers.IO).launch {
            val appInfos = getInstalledApps(this@AppScanActivity)
            withContext(Dispatchers.Main) {
                // UI 보이기 설정
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

    /**
     * 알림을 통해 진입 시 특정 앱만 스캔
     */
    private fun startTargetedScan(packageName: String) {
        showLoading(true, "검사 대상 앱 목록 확인 중...")

        CoroutineScope(Dispatchers.IO).launch {
            val allApps = getInstalledAppsInternal(this@AppScanActivity) // 내부 함수를 사용하여 모든 앱 목록 로드
            val targetAppInfo = allApps.firstOrNull {
                it.packageName == packageName
            }

            withContext(Dispatchers.Main) {
                if (targetAppInfo != null) {
                    // 특정 앱이 발견되면, 해당 앱만 선택하여 바로 스캔 시작
                    val packagesToScan = listOf(packageName)
                    showLoading(true, "${targetAppInfo.appName} 검사 준비 중...")
                    startSecurityScan(packagesToScan)
                } else {
                    // 앱을 찾을 수 없거나 이미 제거된 경우
                    Toast.makeText(this@AppScanActivity, "검사할 앱을 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
                    showLoading(false)
                    // 앱이 없으므로, 전체 스캔 모드로 전환하여 목록을 보여줍니다.
                    startFullScan()
                }
            }
        }
    }

    // ⭐️ 유령 로딩 화면 표시/숨김 처리 함수
    private fun showLoading(show: Boolean, message: String? = null) {
        if (show) {
            // 로딩 UI를 보이게 설정
            loadingGhost.visibility = View.VISIBLE
            tvScanStatus.visibility = View.VISIBLE
            tvScanStatus.text = message ?: "앱 분석 시작..."

            // 기존 목록 UI를 숨김
            recyclerView.visibility = View.GONE
            scanButton.visibility = View.GONE

            scanButton.isEnabled = false

            // ⭐️ 애니메이션 시작 (로딩을 보일 때마다)
            loadingGhost.startAnimation(rotateAnimation)

        } else {
            // 로딩 UI를 숨김 및 애니메이션 중지
            if (this::loadingGhost.isInitialized && !isFinishing && !isDestroyed) {
                loadingGhost.clearAnimation()
                loadingGhost.visibility = View.GONE
            }

            tvScanStatus.visibility = View.GONE

            // 기존 목록 UI를 다시 보이게 설정 (startFullScan에서 설정)
            // recyclerView.visibility = View.VISIBLE // startFullScan에서 처리
            // scanButton.visibility = View.VISIBLE // startFullScan에서 처리

            scanButton.isEnabled = true
        }
    }

    /**
     * 설치된 앱 목록 가져오기 (시스템 앱 제외)
     * 이 함수는 UI 모드(선택)에서 사용됨.
     */
    private fun getInstalledApps(context: Context): List<AppInfo> {
        return getInstalledAppsInternal(context).sortedBy { it.appName }
    }

    /**
     * 설치된 앱 목록을 가져오는 내부 로직 (Targeted Scan에서도 사용됨)
     */
    private fun getInstalledAppsInternal(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val appList = mutableListOf<AppInfo>()

        for (packageInfo in packages) {
            // 시스템 앱 제외
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


    /**
     * 실제 보안 검사를 시작합니다. (전체 또는 특정 패키지)
     */
    private fun startSecurityScan(packagesToScan: List<String>) {
        showLoading(true, "앱 분석 준비 중...") // 로딩 시작

        CoroutineScope(Dispatchers.IO).launch {
            // 선택된 패키지 목록만 대상으로 앱 정보 필터링
            val allApps = getInstalledAppsInternal(this@AppScanActivity)
            val selectedAppList = allApps.filter { packagesToScan.contains(it.packageName) }

            val totalApps = selectedAppList.size
            val scanResults = mutableListOf<ScanResult>()

            selectedAppList.forEachIndexed { index, appInfo ->
                val currentCount = index + 1

                // UI 스레드로 상태 메시지 업데이트
                withContext(Dispatchers.Main) {
                    val status = "앱 분석 중: ${appInfo.appName} (${currentCount}/${totalApps})"
                    showLoading(true, status)
                }

                // ⚠️ 검사 함수 호출
                val result = ApkExtractor.analyzeApp(this@AppScanActivity, appInfo)
                scanResults.add(result)
            }

            // 결과 화면으로 전환
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

    // RecyclerView Adapter (변경 없음)
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
