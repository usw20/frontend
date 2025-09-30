package com.cookandroid.phantom

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.Interceptor

class SignUpActivity : AppCompatActivity() {

    private lateinit var authApi: AuthApi
    private val gson = Gson()

    private lateinit var emailEt: EditText
    private lateinit var pwEt: EditText
    private lateinit var confirmPwEt: EditText
    private lateinit var phoneEt: EditText
    private lateinit var btn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // Retrofit 초기화
        setupRetrofit()

        // View 초기화
        emailEt = findViewById(R.id.signupEmail)
        pwEt = findViewById(R.id.signupPassword)
        confirmPwEt = findViewById(R.id.signupConfirmPassword)
        phoneEt = findViewById(R.id.signupPhone)
        btn = findViewById(R.id.signupBtn)

        btn.setOnClickListener {
            // 간단 검증
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
    }

    private fun setupRetrofit() {
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            // 회원가입에서는 토큰이 필요없으므로 그대로 진행
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

            // 성공 메시지 처리
            val message = when (body) {
                is SignUpResponse -> body.message
                else -> "회원가입이 완료되었습니다. 로그인해 주세요."
            }

            toast(message)

            // 로그인 화면으로 복귀 (이메일 자동 채우기)
            val data = Intent().putExtra("prefill_email", prefillEmail)
            setResult(RESULT_OK, data)
            finish()
        } else {
            // 에러 처리
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

            // 에러에 따라 적절한 필드에 표시
            when (response.code()) {
                409 -> emailEt.error = message // 이메일 중복
                400 -> {
                    // 일반적인 입력 오류는 첫 번째 필드에 표시
                    emailEt.error = message
                }
                else -> {
                    // 기타 오류는 토스트로만 표시
                    toast(message)
                }
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}