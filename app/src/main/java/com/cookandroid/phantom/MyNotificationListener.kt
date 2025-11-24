package com.cookandroid.phantom.notification

import android.R // 안드로이드 기본 아이콘 사용
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cookandroid.phantom.SpamCheckActivity
import kotlinx.coroutines.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.regex.Pattern

class MyNotificationListener : NotificationListenerService() {

    // ===== 백엔드 DTO & API =====
    data class PhishingScanRequest(
        val deviceId: String,
        val sourceType: String,        // "notification"
        val textContent: String,
        val sender: String? = null,
        val timestamp: String? = null,
        val extractedUrls: List<String>? = null,
        val subject: String? = null,
        // 이 요청을 서버에서 카운트/로그에 포함할지 여부
        val shouldLog: Boolean = false
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

    interface PhishingApi {
        @POST("/api/phishing/scan")
        suspend fun scan(@Body request: PhishingScanRequest): Response<PhishingScanResult>
    }

    companion object {
        const val CHANNEL_ID = "phantom_spam_alerts"
        const val NOTIF_ID_BASE = 1000
        const val ACTION_COPY = "com.cookandroid.phantom.ACTION_COPY_TEXT"
        const val EXTRA_TEXT = "com.cookandroid.phantom.EXTRA_TEXT"

        // ===== 중복 방지 공통 로직 =====
        private const val DEDUP_WINDOW_MS = 30_000L // 30초 내 동일 텍스트 알림 억제

        // 🔹 시스템/충전/알람 알림 제외용 상수들
        // (제조사별로 조금 다를 수 있지만 대표적인 패키지들)
        private val SYSTEM_PACKAGES = setOf(
            "com.android.systemui",              // 상태바, 충전, 시스템 팝업
            "com.samsung.android.sm",           // 삼성 디바이스 케어
            "com.samsung.android.lool",         // 옛 디바이스 케어
            "com.sec.android.app.clockpackage", // 삼성 기본 시계/알람
            "com.google.android.deskclock"      // 구글 시계/알람
        )

        // 너무 짧은 알림(초 카운트 같은 것들)은 그냥 패스
        private const val MIN_BODY_LENGTH = 10

        // 충전/배터리/알람 관련 문구는 스팸 탐지 제외
        private val EXCLUDE_KEYWORDS = listOf(
            "충전",
            "고속 충전",
            "충전 중",
            "충전 완료",
            "배터리",
            "배터리 최적화",
            "알람",
            "타이머",
            "카운트다운"
        )

        // 최근 본문 해시 저장
        private val recentText = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > 64
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

        fun shouldAlertText(raw: String): Boolean {
            val key = md5(normalizeForKey(raw))
            val now = System.currentTimeMillis()
            synchronized(recentText) {
                val last = recentText[key]
                if (last != null && now - last < DEDUP_WINDOW_MS) {
                    return false
                }
                recentText[key] = now
                return true
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun getToken(): String? =
        getSharedPreferences("phantom_prefs", MODE_PRIVATE)
            .getString("jwt_token", null)

    private fun buildRetrofit(): Retrofit {
        val authInterceptor = Interceptor { chain ->
            val req = chain.request()
            val t = getToken()
            val newReq = if (!t.isNullOrBlank()) {
                req.newBuilder()
                    .addHeader("Authorization", "Bearer $t")
                    .build()
            } else req
            chain.proceed(newReq)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://etha-unbeloved-supersensually.ngrok-free.dev/") // 실제 폰이면 PC IP로 변경
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val phishingApi: PhishingApi by lazy {
        buildRetrofit().create(PhishingApi::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
    }

    private fun isLoggedIn(): Boolean {
        val sp = getSharedPreferences("phantom_prefs", MODE_PRIVATE)
        return !sp.getString("jwt_token", null).isNullOrBlank()
    }

    private fun isAlertsEnabled(): Boolean {
        val sp = getSharedPreferences("phantom_prefs", MODE_PRIVATE)
        return sp.getBoolean("alerts_enabled", false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 0) 팬텀 앱 알림은 무시
        if (sbn.packageName == packageName) return
        if (!isLoggedIn()) return
        if (!isAlertsEnabled()) return

        val pkg = sbn.packageName

        // 1) 시스템/충전/알람 관련 패키지는 통째로 스캔 제외
        if (SYSTEM_PACKAGES.contains(pkg)) return

        val notif = sbn.notification ?: return
        val extras = notif.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val fullText = listOf(title, text, bigText)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        if (fullText.isBlank()) return

        val bodyOnly = when {
            bigText.isNotBlank() -> bigText
            text.isNotBlank()    -> text
            title.isNotBlank()   -> title
            else                 -> ""
        }
        if (bodyOnly.isBlank()) return

        // 2) 너무 짧은 알림(초/간단 상태 변화 등)은 스캔 안 함
        if (bodyOnly.length < MIN_BODY_LENGTH) return

        // 3) 충전/배터리/알람 관련 키워드가 포함된 알림은 스캔 안 함
        if (EXCLUDE_KEYWORDS.any { bodyOnly.contains(it, ignoreCase = true) }) {
            return
        }

        scope.launch {
            // 4) 동일 알림이 너무 자주 오면(중복) 알림 억제
            if (!shouldAlertText(bodyOnly)) return@launch

            val urls = extractUrls(fullText)
            val request = PhishingScanRequest(
                deviceId      = getPhantomDeviceId(),
                sourceType    = "notification",
                textContent   = fullText,
                timestamp     = getCurrentTimestamp(),
                extractedUrls = urls,
                subject       = title.ifBlank { null },
                shouldLog     = false      // 🔥 알림 스캔은 카운트 안 함
            )

            val response: Response<PhishingScanResult> = try {
                phishingApi.scan(request)
            } catch (_: Exception) {
                return@launch
            }

            if (!response.isSuccessful) return@launch
            val result = response.body() ?: return@launch

            val isPhishing = result.isPhishing ?: false
            val confidence = result.confidence ?: 0.0
            if (!isPhishing || confidence < 0.5) return@launch

            showAlertNotification(bodyOnly, pkg, result)
        }
    }

    private fun buildReasonFromAi(result: PhishingScanResult): String {
        val parts = mutableListOf<String>()

        result.phishingType?.let {
            parts.add("유형: ${translatePhishingType(it)}")
        }

        result.riskLevel?.let {
            parts.add("위험도: $it")
        }

        val indicators = result.riskIndicators ?: emptyList()
        if (indicators.isNotEmpty()) {
            indicators.take(3).forEach { ind ->
                parts.add(translateIndicator(ind))
            }
        }

        if (parts.isEmpty()) {
            return "AI가 피싱 가능성을 감지했습니다."
        }
        return parts.joinToString(" · ")
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
            lower.contains("contains_urls")   -> "URL 링크 포함"
            lower.contains("multiple_urls")   -> "다수의 URL 포함"
            lower.contains("urgency")         -> "긴급성 유도 표현"
            lower.contains("financial")       -> "금융 관련 단어"
            lower.contains("personal")        -> "개인정보 요구"
            lower.contains("click")           -> "클릭 유도"
            else                              -> indicator
        }
    }

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

    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun showAlertNotification(
        bodyOnly: String,
        pkg: String,
        result: PhishingScanResult
    ) {
        val nm = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return

        val confidence = result.confidence ?: 0.0
        val reason = buildReasonFromAi(result)
        val preview = if (bodyOnly.length > 140) bodyOnly.take(140) + "…" else bodyOnly
        val title = "의심: 스팸/피싱 가능성 ${"%.0f%%".format(confidence * 100)}"
        val content = "앱: $pkg • $reason\n$preview"

        val inspectIntent = Intent(applicationContext, SpamCheckActivity::class.java).apply {
            // ⚠️ CLEAR_TASK 제거 → 메인으로 자연스럽게 돌아가게
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_TEXT, bodyOnly)

            putExtra("EXTRA_AI_FROM_NOTIFICATION", true)
            putExtra("EXTRA_AI_IS_PHISHING", result.isPhishing ?: false)
            putExtra("EXTRA_AI_CONFIDENCE", confidence)
            putExtra("EXTRA_AI_TYPE", result.phishingType)
            putExtra("EXTRA_AI_RISK", result.riskLevel)
            putStringArrayListExtra(
                "EXTRA_AI_INDICATORS",
                ArrayList(result.riskIndicators ?: emptyList())
            )
            putStringArrayListExtra(
                "EXTRA_AI_URLS",
                ArrayList(result.suspiciousUrls ?: emptyList())
            )
        }
        val inspectPI = PendingIntent.getActivity(
            applicationContext,
            0,
            inspectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val copyIntent = Intent(applicationContext, CopyActionReceiver::class.java).apply {
            action = ACTION_COPY
            putExtra(EXTRA_TEXT, bodyOnly)
        }
        val copyPI = PendingIntent.getBroadcast(
            applicationContext,
            1,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(inspectPI)
            .addAction(android.R.drawable.ic_menu_view,  "자세히 검사", inspectPI)
            .addAction(android.R.drawable.ic_menu_share, "텍스트 복사", copyPI)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID_BASE, notif)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val ch = NotificationChannel(
                CHANNEL_ID,
                "스팸 탐지 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "알림 텍스트에서 의심스러운 내용이 발견되면 AI로 분석한 결과를 표시합니다."
            }
            nm.createNotificationChannel(ch)
        }
    }
}
