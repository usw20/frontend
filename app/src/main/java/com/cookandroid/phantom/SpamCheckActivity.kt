package com.cookandroid.phantom

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cookandroid.phantom.notification.MyNotificationListener
import kotlinx.coroutines.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SpamCheckActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEXT = "com.cookandroid.phantom.EXTRA_TEXT"
        private const val PREFS = "phantom_prefs"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_ALERTS = "alerts_enabled"
        private const val REQ_POST_NOTI = 2000

        // ✅ 중복 처리 억제(동일 텍스트가 짧은 시간 내 여러 번 전달될 때 무시)
        private const val DUP_WINDOW_MS = 60_000L
        private val recentText = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > 256
            }
        }

        private fun normalizeForKey(s: String): String =
            s.lowercase()
                .replace(Regex("\\s+"), " ")
                .replace(Regex("https?://\\S+"), "<link>")
                .trim()

        private fun md5(s: String): String {
            val md = MessageDigest.getInstance("MD5")
            return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }

        private fun shouldAcceptText(raw: String): Boolean {
            val key = md5(normalizeForKey(raw))
            val now = System.currentTimeMillis()
            synchronized(recentText) {
                val last = recentText[key]
                if (last != null && now - last < DUP_WINDOW_MS) {
                    return false
                }
                recentText[key] = now
                return true
            }
        }
    }

    // ===== DTO =====
    data class PhishingScanRequest(
        val deviceId: String,
        val sourceType: String,
        val textContent: String,
        val sender: String? = null,
        val timestamp: String? = null,
        val extractedUrls: List<String>? = null,
        val subject: String? = null,
        val shouldLog: Boolean = true      // ⭐ 수동 스캔은 기본 true (카운트)
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

    // ===== Views =====
    private lateinit var ghostSwitch: GhostSwitchView
    private lateinit var btnBack: ImageButton
    private lateinit var etMessage: EditText
    private lateinit var btnScan: Button
    private lateinit var resultCard: CardView
    private lateinit var tvResult: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvReasons: TextView
    private lateinit var tvSwitchState: TextView   // (켜짐)/(꺼짐) 표시

    // ===== Coroutine =====
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ===== 검사한 텍스트 추적 =====
    private val scannedTexts = mutableSetOf<String>()

    // ===== Retrofit =====
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
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://etha-unbeloved-supersensually.ngrok-free.dev/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val phishingApi by lazy { buildRetrofit().create(PhishingApi::class.java) }

    // runtime permission callback 저장용
    private var pendingNotifPermissionResult: ((Boolean) -> Unit)? = null

    // ===== Lifecycle =====
    @SuppressLint("MissingInflatedId")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spam_check)

        // View 초기화
        ghostSwitch   = findViewById(R.id.ghostSwitch)
        btnBack       = findViewById(R.id.btnBack)
        etMessage     = findViewById(R.id.etMessage)
        btnScan       = findViewById(R.id.btnScan)
        resultCard    = findViewById(R.id.resultCard)
        tvResult      = findViewById(R.id.tvResult)
        tvScore       = findViewById(R.id.tvScore)
        tvReasons     = findViewById(R.id.tvReasons)
        tvSwitchState = findViewById(R.id.tvSwitchState)

        // 🔙 새로운 백 제스처 처리 (OnBackPressedDispatcher)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val fromNotification =
                        intent.getBooleanExtra("EXTRA_AI_FROM_NOTIFICATION", false)

                    if (fromNotification) {
                        // 알림에서 온 경우 → 메인 페이지로 이동
                        startActivity(Intent(this@SpamCheckActivity, MainPageActivity::class.java))
                        finish()
                    } else {
                        // 그냥 finish() 해서 이전 액티비티로
                        finish()
                    }
                }
            }
        )

        // 뒤로가기 버튼 → dispatcher 사용
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // 초기 결과 텍스트
        resetResult()

        // 🔔 스위치 초기값: 저장값 + 권한/로그인 상태
        val sp = getSharedPreferences(PREFS, MODE_PRIVATE)
        val saved = sp.getBoolean(KEY_ALERTS, false)
        val enabledNow = saved && isNotificationListenerEnabled() && isLoggedIn()
        ghostSwitch.setChecked(enabledNow, animate = false)
        renderSwitch(enabledNow)

        // 스위치 클릭 → 토글
        ghostSwitch.setOnClickListener { ghostSwitch.toggle() }

        // 스위치 상태 변경
        ghostSwitch.setOnCheckedChangeListener { isChecked ->
            if (isChecked) {
                enableAlerts()
            } else {
                sp.edit().putBoolean(KEY_ALERTS, false).apply()
                renderSwitch(false)
                toast("실시간 감시를 비활성화했습니다.")
            }
        }

        // 스캔 버튼
        btnScan.setOnClickListener { performScan() }

        // 엔터로 전송
        etMessage.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                performScan(); true
            } else false
        }

        // 알림 인텐트 텍스트 처리
        handleIncomingTextFromIntent(intent)
        intent?.removeExtra(EXTRA_TEXT)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIncomingTextFromIntent(intent)
        intent?.removeExtra(EXTRA_TEXT)
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    // ===== Switch 표시 =====
    private fun renderSwitch(on: Boolean) {
        tvSwitchState.text = if (on) "  (켜짐)" else "  (꺼짐)"
        tvSwitchState.setTextColor(
            if (on) Color.parseColor("#12AF5D") else Color.parseColor("#9A9AA1")
        )
        ghostSwitch.contentDescription =
            if (on) "실시간 스팸 피싱 알림 스위치, 켜짐"
            else "실시간 스팸 피싱 알림 스위치, 꺼짐"
    }

    /** 실시간 감시 활성화 플로우 */
    private fun enableAlerts() {
        val sp = getSharedPreferences(PREFS, MODE_PRIVATE)

        // 1) 로그인 확인
        if (!isLoggedIn()) {
            toast("로그인 후 사용 가능합니다.")
            ghostSwitch.setChecked(false)
            renderSwitch(false)
            return
        }

        // 2) (Android 13+) 알림 권한
        ensurePostNotificationsPermission { granted ->
            if (!granted) {
                toast("알림 권한이 필요합니다.")
                ghostSwitch.setChecked(false)
                renderSwitch(false)
                return@ensurePostNotificationsPermission
            }

            // 3) 알림 접근 권한
            if (!isNotificationListenerEnabled()) {
                toast("알림 접근 권한을 켜주세요.")
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                ghostSwitch.setChecked(false)
                renderSwitch(false)
                return@ensurePostNotificationsPermission
            }

            // 모두 통과 → 저장 및 라벨 업데이트
            sp.edit().putBoolean(KEY_ALERTS, true).apply()
            renderSwitch(true)
            toast("실시간 감시가 활성화되었습니다.")
        }
    }

    // ===== Permission =====
    private fun ensurePostNotificationsPermission(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) { onResult(true); return }
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) { onResult(true); return }

        pendingNotifPermissionResult = onResult
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQ_POST_NOTI
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_POST_NOTI) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            pendingNotifPermissionResult?.invoke(granted)
            pendingNotifPermissionResult = null
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(
            this,
            MyNotificationListener::class.java
        )
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.split(":").any { it.equals(cn.flattenToString(), ignoreCase = true) }
    }

    private fun isLoggedIn(): Boolean {
        val sp = getSharedPreferences(PREFS, MODE_PRIVATE)
        val token = sp.getString(KEY_TOKEN, null)
        return !token.isNullOrBlank()
    }

    // ===== Scan =====
    @RequiresApi(Build.VERSION_CODES.O)
    private fun performScan() {
        val message = etMessage.text.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, "메시지를 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        // ⭐ 중복 검사 확인
        val textHash = md5(normalizeForKey(message))
        if (scannedTexts.contains(textHash)) {
            Toast.makeText(this, "이미 검사한 메시지입니다", Toast.LENGTH_SHORT).show()
            return
        }

        tvResult.text = "분석 중..."
        tvResult.setTextColor(Color.parseColor("#666666"))
        tvScore.text = ""
        tvReasons.text = ""
        btnScan.isEnabled = false

        uiScope.launch {
            try {
                val urls = extractUrls(message)

                val request = PhishingScanRequest(
                    deviceId      = getPhantomDeviceId(),
                    sourceType    = "manual",        // 수동 스캔
                    textContent   = message,
                    timestamp     = getCurrentTimestamp(),
                    extractedUrls = urls,
                    shouldLog     = true             // ⭐ 여기서만 카운트
                )

                val response = withContext(Dispatchers.IO) {
                    phishingApi.scan(request)
                }

                if (response.isSuccessful) {
                    val result = response.body()
                    displayResult(result)
                    // ⭐ 검사 완료 후 해시 저장
                    scannedTexts.add(textHash)
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
                tvResult.setTextColor(Color.parseColor("#12AF5D"))  // ✅ 6자리로 수정
            }
        }

        tvScore.text = "신뢰도: ${String.format("%.1f%%", confidence * 100)} | 위험도: $riskLevel"

        val indicators = result.riskIndicators ?: emptyList()
        val urls = result.suspiciousUrls ?: emptyList()

        val reasonsText = buildString {
            if (isPhishing) {
                append("탐지 유형: ${translatePhishingType(phishingType)}\n\n")
            }
            if (indicators.isNotEmpty()) {
                append("위험 요소:\n")
                indicators.take(5).forEach { append("• ${translateIndicator(it)}\n") }
            }
            if (urls.isNotEmpty()) {
                append("\n의심스러운 링크:\n")
                urls.take(3).forEach { append("• ${it.take(50)}\n") }
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

    // ===== Intent (알림 텍스트 수신) =====
    private fun handleIncomingTextFromIntent(incoming: Intent?) {
        val raw = incoming?.getStringExtra(EXTRA_TEXT) ?: return
        val text = raw.trim()
        if (text.isEmpty()) return

        if (!shouldAcceptText(text)) {
            return
        }

        etMessage.setText(text)
        Toast.makeText(
            this,
            "알림 텍스트를 불러왔습니다. 확인을 눌러 스캔하세요.",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ===== Helpers =====
    private fun extractUrls(text: String): List<String> {
        val urlPattern =
            "(?i)\\b(?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\([^\\s()<>]+\\))+"
        return Regex(urlPattern).findAll(text).map { it.value }.toList()
    }

    private fun getPhantomDeviceId(): String {
        return Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCurrentTimestamp(): String = try {
        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } catch (e: Exception) {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
    }

    private fun translatePhishingType(type: String): String = when (type) {
        "financial"     -> "금융 사기"
        "personal_info" -> "개인정보 탈취"
        "malware"       -> "악성코드 유포"
        "scam"          -> "사기/스캠"
        else            -> "알 수 없음"
    }

    private fun translateIndicator(indicator: String): String {
        val lower = indicator.lowercase()
        return when {
            lower.contains("suspicious_keyword") -> {
                val keyword = indicator.substringAfter(":").trim()
                "의심 키워드 포함: $keyword"
            }
            lower.contains("contains_urls") -> "URL 링크 포함"
            lower.contains("multiple_urls") -> "다수의 URL 포함"
            lower.contains("urgency")      -> "긴급성 유도 표현"
            lower.contains("financial")    -> "금융 관련 단어"
            lower.contains("personal")     -> "개인정보 요구"
            lower.contains("click")        -> "클릭 유도"
            else                           -> indicator
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}