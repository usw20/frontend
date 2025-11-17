package com.cookandroid.phantom

import android.os.Bundle
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

class FindIdActivity : AppCompatActivity() {

    // 🔧 여기만 바꾸면 됨: by lazy로 즉시 초기화 (lateinit 제거)
    private val api: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://unparticularised-carneous-michaela.ngrok-free.dev/") // 에뮬레이터에서 PC 로컬 서버면 10.0.2.2
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }

    private val gson = Gson()

    private lateinit var etPhone: EditText
    private lateinit var tvError: TextView
    private lateinit var btnSubmit: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_id)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        etPhone = findViewById(R.id.etPhone)
        tvError = findViewById(R.id.tvError)
        btnSubmit = findViewById(R.id.btnSubmit)
        progress = findViewById(R.id.progress)

        btnSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        tvError.text = ""
        val phone = etPhone.text.toString().trim()

        if (phone.isEmpty()) {
            tvError.text = "전화번호를 입력하세요."
            return
        }
        if (!phone.all { it.isDigit() }) {
            tvError.text = "전화번호는 숫자만 입력하세요."
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    // ⚠️ DTO가 phoneNumber 필드이므로 이름 맞추기
                    api.findId(FindIdRequest(phoneNumber = phone))
                }
                handle(res)
            } catch (e: Exception) {
                tvError.text = "네트워크 오류: ${e.message}"
            } finally {
                setLoading(false)
            }
        }
    }

    private fun handle(res: Response<FindIdResponse>) {
        if (res.isSuccessful) {
            val msg = res.body()?.message ?: "이메일 전송 요청이 접수되었습니다."
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            val errStr = res.errorBody()?.string()
            val msg = try {
                gson.fromJson(errStr, ErrorResponse::class.java)?.error
                    ?: "요청 실패(${res.code()})"
            } catch (_: Exception) {
                "요청 실패(${res.code()})"
            }
            tvError.text = msg
        }
    }

    private fun setLoading(loading: Boolean) {
        btnSubmit.isEnabled = !loading
        progress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
    }
}
