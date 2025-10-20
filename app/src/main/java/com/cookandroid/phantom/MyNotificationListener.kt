package com.cookandroid.phantom.notification

import android.R // 안드로이드 기본 아이콘 사용 (stat_sys_warning, ic_menu_view, ic_menu_copy 등)
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cookandroid.phantom.SpamCheckActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class MyNotificationListener : NotificationListenerService() {

    companion object {
        const val CHANNEL_ID = "phantom_spam_alerts"
        const val NOTIF_ID_BASE = 1000
        const val ACTION_COPY = "com.cookandroid.phantom.ACTION_COPY_TEXT"
        const val EXTRA_TEXT = "com.cookandroid.phantom.EXTRA_TEXT"

        // 같은 텍스트 반복 알림 방지용 (최근 N건 해시 저장)
        private val recentHashes = object : LinkedHashMap<Int, Long>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Long>?): Boolean {
                return size > 64
            }
        }
        private val lock = Any()
        private const val DEDUP_WINDOW_MS = 30_000L // 30초 내 동일 텍스트 알림 억제
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
        // 로그인 & 사용자 스위치 체크
        if (!isLoggedIn()) return
        if (!isAlertsEnabled()) return

        val notif = sbn.notification ?: return
        val extras = notif.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        // 점수 계산용 전체 텍스트
        val fullText = listOf(title, text, bigText).filter { it.isNotBlank() }.joinToString("\n")
        if (fullText.isBlank()) return

        // 🔹 실제 “본문”으로 볼 내용만 선택 (표시/복사용)
        val bodyOnly = when {
            bigText.isNotBlank() -> bigText
            text.isNotBlank()    -> text
            title.isNotBlank()   -> title
            else                 -> ""
        }
        if (bodyOnly.isBlank()) return

        val pkg = sbn.packageName

        CoroutineScope(Dispatchers.Default).launch {
            // 중복 억제: 같은 텍스트가 연속적으로 오면 30초 창에서 무시
            val hash = fullText.hashCode()
            val now = System.currentTimeMillis()
            synchronized(lock) {
                val last = recentHashes[hash]
                if (last != null && now - last < DEDUP_WINDOW_MS) return@launch
                recentHashes[hash] = now
            }

            val score = scoreText(fullText)
            if (score >= 0.5) {
                val reason = explainScore(fullText)
                // 🔸 알림/복사/검사로 넘길 땐 본문만 사용
                showAlertNotification(bodyOnly, pkg, score, reason)
            }
        }
    }

    /** 간단 휴리스틱 스코어: 0.0 ~ 1.0 */
    private fun scoreText(text: String): Double {
        var score = 0.0

        // 1) URL 포함 여부 (0.4)
        if (containsUrl(text)) score += 0.4

        // 2) 긴급성 키워드 (0.2)
        val urgencyKeywords = listOf("긴급", "지금", "즉시", "당장", "중요", "지체", "오류", "정지", "정책 위반")
        if (urgencyKeywords.any { text.contains(it, ignoreCase = true) }) score += 0.2

        // 3) 금융/인증 키워드 (0.2)
        val financial = listOf("계좌", "비밀번호", "비번", "OTP", "카드", "체크", "송금", "환불", "입금")
        if (financial.any { text.contains(it, ignoreCase = true) }) score += 0.2

        // 4) 단축 URL (0.15)
        val shortLinkPattern = Pattern.compile("""\b(?:bit\.ly|t\.co|tinyurl|goo\.gl|ow\.ly)\b""", Pattern.CASE_INSENSITIVE)
        if (shortLinkPattern.matcher(text).find()) score += 0.15

        // 5) 기타 의심 키워드 (0.1)
        val suspicious = listOf("보안", "인증", "로그인", "클릭", "수신거부", "당첨", "확인")
        if (suspicious.any { text.contains(it, ignoreCase = true) }) score += 0.1

        return score.coerceAtMost(1.0)
    }

    private fun explainScore(text: String): String {
        val reasons = mutableListOf<String>()
        if (containsUrl(text)) reasons.add("링크 포함")
        if (Pattern.compile("긴급|지금|즉시|당장|중요", Pattern.CASE_INSENSITIVE).matcher(text).find())
            reasons.add("긴급성 표현")
        if (Pattern.compile("계좌|비밀번호|OTP|송금|카드", Pattern.CASE_INSENSITIVE).matcher(text).find())
            reasons.add("금융/인증 언급")
        if (Pattern.compile("(bit\\.ly|t\\.co|tinyurl|goo\\.gl|ow\\.ly)", Pattern.CASE_INSENSITIVE).matcher(text).find())
            reasons.add("단축 URL")
        return if (reasons.isEmpty()) "의심 패턴 발견" else reasons.joinToString(", ")
    }

    private fun containsUrl(text: String): Boolean {
        val urlRegex =
            "(?i)\\b(?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\([^\\s()<>]+\\))+"
        return Regex(urlRegex).containsMatchIn(text)
    }

    private fun showAlertNotification(bodyOnly: String, pkg: String, score: Double, reason: String) {
        val nm = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return

        // ▶ SpamCheckActivity로 이동 (본문만 전달)
        val inspectIntent = Intent(applicationContext, SpamCheckActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_TEXT, bodyOnly)
        }
        val inspectPI = PendingIntent.getActivity(
            applicationContext,
            System.identityHashCode(bodyOnly),
            inspectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ▶ 텍스트 복사 액션 (본문만 전달)
        val copyIntent = Intent(applicationContext, CopyActionReceiver::class.java).apply {
            action = ACTION_COPY
            putExtra(EXTRA_TEXT, bodyOnly)
        }
        val copyPI = PendingIntent.getBroadcast(
            applicationContext,
            System.identityHashCode(bodyOnly) xor 0xCAFEBABE.toInt(),
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val preview = if (bodyOnly.length > 140) bodyOnly.take(140) + "…" else bodyOnly
        val title = "의심: 스팸/피싱 가능성 ${"%.0f%%".format(score * 100)}"
        val content = "앱: $pkg • 이유: $reason\n$preview"

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(inspectPI)
            .addAction(android.R.drawable.ic_menu_view,  "자세히 검사", inspectPI) // 그대로 OK
            .addAction(android.R.drawable.ic_menu_share, "텍스트 복사", copyPI)    // ← ic_menu_copy 대신 이걸로
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID_BASE + (System.currentTimeMillis() % 10_000).toInt(), notif)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val ch = NotificationChannel(
                CHANNEL_ID,
                "스팸 탐지 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "알림 텍스트에서 의심스러운 내용이 발견되면 경고 알림을 표시합니다."
            }
            nm.createNotificationChannel(ch)
        }
    }
}
