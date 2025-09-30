package com.cookandroid.phantom

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.PUT

class ProtectionStartActivity : AppCompatActivity() {

    private lateinit var btnStart: Button
    private lateinit var btnBack: ImageButton
    private lateinit var ivStatus: ImageView
    private lateinit var tvTitle: TextView

    // ====== 토큰 + Retrofit ======
    private val PREFS = "phantom_prefs"
    private val KEY_TOKEN = "jwt_token"

    private fun getToken(): String? =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TOKEN, null)

    private fun buildRetrofit(): Retrofit {
        val authInterceptor = Interceptor { chain ->
            val req = chain.request()
            val token = getToken()
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

    // ====== 서버 토글 API (이 파일 전용: 이름 충돌 방지) ======
    private interface UserApi {
        @PUT("/api/user/settings/malware")
        suspend fun updateMalware(@Body body: ProtectionToggleReq): Response<ProtectionToggleRes>
        @PUT("/api/user/settings/phishing")
        suspend fun updatePhishing(@Body body: ProtectionToggleReq): Response<ProtectionToggleRes>
    }
    private data class ProtectionToggleReq(val isEnabled: Boolean)
    private data class ProtectionToggleRes(val message: String, val isEnabled: Boolean)

    private val userApi by lazy { buildRetrofit().create(UserApi::class.java) }

    // Android 13+ 알림 권한(없어도 보통 크래시는 안 나지만, 안전하게 요청)
    private val requestPostNoti = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored, 서비스는 계속 진행 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_protection_start)

        btnStart = findViewById(R.id.start_protection_button)
        btnBack = findViewById(R.id.back_button)
        ivStatus = findViewById(R.id.imageViewStatus)
        tvTitle  = findViewById(R.id.textViewTitle)

        btnBack.setOnClickListener { finish() }
        btnStart.setOnClickListener { onToggleProtection() }

        refreshUi(ProtectionService.isRunning(this))
    }

    private fun onToggleProtection() {
        val turnOn = !ProtectionService.isRunning(this)
        btnStart.isEnabled = false

        if (turnOn) {
            // (A) Android 13+ 알림 권한 요청(선택)
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPostNoti.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            // (B) 로컬 서비스 즉시 시작 (5초마다 악성/피싱 샘플 스캔 루프)
            try {
                ProtectionService.start(this)
            } catch (e: Exception) {
                Toast.makeText(this, "서비스 시작 실패: ${e.message}", Toast.LENGTH_LONG).show()
                btnStart.isEnabled = true
                return
            }
            Toast.makeText(this, "보호 켰습니다.", Toast.LENGTH_SHORT).show()
            refreshUi(true)

            // (C) 서버 토글은 백그라운드에서 맞춤(실패해도 앱은 계속 동작)
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { userApi.updateMalware(ProtectionToggleReq(true)) }
                runCatching { userApi.updatePhishing(ProtectionToggleReq(true)) }
            }

            // (D) 대시보드 진입 시 안전 가드(미등록/미존재면 크래시 방지)
            startActivity(Intent(this, MainPageActivity::class.java))
            finish()
        } else {
            // 보호 끄기
            runCatching { ProtectionService.stop(this) }
            Toast.makeText(this, "보호 끔", Toast.LENGTH_SHORT).show()
            refreshUi(false)

            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { userApi.updateMalware(ProtectionToggleReq(false)) }
                runCatching { userApi.updatePhishing(ProtectionToggleReq(false)) }
            }
        }

        btnStart.isEnabled = true
    }

    private fun refreshUi(active: Boolean) {
        if (active) {
            btnStart.text = "보호 끄기"
            ivStatus.setImageResource(R.drawable.ghost_angry) // 보호 중 아이콘으로 교체 가능
            tvTitle.text = "다양한 위협으로부터\n안전하게"
        } else {
            btnStart.text = "보호 시작하기"
            ivStatus.setImageResource(R.drawable.ghost_angry)
            tvTitle.text = "보호가 꺼져 있습니다"
        }
    }
}
