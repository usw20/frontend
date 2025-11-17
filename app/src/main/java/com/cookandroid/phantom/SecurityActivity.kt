package com.cookandroid.phantom

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

class SecurityActivity : AppCompatActivity() {

    companion object {
        const val ACTION_PROTECTION_TICK = "com.cookandroid.phantom.PROTECTION_TICK"
        const val EXTRA_PHISHING = "extra_phishing_count"
        const val EXTRA_MALWARE = "extra_malware_count"
    }

    // ==== DTO ====
    data class MalwareScanLogDto(
        val id: String? = null,
        val scanType: String? = null,
        val targetPackageName: String? = null,
        val scanResult: String? = null,
        val threatName: String? = null,
        val detectedAt: String? = null,
        val isBlocked: Boolean? = null
    )
    data class PhishingScanLogDto(
        val id: String? = null,
        val sourceType: String? = null,
        val textContent: String? = null,
        val suspiciousUrl: String? = null,
        val scanResult: String? = null,
        val detectedAt: String? = null,
        val sender: String? = null,
        val confidenceScore: Double? = null,
        val phishingType: String? = null
    )

    // ==== Views ====
    private lateinit var tvSpam: TextView
    private lateinit var tvMalware: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var emptyLogsView: LinearLayout
    private lateinit var btnRefresh: ImageView
    private lateinit var ivSecurityStatus: ImageView
    private lateinit var tvSecurityStatus: TextView
    private lateinit var tvSecurityDesc: TextView
    private lateinit var btnViewAllLogs: TextView
    private lateinit var securityStatusCard: androidx.cardview.widget.CardView
    private lateinit var tabSecurity: LinearLayout
    private lateinit var tabHome: LinearLayout
    private lateinit var tabMypage: LinearLayout
    private lateinit var tvSecurity: TextView
    private lateinit var tvHome: TextView
    private lateinit var tvMypage: TextView

    // 코루틴
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // === Pref & Retrofit ===
    private val PREFS = "phantom_prefs"
    private val KEY_TOKEN = "jwt_token"

