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
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cookandroid.phantom.data.local.TokenDataStore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.Interceptor

class LoginActivity : AppCompatActivity() {

    private lateinit var authApi: AuthApi
    private lateinit var tokenStore: TokenDataStore
    private val gson = Gson()

    private lateinit var emailEt: EditText
    private lateinit var pwEt: EditText
    private lateinit var btnLogin: Button
    private lateinit var linkSignup: TextView
    private lateinit var tvFindId: TextView
    private lateinit var tvForgotPw: TextView
    private lateinit var ghostIv: ImageView

    // 👻 유령 애니메이터
    private var ghostLRAnimator: ObjectAnimator? = null
    private var ghostBobAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_page)

        // Retrofit & Token 저장소 초기화
        tokenStore = TokenDataStore(this)
        setupRetrofit()

        // View refs
        emailEt = findViewById(R.id.inputEmail)
        pwEt = findViewById(R.id.inputPassword)
        btnLogin = findViewById(R.id.btnLogin)
        linkSignup = findViewById(R.id.signupLink)
        tvFindId = findViewById(R.id.tvFindId)
        tvForgotPw = findViewById(R.id.tvForgotPw)
        ghostIv = findViewById(R.id.logoCircle)

        // ✅ 뒤로가기(상단 아이콘) -> 메인으로
        findViewById<ImageButton>(R.id.back_button).setOnClickListener { goMain() }

        // ✅ 시스템 뒤로가기 버튼도 동일 동작
        onBackPressedDispatcher.addCallback(this) { goMain() }

        // 로그인
        btnLogin.setOnClickListener { doLogin() }

        // 회원가입 / 아이디찾기 / 비번찾기 이동
        linkSignup.setOnClickListener { startActivity(Intent(this, SignUpActivity::class.java)) }
        tvFindId.setOnClickListener { startActivity(Intent(this, FindIdActivity::class.java)) }
        tvForgotPw.setOnClickListener { startActivity(Intent(this, ForgotPasswordActivity::class.java)) }

        // 👻 유령 애니메이션 시작
        startGhostAnimation()
    }

    private fun goMain() {
        val intent = Intent(this, MainPageActivity::class.java).apply {
            // 메인이 이미 스택에 있으면 그 위 액티비티들 정리하고 복귀
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish() // 현재(Login) 종료
    }

    private fun setupRetrofit() {
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            // 로그인에서는 토큰이 필요없으므로 그대로 진행
            chain.proceed(originalRequest)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://unparticularised-carneous-michaela.ngrok-free.dev/") // 서버 주소 - 에뮬레이터용
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        authApi = retrofit.create(AuthApi::class.java)
    }

    private fun doLogin() {
        // 에러 초기화
        emailEt.error = null
        pwEt.error = null

        val email = emailEt.text.toString().trim()
        val pw = pwEt.text.toString()

        // 클라이언트 유효성 검사
        when {
            email.isEmpty() -> {
                emailEt.error = "이메일을 입력해주세요."
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailEt.error = "올바른 이메일 형식이 아닙니다."
                return
            }
            pw.isEmpty() -> {
                pwEt.error = "비밀번호를 입력해주세요."
                return
            }
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    authApi.login(LoginRequest(email, pw))
                }
                handleResponse(response) { loginResponse ->
                    // 토큰 저장 후 메인으로 이동 (백스택 정리)
                    lifecycleScope.launch {
                        tokenStore.saveToken(loginResponse.token)
                        Toast.makeText(this@LoginActivity, "로그인 성공!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@LoginActivity, MainPageActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        btnLogin.isEnabled = !loading
        linkSignup.isEnabled = !loading
        tvFindId.isEnabled = !loading
        tvForgotPw.isEnabled = !loading
        btnLogin.text = if (loading) "로그인 중..." else "로그인"
    }

    private fun <T> handleResponse(response: Response<T>, onSuccess: (T) -> Unit) {
        val body = response.body()
        if (response.isSuccessful && body != null) {
            onSuccess(body)
        } else {
            val errorString = response.errorBody()?.string()
            val message = try {
                gson.fromJson(errorString, ErrorResponse::class.java)?.error ?: "요청 실패(${response.code()})"
            } catch (_: Exception) {
                "요청 실패(${response.code()})"
            }
            pwEt.error = message
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
        // 화면 복귀 시 애니메이션 재시작
        ghostLRAnimator?.resume()
        ghostBobAnimator?.resume()

        // 만약 애니메이션이 취소되었다면 다시 생성
        if (ghostLRAnimator?.isRunning != true && ghostLRAnimator?.isPaused != true) {
            startGhostAnimation()
        }
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
}