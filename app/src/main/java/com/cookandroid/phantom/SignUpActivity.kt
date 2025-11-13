package com.cookandroid.phantom

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SignUpActivity : AppCompatActivity() {

    private lateinit var authApi: AuthApi
    private val gson = Gson()

    private lateinit var emailEt: EditText
    private lateinit var pwEt: EditText
    private lateinit var confirmPwEt: EditText
    private lateinit var phoneEt: EditText
    private lateinit var btn: Button
    private lateinit var backBtn: ImageButton
    private lateinit var ghostIv: ImageView
    private lateinit var loginLink: TextView  // 로그인 링크 추가

    // 유령 좌우 이동 애니메이터
    private var ghostLRAnimator: ObjectAnimator? = null
    // (선택) 살짝 떠 있는 느낌을 위한 상하 보브 애니메이터 — 필요 없으면 주석 처리해도 됨
    private var ghostBobAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        setupRetrofit()

        // View 초기화
        backBtn = findViewById(R.id.back_button)
        emailEt = findViewById(R.id.signupEmail)
        pwEt = findViewById(R.id.signupPassword)
        confirmPwEt = findViewById(R.id.signupConfirmPassword)
        phoneEt = findViewById(R.id.signupPhone)
        btn = findViewById(R.id.signupBtn)
        ghostIv = findViewById(R.id.signupGhost)
        loginLink = findViewById(R.id.loginLink)  // 로그인 링크 초기화

        // 🔙 뒤로가기 → 로그인
        backBtn.setOnClickListener {
            goToLogin(prefillEmail = emailEt.text.toString().trim())
        }

        // 📝 로그인 링크 클릭 → 로그인 화면으로 이동
        loginLink.setOnClickListener {
            goToLogin(prefillEmail = emailEt.text.toString().trim())
        }

        // 가입 버튼
        btn.setOnClickListener {
            emailEt.error = null
            pwEt.error = null
            confirmPwEt.error = null
            phoneEt.error = null

            val email = emailEt.text.toString().trim()
            val pw = pwEt.text.toString().trim()
            val confirm = confirmPwEt.text.toString().trim()
            val phone = phoneEt.text.toString().trim()

            var invalid = false

            if (email.isEmpty()) {
                emailEt.error = "이메일을 입력해주세요."
                invalid = true
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailEt.error = "이메일 형식이 올바르지 않습니다."
                invalid = true
            }

            if (pw.isEmpty()) {
                pwEt.error = "비밀번호를 입력해주세요."
                invalid = true
            } else if (pw.length < 8) {
                pwEt.error = "비밀번호는 8자 이상이어야 합니다."
                invalid = true
            }

            if (confirm.isEmpty()) {
                confirmPwEt.error = "비밀번호 확인을 입력해주세요."
                invalid = true
            } else if (pw != confirm) {
                confirmPwEt.error = "비밀번호가 일치하지 않습니다."
                invalid = true
            }

            if (phone.isEmpty()) {
                phoneEt.error = "전화번호를 입력해주세요."
                invalid = true
            } else if (!phone.all { it.isDigit() }) {
                phoneEt.error = "전화번호는 숫자만 입력하세요."
                invalid = true
            }

            if (invalid) return@setOnClickListener

            signUp(email, pw, phone)
        }

        // 👻 유령 좌우 이동 애니메이션 시작
        startGhostAnimation()
    }

    private fun goToLogin(prefillEmail: String?) {
        val intent = Intent(this, LoginActivity::class.java).apply {
            putExtra("prefill_email", prefillEmail ?: "")
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun setupRetrofit() {
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            chain.proceed(originalRequest)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8080/") // 에뮬레이터용 로컬 서버
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        authApi = retrofit.create(AuthApi::class.java)
    }

    private fun signUp(email: String, pw: String, phone: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    authApi.signUp(SignUpRequest(email, pw, phone))
                }
                handleSignupResponse(response, prefillEmail = email)
            } catch (e: Exception) {
                toast("네트워크 오류: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        btn.isEnabled = !loading
        btn.text = if (loading) "회원가입 중..." else "회원가입"
        emailEt.isEnabled = !loading
        pwEt.isEnabled = !loading
        confirmPwEt.isEnabled = !loading
        phoneEt.isEnabled = !loading
    }

    private fun <T> handleSignupResponse(response: Response<T>, prefillEmail: String) {
        if (response.isSuccessful) {
            val body = response.body()
            val message = when (body) {
                is SignUpResponse -> body.message
                else -> "회원가입이 완료되었습니다. 로그인해 주세요."
            }
            toast(message)
            goToLogin(prefillEmail)
        } else {
            val errorString = response.errorBody()?.string()
            val message = try {
                gson.fromJson(errorString, ErrorResponse::class.java)?.error
                    ?: "요청 실패(${response.code()})"
            } catch (_: Exception) {
                when (response.code()) {
                    400 -> "입력 정보를 확인해주세요."
                    409 -> "이미 존재하는 이메일입니다."
                    500 -> "서버 오류가 발생했습니다."
                    else -> "요청 실패(${response.code()})"
                }
            }

            when (response.code()) {
                409 -> emailEt.error = message
                400 -> emailEt.error = message
                else -> toast(message)
            }
        }
    }

    // ---------------------------
    // 👻 유령 애니메이션 관련 코드
    // ---------------------------
    private fun startGhostAnimation() {
        // 좌우 왕복(-30dp ~ +30dp) — dp를 px로 변환
        val rangeDp = 30f
        val rangePx = rangeDp * resources.displayMetrics.density

        ghostLRAnimator = ObjectAnimator.ofFloat(ghostIv, "translationX", -rangePx, rangePx).apply {
            duration = 2200L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // (선택) 살짝 떠 있는 느낌: 상하로 4dp 정도 천천히 왕복
        val bobRangeDp = 4f
        val bobRangePx = bobRangeDp * resources.displayMetrics.density
        ghostBobAnimator = ObjectAnimator.ofFloat(ghostIv, "translationY", 0f, -bobRangePx).apply {
            duration = 1800L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onResume() {
        super.onResume()
        // 화면 복귀 시 애니메이션이 멈춰있다면 재시작
        if (ghostLRAnimator?.isRunning != true) ghostLRAnimator?.start()
        if (ghostBobAnimator?.isRunning != true) ghostBobAnimator?.start()
    }

    override fun onPause() {
        super.onPause()
        // 화면 벗어날 때는 살짝 멈춰 배터리 절약
        ghostLRAnimator?.pause()
        ghostBobAnimator?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        ghostLRAnimator?.cancel()
        ghostBobAnimator?.cancel()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}