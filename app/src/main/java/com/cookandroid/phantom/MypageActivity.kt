package com.cookandroid.phantom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    // ====== UI 뷰들 ======
    // 네비게이션
    private lateinit var tabSecurity: LinearLayout
    private lateinit var tabHome: LinearLayout
    private lateinit var tabMypage: LinearLayout
    private lateinit var ivSecurity: ImageView
    private lateinit var ivHome: ImageView
    private lateinit var ivMypage: ImageView
    private lateinit var tvSecurity: TextView
    private lateinit var tvHome: TextView
    private lateinit var tvMypage: TextView

    // 프로필
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvUserPhone: TextView
    private lateinit var btnEditProfile: ImageView

    // 플랜
    private lateinit var tvPlan: TextView
    private lateinit var btnUpgrade: Button

    // 보안 관리
    private lateinit var rowPrivacy: LinearLayout
    private lateinit var rowNoti: LinearLayout
    private lateinit var rowChangePassword: LinearLayout

    // 앱 정보
    private lateinit var rowDataUsage: LinearLayout
    private lateinit var rowFaq: LinearLayout
    private lateinit var rowAbout: LinearLayout
    private lateinit var tvVersion: TextView

    // 계정 관리
    private lateinit var btnLogout: LinearLayout
    private lateinit var btnDeleteAccount: LinearLayout

    // ====== 네트워크 ======
    private lateinit var userApi: MUserApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mypage)

        // Views 초기화
        initViews()

        // 네비게이션 설정
        setupNavigation()

        // Retrofit 준비
        userApi = buildRetrofitWithAuth_M(this).create(MUserApi::class.java)

        // 로그인 여부에 따라 UI 설정
        val isLoggedIn = !getToken_M(this).isNullOrBlank()
        setupUI(isLoggedIn)

        // 클릭 리스너 설정
        setupClickListeners(isLoggedIn)

        // 로그인 상태면 프로필 로드
        if (isLoggedIn) {
            fetchProfile()
        }
    }

    private fun initViews() {
        // 네비게이션
        tabSecurity = findViewById(R.id.tab_security)
        tabHome = findViewById(R.id.tab_home)
        tabMypage = findViewById(R.id.tab_mypage)
        ivSecurity = findViewById(R.id.ivSecurity)
        ivHome = findViewById(R.id.ivHome)
        ivMypage = findViewById(R.id.ivMypage)
        tvSecurity = findViewById(R.id.tvSecurity)
        tvHome = findViewById(R.id.tvHome)
        tvMypage = findViewById(R.id.tvMypage)

        // 프로필
        tvUserName = findViewById(R.id.tvUserName)
        tvUserEmail = findViewById(R.id.tvUserEmail)
        tvUserPhone = findViewById(R.id.tvUserPhone)
        btnEditProfile = findViewById(R.id.btnEditProfile)

        // 플랜
        tvPlan = findViewById(R.id.tvPlan)
        btnUpgrade = findViewById(R.id.btnUpgrade)

        // 보안 관리
        rowPrivacy = findViewById(R.id.rowPrivacy)
        rowNoti = findViewById(R.id.rowNoti)
        rowChangePassword = findViewById(R.id.rowChangePassword)

        // 앱 정보
        rowDataUsage = findViewById(R.id.rowDataUsage)
        rowFaq = findViewById(R.id.rowFaq)
        rowAbout = findViewById(R.id.rowAbout)
        tvVersion = findViewById(R.id.tvVersion)

        // 계정 관리
        btnLogout = findViewById(R.id.btnLogout)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)
    }

    private fun setupNavigation() {
        // 현재 페이지 강조 (마이페이지) - 텍스트만 변경
        tvMypage.setTextColor(ContextCompat.getColor(this, android.R.color.holo_purple))
        tvMypage.setTypeface(null, android.graphics.Typeface.BOLD)

        // 다른 탭들은 회색 - 텍스트만 변경
        tvSecurity.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        tvHome.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))

        tabSecurity.setOnClickListener {
            startActivity(Intent(this, SecurityActivity::class.java))
            finish()
        }
        tabHome.setOnClickListener {
            startActivity(Intent(this, MainPageActivity::class.java))
            finish()
        }
        tabMypage.setOnClickListener { /* 현재 화면 */ }
    }

    private fun setupUI(isLoggedIn: Boolean) {
        if (isLoggedIn) {
            // 로그인 상태
            tvUserName.text = "팬텀 사용자"
            tvUserEmail.visibility = View.VISIBLE
            tvUserPhone.visibility = View.VISIBLE
            btnLogout.visibility = View.VISIBLE
            btnDeleteAccount.visibility = View.VISIBLE
        } else {
            // 비로그인 상태
            tvUserName.text = "로그인이 필요합니다"
            tvUserEmail.visibility = View.GONE
            tvUserPhone.visibility = View.GONE
            btnLogout.visibility = View.GONE
            btnDeleteAccount.visibility = View.GONE
        }

        // 버전 정보
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            tvVersion.text = "v$versionName"
        } catch (e: Exception) {
            tvVersion.text = "v1.0.0"
        }
    }

    private fun setupClickListeners(isLoggedIn: Boolean) {
        // 프로필 수정 / 로그인
        btnEditProfile.setOnClickListener {
            if (isLoggedIn) {
                startActivity(Intent(this, ChangePasswordActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }

        // 프리미엄 업그레이드
        btnUpgrade.setOnClickListener {
            Toast.makeText(this, "프리미엄 플랜 준비중입니다", Toast.LENGTH_SHORT).show()
            // TODO: 결제 화면으로 이동
        }

        // 보안 관리
        rowPrivacy.setOnClickListener {
            Toast.makeText(this, "개인정보 권한 설정", Toast.LENGTH_SHORT).show()
            // TODO: 권한 설정 화면으로 이동
        }

        rowNoti.setOnClickListener {
            Toast.makeText(this, "알림 설정", Toast.LENGTH_SHORT).show()
            // TODO: 알림 설정 화면으로 이동
        }

        rowChangePassword.setOnClickListener {
            if (isLoggedIn) {
                startActivity(Intent(this, ChangePasswordActivity::class.java))
            } else {
                Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }

        // 앱 정보
        rowDataUsage.setOnClickListener {
            Toast.makeText(this, "데이터 사용 리포트", Toast.LENGTH_SHORT).show()
            // TODO: 데이터 리포트 화면으로 이동
        }

        rowFaq.setOnClickListener {
            Toast.makeText(this, "도움말 / FAQ", Toast.LENGTH_SHORT).show()
            // TODO: FAQ 화면으로 이동
        }

        rowAbout.setOnClickListener {
            showAboutDialog()
        }

        // 계정 관리
        if (isLoggedIn) {
            btnLogout.setOnClickListener {
                showLogoutConfirm()
            }

            btnDeleteAccount.setOnClickListener {
                showDeleteConfirm()
            }
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

                        // 플랜 정보 업데이트 (서버에서 받아온다면)
                        // tvPlan.text = body.plan ?: "Free Plan"
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

    // =================== 로그아웃 ===================
    private fun showLogoutConfirm() {
        AlertDialog.Builder(this)
            .setTitle("로그아웃")
            .setMessage("로그아웃 하시겠습니까?")
            .setNegativeButton("취소", null)
            .setPositiveButton("확인") { _, _ ->
                clearToken_M(this)
                Toast.makeText(this, "로그아웃 되었습니다", Toast.LENGTH_SHORT).show()
                goLoginClearTask()
            }
            .show()
    }

    // =================== 계정 탈퇴 ===================
    private fun showDeleteConfirm() {
        AlertDialog.Builder(this)
            .setTitle("계정 탈퇴")
            .setMessage("정말로 계정을 삭제하시겠습니까?\n탈퇴 후에는 데이터 복구가 불가능합니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ -> deleteAccount() }
            .show()
    }

    private fun deleteAccount() {
        setActionsEnabled(false)
        lifecycleScope.launch {
            try {
                val res: Response<MDeleteResponse> = withContext(Dispatchers.IO) {
                    userApi.deleteAccount()
                }
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

    // =================== 앱 정보 다이얼로그 ===================
    private fun showAboutDialog() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.0.0"
        }

        AlertDialog.Builder(this)
            .setTitle("팬텀 (Phantom)")
            .setMessage(
                """
                버전: $versionName
                
                실시간 보안 솔루션
                스팸/피싱 탐지 및 악성코드 차단
                
                © 2024 Phantom Security
                """.trimIndent()
            )
            .setPositiveButton("확인", null)
            .show()
    }

    // =================== 유틸리티 ===================
    private fun setActionsEnabled(enabled: Boolean) {
        btnLogout.isEnabled = enabled
        btnDeleteAccount.isEnabled = enabled
        btnLogout.alpha = if (enabled) 1.0f else 0.5f
        btnDeleteAccount.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun goLoginClearTask() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun formatPhone(raw: String?): String {
        val digits = raw?.filter { it.isDigit() }.orEmpty()
        if (digits.isBlank()) return "-"
        return when (digits.length) {
            10 -> "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6)}"
            11 -> "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}"
            else -> raw ?: "-"
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // 네비게이션 텍스트 강조 유지
        tvMypage.setTextColor(ContextCompat.getColor(this, android.R.color.holo_purple))
        tvMypage.setTypeface(null, android.graphics.Typeface.BOLD)
    }
}

// =================== 네트워크 모델 ===================
private data class MProfileResponse(
    val id: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val plan: String? = null
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

// =================== SharedPreferences ===================
private const val PREFS = "phantom_prefs"
private const val KEY_TOKEN = "jwt_token"

private fun getToken_M(ctx: Context): String? =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_TOKEN, null)

private fun clearToken_M(ctx: Context) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().remove(KEY_TOKEN).apply()
}

// =================== Retrofit ===================
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
        .baseUrl("http://10.0.2.2:8080/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}