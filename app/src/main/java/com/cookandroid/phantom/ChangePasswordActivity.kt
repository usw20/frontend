package com.cookandroid.phantom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// ⚠️ AuthOneFileActivity.kt 안의 인터페이스/DTO를 그대로 사용합니다.
// interface AuthApi { @PUT("/api/auth/change-password") ... }
// data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var etCurrent: EditText
    private lateinit var etNew: EditText
    private lateinit var etConfirm: EditText
    private lateinit var btnSubmit: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvHint: TextView

    // ====== 로컬 util: 토큰 & Retrofit (AuthOneFileActivity의 로직을 최소 복제) ======
    private val PREFS = "phantom_prefs"
    private val KEY_TOKEN = "jwt_token"

    private fun getToken(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    private fun clearToken(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOKEN, null).apply()
    }

    private fun buildRetrofit(ctx: Context): Retrofit {
        val authInterceptor = Interceptor { chain ->
            val req = chain.request()
            val token = getToken(ctx)
            val newReq = if (!token.isNullOrBlank()) {
                req.newBuilder().addHeader("Authorization", "Bearer $token").build()
            } else req
            chain.proceed(newReq)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://etha-unbeloved-supersensually.ngrok-free.dev/")  // 에뮬레이터 → 로컬서버. 배포 시 교체
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val api: AuthApi by lazy {
        buildRetrofit(this).create(AuthApi::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        etCurrent = findViewById(R.id.etCurrentPassword)
        etNew = findViewById(R.id.etNewPassword)
        etConfirm = findViewById(R.id.etConfirmPassword)
        btnSubmit = findViewById(R.id.btnChangePassword)
        progress = findViewById(R.id.progress)
        tvHint = findViewById(R.id.tvPolicy)

        findViewById<ImageButton?>(R.id.btnBack)?.setOnClickListener { finish() }
        btnSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        val cur = etCurrent.text.toString().trim()
        val next = etNew.text.toString().trim()
        val confirm = etConfirm.text.toString().trim()

        if (cur.isEmpty() || next.isEmpty() || confirm.isEmpty()) {
            toast("모든 필드를 입력하세요.")
            return
        }
        if (next.length < 8) {
            toast("새 비밀번호는 8자 이상이어야 합니다.")
            return
        }
        if (next != confirm) {
            toast("새 비밀번호가 일치하지 않습니다.")
            return
        }
        // 간단 강도 체크: 문자/숫자/특수 중 2종 이상
        val kinds = listOf(
            Regex(".*[A-Za-z].*").containsMatchIn(next),
            Regex(".*\\d.*").containsMatchIn(next),
            Regex(".*[^A-Za-z0-9].*").containsMatchIn(next)
        ).count { it }
        if (kinds < 2) {
            tvHint.visibility = View.VISIBLE
            toast("문자/숫자/특수문자 중 2가지 이상을 포함해 주세요.")
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                val res = withContext(Dispatchers.IO) {
                    api.changePassword(ChangePasswordRequest(cur, next))
                }
                if (res.isSuccessful) {
                    val msg = res.body()?.message ?: "비밀번호가 변경되었습니다."
                    toast(msg)
                    // 보안상: 토큰 제거 후 재로그인 권장
                    clearToken(this@ChangePasswordActivity)
                    goLoginClearTask()
                } else {
                    val errText = res.errorBody()?.string()
                    toast("변경 실패(${res.code()}): ${errText ?: "요청 실패"}")
                }
            } catch (e: HttpException) {
                toast("요청 오류: ${e.code()}")
            } catch (e: Exception) {
                toast("네트워크 오류: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(show: Boolean) {
        progress.visibility = if (show) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !show
    }

    private fun goLoginClearTask() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
