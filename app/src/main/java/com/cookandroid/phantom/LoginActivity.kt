package com.cookandroid.phantom

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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

        // 로그인
        btnLogin.setOnClickListener { doLogin() }

        // 회원가입 페이지로 이동
        linkSignup.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        // 아이디 찾기 페이지로 이동
        tvFindId.setOnClickListener {
            startActivity(Intent(this, FindIdActivity::class.java))
        }

        // 비밀번호 찾기 페이지로 이동
        tvForgotPw.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
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
            .baseUrl("http://10.0.2.2:8080/") // 서버 주소 - 에뮬레이터용
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
}