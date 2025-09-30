package com.cookandroid.phantom

import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ForgotPasswordActivity : AppCompatActivity() {

    // ✅ Retrofit 즉시 준비(에뮬레이터→로컬 서버: 10.0.2.2)
    private val api: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8080/")   // 서버가 /api/auth/forgot-password 라우트라면 그대로 OK
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }

    private val gson = Gson()

    private lateinit var etEmail: EditText
    private lateinit var tvError: TextView
    private lateinit var btnSubmit: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        etEmail = findViewById(R.id.etEmail)
        tvError = findViewById(R.id.tvError)
        btnSubmit = findViewById(R.id.btnSubmit)
        progress = findViewById(R.id.progress)

        // (선택) 이전 화면에서 이메일 전달 시 자동 채우기
        intent.getStringExtra("prefill_email")?.let { etEmail.setText(it) }

        btnSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        tvError.text = ""
        val email = etEmail.text.toString().trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tvError.text = "올바른 이메일 형식이 아닙니다."
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api.forgotPassword(ForgotPasswordRequest(email = email)) // ✅ DTO 키 일치
                }
                handleForgot(res)
            } catch (e: Exception) {
                tvError.text = "네트워크 오류: ${e.message ?: "연결 실패"}"
            } finally {
                setLoading(false)
            }
        }
    }

    private fun handleForgot(res: Response<ForgotPasswordResponse>) {
        if (res.isSuccessful) {
            // ✅ 200/204 모두 성공 처리
            val msg = res.body()?.message ?: "비밀번호 재설정 링크가 이메일로 전송되었습니다."
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val errStr = res.errorBody()?.string()
        // 서버가 {"error":"..."} 또는 {"message":"..."} 형태를 주는 경우
        val parsed = try { gson.fromJson(errStr, ErrorResponse::class.java) } catch (_: Exception) { null }
        val serverMsg = parsed?.error ?: parsed?.message

        val msg = when (res.code()) {
            404 -> serverMsg ?: "해당 이메일로 가입된 사용자가 없습니다."
            429 -> serverMsg ?: "요청이 너무 잦습니다. 잠시 후 다시 시도하세요."
            else -> serverMsg ?: "요청 실패 (${res.code()})"
        }
        tvError.text = msg
    }

    private fun setLoading(loading: Boolean) {
        btnSubmit.isEnabled = !loading
        progress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        etEmail.isEnabled = !loading
    }
}
