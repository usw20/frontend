package com.cookandroid.phantom

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import retrofit2.Response
import java.util.UUID

// ==== 브로드캐스트 액션/엑스트라 ====
const val ACTION_PROTECTION_TICK = "com.cookandroid.phantom.PROTECTION_TICK"
const val EXTRA_MALWARE = "malware"
const val EXTRA_PHISHING = "phishing"
const val EXTRA_ACTIVE = "active"

// ==== SharedPreferences 키 ====
private const val PREFS = "phantom_prefs"
private const val KEY_TOKEN = "jwt_token"
private const val KEY_PROTECTION_ON = "protection_on"

// ==== DTO (백엔드 스펙과 일치) - 여기만 둡니다 (재정의 금지) ====
data class MalwareScanRequest(
    val deviceId: String,
    val scanType: String,            // "realtime" or "manual"
    val targetPackageName: String,
    val targetHash: String? = null,
    val permissions: List<String>? = null,
    val fileSize: Long? = null,
    val installTime: String? = null,
    val versionCode: Int? = null,
    val apiCalls: List<String>? = null
)
data class MalwareScanResult(
    val isMalicious: Boolean,
    val confidence: Double,
    val threatType: String? = null,
    val riskLevel: String? = null,
    val shouldBlock: Boolean? = null
)

data class PhishingScanRequest(
    val deviceId: String,
    val sourceType: String,          // "sms" or "email"
    val textContent: String,
    val sender: String? = null,
    val timestamp: String? = null,
    val extractedUrls: List<String>? = null,
    val subject: String? = null
)
data class PhishingScanResult(
    val isPhishing: Boolean,
    val confidence: Double,
    val phishingType: String? = null,
    val riskLevel: String? = null,
    val riskIndicators: List<String>? = null,
    val suspiciousUrls: List<String>? = null,
    val shouldBlock: Boolean? = null
)

// ==== Retrofit API (서비스에서 쓰는 것만: scan, stats) ====
interface MalwareApi {
    @POST("/api/malware/scan")
    suspend fun scan(@Body body: MalwareScanRequest): Response<MalwareScanResult>

    @GET("/api/malware/statistics")
    suspend fun stats(): Response<Map<String, Long>>
}
interface PhishingApi {
    @POST("/api/phishing/scan")
    suspend fun scan(@Body body: PhishingScanRequest): Response<PhishingScanResult>

    @GET("/api/phishing/statistics")
    suspend fun stats(): Response<Map<String, Long>>
}
interface UserApiForToggle {
    @PUT("/api/user/settings/malware")
    suspend fun updateMalware(@Body body: SecuritySettingRequest): Response<SecuritySettingResponse>
    @PUT("/api/user/settings/phishing")
    suspend fun updatePhishing(@Body body: SecuritySettingRequest): Response<SecuritySettingResponse>
}

// ==== 공용 Retrofit 빌더 ====
private fun buildRetrofit(ctx: Context): Retrofit {
    val authInterceptor = Interceptor { chain ->
        val req = chain.request()
        val token = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)
        val newReq = if (!token.isNullOrBlank()) {
            req.newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else req
        chain.proceed(newReq)
    }
    val client = OkHttpClient.Builder().addInterceptor(authInterceptor).build()
    return Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8080/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}

class ProtectionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    private lateinit var malwareApi: MalwareApi
    private lateinit var phishingApi: PhishingApi
    private lateinit var userApi: UserApiForToggle

    private val channelId = "protection_channel"
    private val notifId = 8127

    override fun onCreate() {
        super.onCreate()
        val retrofit = buildRetrofit(this)
        malwareApi = retrofit.create(MalwareApi::class.java)
        phishingApi = retrofit.create(PhishingApi::class.java)
        userApi = retrofit.create(UserApiForToggle::class.java)

        createChannel()
        startForeground(notifId, buildNotification(active = true, spam = 0, malware = 0))

        // 서버 탐지 기능 ON (성공/실패 무관 비동기 시도)
        serviceScope.launch {
            runCatching { userApi.updateMalware(SecuritySettingRequest(true)) }
            runCatching { userApi.updatePhishing(SecuritySettingRequest(true)) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (loopJob?.isActive == true) return START_STICKY

        loopJob = serviceScope.launch {
            val deviceId = getDeviceId(this@ProtectionService)
            var tick = 0
            while (isActive) {
                try {
                    // (A) 샘플 스캔 호출
                    runCatching {
                        val targetPkg = if (tick % 8 == 0) "com.evilapp.malware" else "com.system.healthcheck"
                        malwareApi.scan(
                            MalwareScanRequest(
                                deviceId = deviceId,
                                scanType = "realtime",
                                targetPackageName = targetPkg,
                                targetHash = UUID.randomUUID().toString().replace("-", "")
                            )
                        )
                    }
                    runCatching {
                        val text = if (tick % 6 == 0)
                            "긴급! 계정이 정지되었습니다. 즉시 확인하세요: http://fake-bank.com/verify"
                        else "정기 알림: 오늘 일정이 업데이트되었습니다."
                        phishingApi.scan(
                            PhishingScanRequest(
                                deviceId = deviceId,
                                sourceType = "sms",
                                textContent = text,
                                sender = "1588-0000"
                            )
                        )
                    }

                    // (B) 통계 조회 → 브로드캐스트 + 알림 갱신
                    val mal = runCatching { malwareApi.stats().body() }.getOrNull()
                    val phi = runCatching { phishingApi.stats().body() }.getOrNull()

                    val malwareCount =
                        (mal?.get("malicious")?.toInt() ?: 0) + (mal?.get("suspicious")?.toInt() ?: 0)
                    val phishingCount = (phi?.get("phishing")?.toInt() ?: 0)

                    // 브로드캐스트
                    sendBroadcast(Intent(ACTION_PROTECTION_TICK).apply {
                        putExtra(EXTRA_MALWARE, malwareCount)
                        putExtra(EXTRA_PHISHING, phishingCount)
                        putExtra(EXTRA_ACTIVE, true)
                    })

                    // 알림 갱신 (탭 → SecurityActivity)
                    val notif = buildNotification(active = true, spam = phishingCount, malware = malwareCount)
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(notifId, notif)

                } catch (_: Exception) {
                    // 네트워크 오류는 무시하고 다음틱
                }
                tick++
                delay(5000)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        serviceScope.launch {
            // 서버 탐지 기능 OFF
            runCatching { userApi.updateMalware(SecuritySettingRequest(false)) }
            runCatching { userApi.updatePhishing(SecuritySettingRequest(false)) }
        }
        // 상태 브로드캐스트 (off)
        sendBroadcast(Intent(ACTION_PROTECTION_TICK).apply {
            putExtra(EXTRA_ACTIVE, false)
        })
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "실시간 보호", NotificationManager.IMPORTANCE_LOW)
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(active: Boolean, spam: Int, malware: Int): Notification {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, SecurityActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val status = if (active) "감시 중" else "중지됨"
        val content = "스팸/피싱 $spam · 악성코드 $malware ($status)"
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.shield)
            .setContentTitle("Phantom 보호")
            .setContentText(content)
            .setContentIntent(pending)
            .setOngoing(active)
            .build()
    }

    private fun getDeviceId(ctx: Context): String =
        android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID)

    companion object {
        fun start(ctx: Context) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PROTECTION_ON, true).apply()
            val i = Intent(ctx, ProtectionService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun stop(ctx: Context) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PROTECTION_ON, false).apply()
            ctx.stopService(Intent(ctx, ProtectionService::class.java))
        }
        fun isRunning(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PROTECTION_ON, false)
    }
}
