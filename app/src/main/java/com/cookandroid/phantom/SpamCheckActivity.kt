package com.cookandroid.phantom

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import kotlinx.coroutines.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SpamCheckActivity : AppCompatActivity() {

    // ===== DTO =====
    data class PhishingScanRequest(
        val deviceId: String,
        val sourceType: String,
        val textContent: String,
        val sender: String? = null,
        val timestamp: String? = null,
        val extractedUrls: List<String>? = null,
        val subject: String? = null
    )

    data class PhishingScanResult(
        val isPhishing: Boolean?,
        val confidence: Double?,
        val phishingType: String?,
        val riskLevel: String?,
        val riskIndicators: List<String>?,
        val suspiciousUrls: List<String>?,
        val shouldBlock: Boolean?
    )

    // ===== Retrofit API =====
    interface PhishingApi {
        @POST("/api/phishing/scan")
        suspend fun scan(@Body request: PhishingScanRequest): Response<PhishingScanResult>
    }

    // Views
    private lateinit var btnBack: ImageButton
    private lateinit var etMessage: EditText
    private lateinit var btnScan: Button
    private lateinit var resultCard: CardView
    private lateinit var tvResult: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvReasons: TextView

    // Coroutine
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Retrofit
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

    private val phishingApi by lazy { buildRetrofit().create(PhishingApi::class.java) }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spam_check)

        // 뷰 초기화
        btnBack = findViewById(R.id.btnBack)
        etMessage = findViewById(R.id.etMessage)
        btnScan = findViewById(R.id.btnScan)
        resultCard = findViewById(R.id.resultCard)
        tvResult = findViewById(R.id.tvResult)
        tvScore = findViewById(R.id.tvScore)
        tvReasons = findViewById(R.id.tvReasons)

        // 뒤로가기 버튼
        btnBack.setOnClickListener { finish() }

        // 스캔 버튼
        btnScan.setOnClickListener { performScan() }

        // 엔터키로 전송
        etMessage.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                performScan()
                true
            } else false
        }

        // 초기 상태
        resetResult()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun performScan() {
        val message = etMessage.text.toString().trim()

        if (message.isEmpty()) {
            Toast.makeText(this, "메시지를 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        // 로딩 상태
        tvResult.text = "분석 중..."
        tvResult.setTextColor(Color.parseColor("#666666"))
        tvScore.text = ""
        tvReasons.text = ""
        btnScan.isEnabled = false

        uiScope.launch {
            try {
                // URL 추출
                val urls = extractUrls(message)

                // 요청 생성
                val request = PhishingScanRequest(
                    deviceId = getPhantomDeviceId(),
                    sourceType = "manual",  // 수동 입력
                    textContent = message,
                    timestamp = getCurrentTimestamp(),
                    extractedUrls = urls
                )

                // API 호출
                val response = withContext(Dispatchers.IO) {
                    phishingApi.scan(request)
                }

                if (response.isSuccessful) {
                    val result = response.body()
                    displayResult(result)
                } else {
                    showError("서버 오류: ${response.code()}")
                }

            } catch (e: Exception) {
                showError("네트워크 오류: ${e.message}")
            } finally {
                btnScan.isEnabled = true
            }
        }
    }

    private fun displayResult(result: PhishingScanResult?) {
        if (result == null) {
            showError("결과를 받을 수 없습니다")
            return
        }

        val isPhishing = result.isPhishing ?: false
        val confidence = result.confidence ?: 0.0
        val riskLevel = result.riskLevel ?: "UNKNOWN"
        val phishingType = result.phishingType ?: "unknown"

        // 결과 텍스트 및 색상
        when {
            isPhishing && confidence > 0.7 -> {
                tvResult.text = "⚠️ 위험: 피싱/스팸으로 판단됩니다"
                tvResult.setTextColor(Color.parseColor("#E54848"))
            }
            isPhishing && confidence > 0.5 -> {
                tvResult.text = "⚠️ 주의: 의심스러운 메시지입니다"
                tvResult.setTextColor(Color.parseColor("#FF9800"))
            }
            else -> {
                tvResult.text = "✓ 안전: 정상 메시지로 판단됩니다"
                tvResult.setTextColor(Color.parseColor("#12AF5D"))
            }
        }

        // 신뢰도 점수
        tvScore.text = "신뢰도: ${String.format("%.1f%%", confidence * 100)} | 위험도: $riskLevel"

        // 위험 지표
        val indicators = result.riskIndicators ?: emptyList()
        val urls = result.suspiciousUrls ?: emptyList()

        val reasonsText = buildString {
            if (isPhishing) {
                append("탐지 유형: ${translatePhishingType(phishingType)}\n\n")
            }

            if (indicators.isNotEmpty()) {
                append("위험 요소:\n")
                indicators.take(5).forEach { indicator ->
                    append("• ${translateIndicator(indicator)}\n")
                }
            }

            if (urls.isNotEmpty()) {
                append("\n의심스러운 링크:\n")
                urls.take(3).forEach { url ->
                    append("• ${url.take(50)}\n")
                }
            }

            if (indicators.isEmpty() && urls.isEmpty()) {
                append("특별한 위험 요소가 발견되지 않았습니다.")
            }
        }

        tvReasons.text = reasonsText.trim()
    }

    private fun showError(message: String) {
        tvResult.text = "❌ 오류 발생"
        tvResult.setTextColor(Color.parseColor("#E54848"))
        tvScore.text = ""
        tvReasons.text = message
    }

    private fun resetResult() {
        tvResult.text = "결과 대기 중"
        tvResult.setTextColor(Color.parseColor("#666666"))
        tvScore.text = "스코어: -"
        tvReasons.text = "메시지를 입력하고 '스팸 탐지하기' 버튼을 누르세요."
    }

    // URL 추출
    private fun extractUrls(text: String): List<String> {
        val urlPattern = "(?i)\\b(?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\([^\\s()<>]+\\))+"
        return Regex(urlPattern).findAll(text).map { it.value }.toList()
    }

    // 기기 ID 가져오기 (간단히 Android ID 사용)
    private fun getPhantomDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }

    // 현재 시간
    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCurrentTimestamp(): String {
        return try {
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (e: Exception) {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
        }
    }

    // 피싱 타입 번역
    private fun translatePhishingType(type: String): String = when (type) {
        "financial" -> "금융 사기"
        "personal_info" -> "개인정보 탈취"
        "malware" -> "악성코드 유포"
        "scam" -> "사기/스캠"
        else -> "알 수 없음"
    }

    // 위험 지표 번역
    private fun translateIndicator(indicator: String): String {
        val lower = indicator.lowercase()
        return when {
            lower.contains("suspicious_keyword") -> {
                val keyword = indicator.substringAfter(":").trim()
                "의심 키워드 포함: $keyword"
            }
            lower.contains("contains_urls") -> "URL 링크 포함"
            lower.contains("multiple_urls") -> "다수의 URL 포함"
            lower.contains("urgency") -> "긴급성 유도 표현"
            lower.contains("financial") -> "금융 관련 단어"
            lower.contains("personal") -> "개인정보 요구"
            lower.contains("click") -> "클릭 유도"
            else -> indicator
        }
    }
}