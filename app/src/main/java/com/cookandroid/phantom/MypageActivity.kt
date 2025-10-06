// app/src/main/java/com/cookandroid/phantom/MypageActivity.kt
package com.cookandroid.phantom

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

class MypageActivity : AppCompatActivity() {

    // ====== UI 색상/탭 ======
    private val PURPLE = Color.parseColor("#660099")
    private val BLACK  = Color.parseColor("#000000")

    private lateinit var tabSecurity: View
    private lateinit var tabHome: View
    private lateinit var tabMypage: View

    private lateinit var tvSecurity: TextView
    private lateinit var tvHome: TextView
    private lateinit var tvMypage: TextView

    private enum class Tab { HOME, SECURITY, MYPAGE }

    // ====== 프로필 UI ======
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvUserPhone: TextView
    private lateinit var btnEditProfile: TextView

    // ====== 액션 버튼 ======
    private lateinit var btnLogout: TextView
    private lateinit var btnDeleteAccount: TextView

    // ====== 네트워크 ======
    private lateinit var userApi: MUserApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mypage)

        // ---- 하단 탭 ----
        tabSecurity = findViewById(R.id.tab_security)
        tabHome     = findViewById(R.id.tab_home)
        tabMypage   = findViewById(R.id.tab_mypage)

        tvSecurity  = findViewById(R.id.tvSecurity)
        tvHome      = findViewById(R.id.tvHome)
        tvMypage    = findViewById(R.id.tvMypage)

        highlight(Tab.MYPAGE)

        tabSecurity.setOnClickListener {
            if (currentTab() != Tab.SECURITY) {
                startActivity(Intent(this, SecurityActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        }
        tabHome.setOnClickListener {
            if (currentTab() != Tab.HOME) {
                startActivity(Intent(this, MainPageActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        }
        tabMypage.setOnClickListener { /* 현재 탭 */ }

        // ---- 프로필 뷰 ----
        tvUserName      = findViewById(R.id.tvUserName)
        tvUserEmail     = findViewById(R.id.tvUserEmail)
        tvUserPhone     = findViewById(R.id.tvUserPhone)
        btnEditProfile  = findViewById(R.id.btnEditProfile)

        // ---- 액션 버튼 ----
        btnLogout        = findViewById(R.id.btnLogout)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)

        // ---- Retrofit 준비 (/api/ base) ----
        userApi = buildRetrofitWithAuth_M(this).create(MUserApi::class.java)

        // ---- 로그인 여부에 따라 UI 스위칭 ----
        val isLoggedIn = !getToken_M(this).isNullOrBlank()
        if (isLoggedIn) {
            // 로그인 상태
            tvUserName.visibility = View.VISIBLE
            tvUserName.text = "팬텀 사용자"

            tvUserEmail.visibility = View.VISIBLE
            tvUserPhone.visibility = View.VISIBLE
            btnLogout.visibility = View.VISIBLE
            btnDeleteAccount.visibility = View.VISIBLE

            // ▶ 프로필 수정 → 비밀번호 변경 화면
            btnEditProfile.text = "비밀번호 변경"
            btnEditProfile.setTextColor(PURPLE)
            btnEditProfile.setOnClickListener {
                startActivity(Intent(this, ChangePasswordActivity::class.java))
            }

            btnLogout.setOnClickListener {
                clearToken_M(this)
                Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
                goLoginClearTask()
            }
            btnDeleteAccount.setOnClickListener { showDeleteConfirm() }

            // 프로필 로딩
            fetchProfile()
        } else {
            // 비로그인 상태
            tvUserName.visibility = View.VISIBLE
            tvUserName.text = "로그인이 필요합니다"

            tvUserEmail.visibility = View.GONE
            tvUserPhone.visibility = View.GONE
            btnLogout.visibility = View.GONE
            btnDeleteAccount.visibility = View.GONE

            btnEditProfile.text = "로그인"
            btnEditProfile.setTextColor(PURPLE)
            btnEditProfile.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        highlight(Tab.MYPAGE)
    }

    private fun currentTab(): Tab = Tab.MYPAGE

    private fun highlight(tab: Tab) {
        fun TextView.tint(c: Int) = setTextColor(c)
        when (tab) {
            Tab.HOME     -> { tvHome.tint(PURPLE);    tvSecurity.tint(BLACK); tvMypage.tint(BLACK) }
            Tab.SECURITY -> { tvSecurity.tint(PURPLE); tvHome.tint(BLACK);    tvMypage.tint(BLACK) }
            Tab.MYPAGE   -> { tvMypage.tint(PURPLE);  tvHome.tint(BLACK);     tvSecurity.tint(BLACK) }
        }
    }

    // =================== 프로필 불러오기 ===================
    private fun fetchProfile() {
        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { userApi.getProfile() }
                if (res.isSuccessful) {
                    val body = res.body()
                    if (body != null) {
                        tvUserEmail.text = body.email ?: "-"
                        tvUserPhone.text = formatPhone(body.phoneNumber)
                    } else {
                        toast("프로필 응답이 비어있습니다.")
                    }
                } else {
                    toast("프로필 불러오기 실패 (${res.code()})")
                    if (res.code() == 401) {
                        clearToken_M(this@MypageActivity)
                        goLoginClearTask()
                    }
                }
            } catch (e: Exception) {
                toast("프로필 오류: ${e.message}")
            }
        }
    }

    private fun showDeleteConfirm() {
        AlertDialog.Builder(this)
            .setTitle("계정을 삭제하시겠습니까?")
            .setMessage("탈퇴 후에는 데이터 복구가 불가능합니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ -> deleteAccount() }
            .show()
    }

    private fun deleteAccount() {
        setActionsEnabled(false) // 버튼 중복 클릭 방지
        lifecycleScope.launch {
            try {
                val res: Response<MDeleteResponse> = withContext(Dispatchers.IO) { userApi.deleteAccount() }
                if (res.isSuccessful) {
                    val msg = res.body()?.message ?: "계정이 성공적으로 삭제되었습니다."
                    toast(msg)
                    clearToken_M(this@MypageActivity)
                    goLoginClearTask()
                } else {
                    toast("삭제 실패 (${res.code()})")
                    if (res.code() == 401) {
                        clearToken_M(this@MypageActivity)
                        goLoginClearTask()
                    }
                }
            } catch (e: Exception) {
                toast("네트워크 오류: ${e.message}")
            } finally {
                setActionsEnabled(true)
            }
        }
    }

    private fun setActionsEnabled(enabled: Boolean) {
        btnLogout.isEnabled = enabled
        btnDeleteAccount.isEnabled = enabled
    }

    private fun goLoginClearTask() {
        val i = Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(i)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun formatPhone(raw: String?): String {
        val d = raw?.filter { it.isDigit() }.orEmpty()
        if (d.isBlank()) return "-"
        return when (d.length) {
            10 -> "${d.substring(0,3)}-${d.substring(3,6)}-${d.substring(6)}"
            11 -> "${d.substring(0,3)}-${d.substring(3,7)}-${d.substring(7)}"
            else -> raw ?: "-"
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

// =================== (이 파일 전용) 네트워크 유틸/모델 ===================

private data class MProfileResponse(
    val id: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null
)

private data class MDeleteResponse(
    val message: String? = null
)

private interface MUserApi {
    @GET("/api/user/profile")
    suspend fun getProfile(): Response<MProfileResponse>

    @DELETE("/api/user/delete")
    suspend fun deleteAccount(): Response<MDeleteResponse>
}

// ===== SharedPreferences (토큰) =====
private const val PREFS = "phantom_prefs"
private const val KEY_TOKEN = "jwt_token"

private fun getToken_M(ctx: Context): String? =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_TOKEN, null)

private fun clearToken_M(ctx: Context) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_TOKEN, null).apply()
}

// ===== Retrofit + JWT 인터셉터 + 타임아웃 =====
private fun buildRetrofitWithAuth_M(ctx: Context): Retrofit {
    val authInterceptor = Interceptor { chain ->
        val req = chain.request()
        val token = getToken_M(ctx)
        val newReq = if (!token.isNullOrBlank()) {
            req.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else req
        chain.proceed(newReq)
    }

    val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    return Retrofit.Builder()
        // 에뮬레이터 ↔ PC: 10.0.2.2 (물리 기기면 PC LAN IP로 교체)
        .baseUrl("http://10.0.2.2:8080/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
