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

    companion object {
        // ProtectionService가 브로드캐스트로 보낸다고 가정하는 액션/엑스트라
        // (서비스 쪽과 문자열이 다르면 여기 문자열을 서비스와 맞춰주세요)
        const val ACTION_PROTECTION_TICK = "com.cookandroid.phantom.PROTECTION_TICK"
        const val EXTRA_PHISHING = "extra_phishing_count"
        const val EXTRA_MALWARE = "extra_malware_count"
        // 필요 시 isActive 같은 것도 쓰고 싶으면 EXTRA_ACTIVE 추가 정의 가능
        // const val EXTRA_ACTIVE = "extra_active"
    }

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

    // === 읽기 전용 API ===
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

    // === ProtectionService(또는 유사 서비스)가 주기적으로 보내는 브로드캐스트 수신 ===
    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PROTECTION_TICK) return
            val spam = intent.getIntExtra(EXTRA_PHISHING, 0)
            val malware = intent.getIntExtra(EXTRA_MALWARE, 0)
            tvSpam.text = spam.toString()
            tvMalware.text = malware.toString()
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security)

        tvSpam = findViewById(R.id.tvSpam)
        tvMalware = findViewById(R.id.tvMalware)
        recycler = findViewById(R.id.recyclerLogs)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = SecurityLogAdapter()

        tabSecurity = findViewById(R.id.tab_security)
        tabHome = findViewById(R.id.tab_home)
        tabMypage = findViewById(R.id.tab_mypage)

        tabSecurity.setOnClickListener { /* 현재 화면 유지 */ }
        tabHome.setOnClickListener { startActivity(Intent(this, MainPageActivity::class.java)) }
        tabMypage.setOnClickListener { startActivity(Intent(this, MypageActivity::class.java)) }

        // 초기 서버 통계 & 로그 1회 로드
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