    private fun getToken(): String? =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TOKEN, null)

    private fun buildRetrofit(): Retrofit {
        val authInterceptor = Interceptor { chain ->
            val req = chain.request()
            val t = getToken()
            val newReq = if (!t.isNullOrBlank())
                req.newBuilder().addHeader("Authorization", "Bearer $t").build()
            else req
            chain.proceed(newReq)
        }
        val client = OkHttpClient.Builder().addInterceptor(authInterceptor).build()
        return Retrofit.Builder()
            .baseUrl("https://unparticularised-carneous-michaela.ngrok-free.dev/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // === API ===
    interface MalwareReadApi {
        @GET("/api/malware/statistics") suspend fun stats(): Response<Map<String, Long>>
        @GET("/api/malware/history") suspend fun history(): Response<List<MalwareScanLogDto>>
    }
    interface PhishingReadApi {
        @GET("/api/phishing/statistics") suspend fun stats(): Response<Map<String, Long>>
        @GET("/api/phishing/history") suspend fun history(): Response<List<PhishingScanLogDto>>
    }

    private val retrofit by lazy { buildRetrofit() }
    private val malwareApi by lazy { retrofit.create(MalwareReadApi::class.java) }
    private val phishingApi by lazy { retrofit.create(PhishingReadApi::class.java) }

    // === BroadcastReceiver ===
    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PROTECTION_TICK) return
            val spam = intent.getIntExtra(EXTRA_PHISHING, 0)
            val malware = intent.getIntExtra(EXTRA_MALWARE, 0)
            tvSpam.text = spam.toString()
            tvMalware.text = malware.toString()
            updateSecurityStatus(spam, malware)
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security)

        // Views 초기화
        tvSpam = findViewById(R.id.tvSpam)
        tvMalware = findViewById(R.id.tvMalware)
        recycler = findViewById(R.id.recyclerLogs)
        emptyLogsView = findViewById(R.id.emptyLogsView)
        btnRefresh = findViewById(R.id.btnRefresh)
        ivSecurityStatus = findViewById(R.id.ivSecurityStatus)
        tvSecurityStatus = findViewById(R.id.tvSecurityStatus)
        tvSecurityDesc = findViewById(R.id.tvSecurityDesc)
        btnViewAllLogs = findViewById(R.id.btnViewAllLogs)

        // 보안 상태 카드 찾기
        securityStatusCard = findViewById(R.id.securityStatusCard)

        tabSecurity = findViewById(R.id.tab_security)
        tabHome = findViewById(R.id.tab_home)
        tabMypage = findViewById(R.id.tab_mypage)
        tvSecurity = findViewById(R.id.tvSecurity)
        tvHome = findViewById(R.id.tvHome)
        tvMypage = findViewById(R.id.tvMypage)

        // RecyclerView 설정
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = SecurityLogAdapter()

        // 네비게이션 바 설정
        setupNavigation()

        // 클릭 리스너 설정
        setupClickListeners()

        // 초기 데이터 로드
        loadStatsAndLogs()
    }

    private fun setupNavigation() {
        // 현재 페이지 강조 (보안 페이지) - 텍스트만 변경
        tvSecurity.setTextColor(ContextCompat.getColor(this, android.R.color.holo_purple))
        tvSecurity.setTypeface(null, android.graphics.Typeface.BOLD)

        // 다른 탭들은 회색 - 텍스트만 변경
        tvHome.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        tvMypage.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))

        tabSecurity.setOnClickListener { /* 현재 화면 */ }
        tabHome.setOnClickListener {
            startActivity(Intent(this, MainPageActivity::class.java))
            finish()
        }
        tabMypage.setOnClickListener {
            startActivity(Intent(this, MypageActivity::class.java))
            finish()
        }
    }

    private fun setupClickListeners() {
        // 로그인 상태 확인
        val isLoggedIn = !getToken().isNullOrBlank()

        // 새로고침 버튼
        btnRefresh.setOnClickListener {
            loadStatsAndLogs()
            Toast.makeText(this, "데이터를 새로고침합니다", Toast.LENGTH_SHORT).show()
        }

        // 전체보기 버튼
        btnViewAllLogs.setOnClickListener {
            if (isLoggedIn) {
                Toast.makeText(this, "전체 로그 보기 기능 준비중", Toast.LENGTH_SHORT).show()
                // TODO: 전체 로그 화면으로 이동
            } else {
                Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }
        btnViewAllLogs.setOnClickListener {
            val isLoggedIn = !getToken().isNullOrBlank()
            if (isLoggedIn) {
                FullLogsDialogFragment().show(supportFragmentManager, "fullLogs")
            } else {
                android.widget.Toast.makeText(this, "로그인이 필요합니다", android.widget.Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }

        // 보안 상태 카드 클릭 - 로그인 상태에 따라 다르게 동작
        securityStatusCard.setOnClickListener {
            if (isLoggedIn) {
                // 로그인 상태: 스팸 탐지 화면으로 이동
                startActivity(Intent(this, SpamCheckActivity::class.java))
            } else {
                // 비로그인 상태: 로그인 페이지로 이동
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            ContextCompat.registerReceiver(
                this,
                tickReceiver,
                IntentFilter(ACTION_PROTECTION_TICK),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        try {
            unregisterReceiver(tickReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onStop()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // 네비게이션 텍스트 강조 유지
        tvSecurity.setTextColor(ContextCompat.getColor(this, android.R.color.holo_purple))
        tvSecurity.setTypeface(null, android.graphics.Typeface.BOLD)
    }

    // ===== 서버에서 통계/로그 가져오기 =====
    private fun loadStatsAndLogs() {
        val isLoggedIn = !getToken().isNullOrBlank()

        if (!isLoggedIn) {
            // 비로그인 상태: 기본 UI 표시
            tvSpam.text = "0"
            tvMalware.text = "0"
            updateSecurityStatus(0, 0)
            recycler.visibility = View.GONE
            emptyLogsView.visibility = View.VISIBLE
            return
        }

        uiScope.launch {
            try {
                // 통계 가져오기
                val spamCount = withContext(Dispatchers.IO) {
                    phishingApi.stats().body()?.get("phishing")?.toInt() ?: 0
                }
                val malwareCount = withContext(Dispatchers.IO) {
                    val m = malwareApi.stats().body()
                    (m?.get("malicious")?.toInt() ?: 0) + (m?.get("suspicious")?.toInt() ?: 0)
                }

                tvSpam.text = spamCount.toString()
                tvMalware.text = malwareCount.toString()

                // 보안 상태 업데이트
                updateSecurityStatus(spamCount, malwareCount)

                // 로그 가져오기
                val logs = withContext(Dispatchers.IO) {
                    val m = malwareApi.history().body().orEmpty().map {
                        UnifiedLog(
                            type = "malware",
                            title = it.targetPackageName ?: "알 수 없는 앱",
                            result = it.scanResult ?: "unknown",
                            detectedAt = it.detectedAt ?: ""
                        )
                    }
                    val p = phishingApi.history().body().orEmpty().map {
                        UnifiedLog(
                            type = "phishing",
                            title = (it.textContent ?: "내용 없음").take(50),
                            result = it.scanResult ?: "unknown",
                            detectedAt = it.detectedAt ?: ""
                        )
                    }
                    (m + p).sortedByDescending { it.detectedAt }.take(30)
                }

                // 로그 표시
                if (logs.isEmpty()) {
                    recycler.visibility = View.GONE
                    emptyLogsView.visibility = View.VISIBLE
                } else {
                    recycler.visibility = View.VISIBLE
                    emptyLogsView.visibility = View.GONE
                    (recycler.adapter as SecurityLogAdapter).submit(logs)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // 네트워크 오류 시 기본값 유지
                recycler.visibility = View.GONE
                emptyLogsView.visibility = View.VISIBLE
                Toast.makeText(this@SecurityActivity, "데이터를 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 보안 상태 업데이트
    private fun updateSecurityStatus(spamCount: Int, malwareCount: Int) {
        val totalThreats = spamCount + malwareCount
        val isLoggedIn = !getToken().isNullOrBlank()

        if (!isLoggedIn) {
            // 비로그인 상태
            ivSecurityStatus.setColorFilter(
                ContextCompat.getColor(this, android.R.color.darker_gray)
            )
            tvSecurityStatus.text = "로그인이 필요합니다"
            tvSecurityDesc.text = "로그인하여 실시간 보호를 시작하세요"
        } else {
            // 로그인 상태
            when {
                totalThreats == 0 -> {
                    ivSecurityStatus.setColorFilter(
                        ContextCompat.getColor(this, android.R.color.holo_green_dark)
                    )
                    tvSecurityStatus.text = "기기 보안 상태 양호"
                    tvSecurityDesc.text = "실시간 보호 활성화됨"
                }
                totalThreats < 5 -> {
                    ivSecurityStatus.setColorFilter(
                        ContextCompat.getColor(this, android.R.color.holo_orange_light)
                    )
                    tvSecurityStatus.text = "경미한 위협 감지"
                    tvSecurityDesc.text = "${totalThreats}건의 위협이 탐지되었습니다"
                }
                else -> {
                    ivSecurityStatus.setColorFilter(
                        ContextCompat.getColor(this, android.R.color.holo_red_dark)
                    )
                    tvSecurityStatus.text = "주의 필요"
                    tvSecurityDesc.text = "${totalThreats}건의 위협이 탐지되었습니다"
                }
            }
        }
    }

    // ===== RecyclerView =====
    data class UnifiedLog(
        val type: String,
        val title: String,
        val result: String,
        val detectedAt: String
    )

    class SecurityLogAdapter : RecyclerView.Adapter<VH>() {
        private val items = mutableListOf<UnifiedLog>()

        fun submit(newItems: List<UnifiedLog>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_security_log, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val iv: ImageView = v.findViewById(R.id.ivType)
        private val tvTitle: TextView = v.findViewById(R.id.tvTitle)
        private val tvSub: TextView = v.findViewById(R.id.tvSub)

        fun bind(item: UnifiedLog) {
            // 타입에 따른 아이콘 설정
            iv.setImageResource(
                if (item.type == "malware")
                    android.R.drawable.ic_dialog_alert
                else
                    android.R.drawable.ic_dialog_info
            )

            // 타입에 따른 색상 설정
            val color = when (item.result.lowercase()) {
                "malicious", "phishing" -> android.R.color.holo_red_dark
                "suspicious" -> android.R.color.holo_orange_light
                "safe" -> android.R.color.holo_green_dark
                else -> android.R.color.darker_gray
            }
            iv.setColorFilter(
                ContextCompat.getColor(itemView.context, color)
            )

            // 타이틀 설정
            tvTitle.text = if (item.type == "malware") {
                "악성코드: ${item.result.uppercase()}"
            } else {
                "피싱: ${item.result.uppercase()}"
            }

            // 서브 텍스트 설정 (날짜 포맷팅)
            val formattedDate = try {
                item.detectedAt.substring(0, 16).replace("T", " ")
            } catch (e: Exception) {
                item.detectedAt
            }
            tvSub.text = "${item.title}\n$formattedDate"
        }
    }
}