package com.cookandroid.phantom

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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

    // ==== 이 화면에서만 쓰는 DTO (서버 히스토리용) ====
    data class MalwareScanLogDto(
        val id: String? = null,
        val scanType: String? = null,
        val targetPackageName: String? = null,
        val scanResult: String? = null,   // "malicious" / "suspicious" / "safe"
        val threatName: String? = null,
        val detectedAt: String? = null,
        val isBlocked: Boolean? = null
    )
    data class PhishingScanLogDto(
        val id: String? = null,
        val sourceType: String? = null,   // "sms" / "email"
        val textContent: String? = null,
        val suspiciousUrl: String? = null,
        val scanResult: String? = null,   // "phishing" / "safe"
        val detectedAt: String? = null,
        val sender: String? = null,
        val confidenceScore: Double? = null,
        val phishingType: String? = null
    )

    // views
    private lateinit var tvSpam: TextView
    private lateinit var tvMalware: TextView
    private lateinit var tvStatusSpam: TextView
    private lateinit var tvStatusMalware: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var tabSecurity: LinearLayout
    private lateinit var tabHome: LinearLayout
    private lateinit var tabMypage: LinearLayout

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
            .baseUrl("http://10.0.2.2:8080/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // === 읽기 전용 API (이름 충돌 방지) ===
    interface MalwareReadApi {
        @GET("/api/malware/statistics") suspend fun stats(): Response<Map<String, Long>>
        @GET("/api/malware/history")    suspend fun history(): Response<List<MalwareScanLogDto>>
    }
    interface PhishingReadApi {
        @GET("/api/phishing/statistics") suspend fun stats(): Response<Map<String, Long>>
        @GET("/api/phishing/history")    suspend fun history(): Response<List<PhishingScanLogDto>>
    }

    private val retrofit by lazy { buildRetrofit() }
    private val malwareApi by lazy { retrofit.create(MalwareReadApi::class.java) }
    private val phishingApi by lazy { retrofit.create(PhishingReadApi::class.java) }

    // === 5초마다 ProtectionService가 쏘는 브로드캐스트 수신 ===
    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PROTECTION_TICK) return
            val active = intent.getBooleanExtra(EXTRA_ACTIVE, false)
            val spam = intent.getIntExtra(EXTRA_PHISHING, 0)
            val malware = intent.getIntExtra(EXTRA_MALWARE, 0)

            applyStatus(active)          // 초록/빨강 상태 반영
            tvSpam.text = spam.toString()
            tvMalware.text = malware.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ⬇️ XML: activity_security.xml
        setContentView(R.layout.activity_security)

        tvSpam = findViewById(R.id.tvSpam)
        tvMalware = findViewById(R.id.tvMalware)
        tvStatusSpam = findViewById(R.id.tvStatusSpam)
        tvStatusMalware = findViewById(R.id.tvStatusMalware)
        recycler = findViewById(R.id.recyclerLogs)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = SecurityLogAdapter()

        tabSecurity = findViewById(R.id.tab_security)
        tabHome = findViewById(R.id.tab_home)
        tabMypage = findViewById(R.id.tab_mypage)

        tabSecurity.setOnClickListener { /* 현재 화면 유지 */ }
        tabHome.setOnClickListener { startActivity(Intent(this, MainPageActivity::class.java)) }
        tabMypage.setOnClickListener { startActivity(Intent(this, MypageActivity::class.java)) }

        // 보호 상태(서비스 실행 여부) 반영
        applyStatus(ProtectionService.isRunning(this))

        // 최초 1회 서버 통계 & 로그 로드
        loadStatsAndLogs()
    }

    override fun onStart() {
        super.onStart()
        // Android 13 이상 호환 등록
        ContextCompat.registerReceiver(
            this,
            tickReceiver,
            IntentFilter(ACTION_PROTECTION_TICK),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        runCatching { unregisterReceiver(tickReceiver) }
        super.onStop()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    // ===== 상태(초록/빨강) UI =====
    private fun applyStatus(active: Boolean) {
        if (active) {
            setStatus(tvStatusSpam, "실시간 감시 중", true)
            setStatus(tvStatusMalware, "실시간 감시 중", true)
        } else {
            setStatus(tvStatusSpam, "감지중이 아닙니다", false)
            setStatus(tvStatusMalware, "감지중이 아닙니다", false)
        }
    }

    private fun setStatus(tv: TextView, text: String, isActive: Boolean) {
        tv.text = text
        // 점 아이콘 (green_dot / red_dot)
        tv.setCompoundDrawablesWithIntrinsicBounds(getDot(isActive), null, null, null)
        tv.compoundDrawablePadding = dp(4)
        // 텍스트 색도 함께 바꿈
        tv.setTextColor(Color.parseColor(if (isActive) "#12AF5D" else "#E54848"))
    }

    private fun getDot(green: Boolean): Drawable? =
        resources.getDrawable(if (green) R.drawable.green_dot else R.drawable.red_dot, theme)

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).toInt()

    // ===== 서버에서 통계/로그 1회 가져오기 =====
    private fun loadStatsAndLogs() {
        uiScope.launch {
            try {
                val spamCount = withContext(Dispatchers.IO) {
                    phishingApi.stats().body()?.get("phishing")?.toInt() ?: 0
                }
                val malwareCount = withContext(Dispatchers.IO) {
                    val m = malwareApi.stats().body()
                    (m?.get("malicious")?.toInt() ?: 0) + (m?.get("suspicious")?.toInt() ?: 0)
                }
                tvSpam.text = spamCount.toString()
                tvMalware.text = malwareCount.toString()

                val logs = withContext(Dispatchers.IO) {
                    val m = malwareApi.history().body().orEmpty().map {
                        UnifiedLog(
                            type = "malware",
                            title = it.targetPackageName ?: "-",
                            result = it.scanResult ?: "-",
                            detectedAt = it.detectedAt ?: ""
                        )
                    }
                    val p = phishingApi.history().body().orEmpty().map {
                        UnifiedLog(
                            type = "phishing",
                            title = (it.textContent ?: "-").take(50),
                            result = it.scanResult ?: "-",
                            detectedAt = it.detectedAt ?: ""
                        )
                    }
                    (m + p).sortedByDescending { it.detectedAt }.take(30)
                }
                (recycler.adapter as SecurityLogAdapter).submit(logs)
            } catch (_: Exception) {
                // 네트워크 실패 시 조용히 무시 (초기값 유지)
            }
        }
    }

    // ===== RecyclerView =====
    data class UnifiedLog(
        val type: String,      // "malware" | "phishing"
        val title: String,     // 패키지명 or 메시지 일부
        val result: String,    // malicious/suspicious/safe or phishing/safe
        val detectedAt: String
    )

    class SecurityLogAdapter : RecyclerView.Adapter<VH>() {
        private val items = mutableListOf<UnifiedLog>()
        fun submit(newItems: List<UnifiedLog>) {
            items.clear(); items.addAll(newItems); notifyDataSetChanged()
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
        fun bind(item: SecurityActivity.UnifiedLog) {
            iv.setImageResource(if (item.type == "malware") R.drawable.shield else R.drawable.mypage)
            tvTitle.text = if (item.type == "malware")
                "악성코드: ${item.result.uppercase()}"
            else
                "피싱: ${item.result.uppercase()}"
            tvSub.text = "${item.title}\n${item.detectedAt}"
        }
    }
}
