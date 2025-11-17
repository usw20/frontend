package com.cookandroid.phantom

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// =======================================================
// (1) DTO — Auth / User (백엔드 스펙 통합)
// =======================================================
data class UserPayload(
    val id: String,
    val email: String,
    val isMalwareDetectionEnabled: Boolean? = null,
    val isPhishingDetectionEnabled: Boolean? = null,
    val phoneNumber: String? = null
)

data class SignUpRequest(val email: String, val password: String, val phoneNumber: String)
data class SignUpResponse(val message: String, val token: String, val user: UserPayload)
data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val message: String, val token: String, val user: UserPayload)

data class FindIdRequest(val phoneNumber: String)
data class FindIdResponse(val message: String)
data class ForgotPasswordRequest(val email: String)
data class ForgotPasswordResponse(val message: String)

// (A) 개별 비밀번호 변경 전용 DTO (엔드포인트: /api/auth/change-password)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
data class ChangePasswordResponse(val message: String)

// (B) 프로필 + (선택)비밀번호 변경을 한 번에 처리하는 DTO
data class UpdateProfileBody(
    val phoneNumber: String? = null,
    val changePassword: ChangePasswordBody? = null
) {
    data class ChangePasswordBody(
        val currentPassword: String,
        val newPassword: String
    )
}
data class UpdateProfileResponse(val message: String, val user: UserPayload)

// (C) 보안 토글 DTO (악성코드/피싱 공용)
data class SecuritySettingRequest(val isEnabled: Boolean)
data class SecuritySettingResponse(val message: String, val isEnabled: Boolean)

// (D) 공용 메시지/프로필 조회 응답
data class SimpleMessageResponse(val message: String)
data class ProfileResponse(val message: String? = null, val user: UserPayload)

data class ErrorResponse(
    val error: String? = null,
    val message: String? = null,
    val errors: Map<String, String>? = null
)

// =======================================================
// (1-2) DTO — Chatbot (서버/클라 이름 차이를 모두 수용)
// =======================================================
data class ChatbotMessageRequest(
    val message: String,
    val conversationId: String? = null
)

data class ChatbotMessageResponse(
    @SerializedName(value = "reply", alternate = ["response"])
    val reply: String,
    val conversationId: String? = null,
    val messageId: String? = null,
    val createdAt: String? = null,
    val category: String? = null,
    val responseTime: Double? = null,
    val requiresFollowup: Boolean? = null,
    val suggestedActions: String? = null
)

data class ChatbotConversationResponse(
    val conversationId: String,
    val messages: List<ConversationMessage>
) {
    data class ConversationMessage(
        val sender: String, // "USER" / "BOT"
        val text: String,
        val messageId: String
    )
}

data class ChatbotFeedbackRequest(
    val messageId: String,
    val isHelpful: Boolean,
    val comment: String? = null
)

// =======================================================
// (2) API 인터페이스 — 절대경로(/api/...) 사용
// =======================================================
interface AuthApi {
    @POST("/api/auth/signup")
    suspend fun signUp(@Body body: SignUpRequest): Response<SignUpResponse>

    @POST("/api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("/api/auth/find-id")
    suspend fun findId(@Body body: FindIdRequest): Response<FindIdResponse>

    @POST("/api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<ForgotPasswordResponse>

    // 개별 비밀번호 변경 (선택 사용)
    @PUT("/api/user/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<ChangePasswordResponse>
}

interface UserApi {
    // 프로필 수정 + (선택)비밀번호 변경을 한 번에
    @PUT("/api/user/profile")
    suspend fun updateProfile(@Body body: UpdateProfileBody): Response<UpdateProfileResponse>

    @GET("/api/user/profile")
    suspend fun getProfile(): Response<ProfileResponse>

    // 보안 토글
    @PUT("/api/user/settings/malware")
    suspend fun updateMalware(@Body body: SecuritySettingRequest): Response<SecuritySettingResponse>

    @PUT("/api/user/settings/phishing")
    suspend fun updatePhishing(@Body body: SecuritySettingRequest): Response<SecuritySettingResponse>

    @DELETE("/api/user/delete")
    suspend fun deleteAccount(): Response<SimpleMessageResponse>
}

// ✅ Chatbot API
interface ChatbotApi {
    @POST("/api/chatbot/message")
    suspend fun sendMessage(@Body body: ChatbotMessageRequest): Response<ChatbotMessageResponse>

    @GET("/api/chatbot/history")
    suspend fun getHistory(): Response<List<ChatbotConversationResponse>>

    @POST("/api/chatbot/feedback")
    suspend fun submitFeedback(@Body body: ChatbotFeedbackRequest): Response<Map<String, String>>

    @GET("/api/chatbot/statistics")
    suspend fun getStatistics(): Response<Map<String, Long>>
}

// =======================================================
// (3) 토큰 저장
// =======================================================
private const val PREFS = "phantom_prefs"
private const val KEY_TOKEN = "jwt_token"

private fun saveToken(ctx: Context, token: String?) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY_TOKEN, token).apply()
}
private fun getToken(ctx: Context): String? =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)
private fun clearToken(ctx: Context) = saveToken(ctx, null)

// =======================================================
// (4) Retrofit 빌더 (Authorization 자동 부착)
// =======================================================
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
        .baseUrl("https://unparticularised-carneous-michaela.ngrok-free.dev/") // 에뮬레이터→로컬 서버 (배포 시 도메인으로 교체)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}

// =======================================================
// (5) 단일 Activity — Auth + User + Security + Chatbot
// =======================================================
class AuthOneFileActivity : ComponentActivity() {

    // 공용 입력
    private lateinit var etEmail: EditText
    private lateinit var etPw: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCurPw: EditText
    private lateinit var etNewPw: EditText

    // 챗봇 입력
    private lateinit var etChatMsg: EditText
    private lateinit var etConvId: EditText
    private lateinit var etFeedbackMsgId: EditText
    private lateinit var swHelpful: Switch
    private lateinit var etFeedbackComment: EditText

    // 결과/로딩
    private lateinit var tvResult: TextView
    private lateinit var progress: ProgressBar

    // API
    private lateinit var authApi: AuthApi
    private lateinit var userApi: UserApi
    private lateinit var chatbotApi: ChatbotApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrofit 준비
        val retrofit = buildRetrofit(this)
        authApi = retrofit.create(AuthApi::class.java)
        userApi = retrofit.create(UserApi::class.java)
        chatbotApi = retrofit.create(ChatbotApi::class.java)

        // ---------- UI (동적) ----------
        val rootScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        rootScroll.addView(root)

        fun title(t: String): TextView = TextView(this).apply {
            text = t
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 24, 0, 8)
        }
        fun input(hint: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText =
            EditText(this).apply {
                this.hint = hint
                inputType = type
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 12
                }
            }
        fun btn(label: String, onClick: () -> Unit): Button =
            Button(this).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 10
                }
                setOnClickListener { onClick() }
            }

        // ===== 공통 입력 =====
        etEmail = input("이메일", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        etPw    = input("비밀번호", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        etPhone = input("전화번호(숫자만)", InputType.TYPE_CLASS_PHONE)
        etCurPw = input("현재 비밀번호(변경용)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        etNewPw = input("새 비밀번호(변경용)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

        progress = ProgressBar(this).apply { visibility = View.GONE }
        tvResult = TextView(this).apply { textSize = 13f; setPadding(0, 14, 0, 0) }

        // ===== Auth / User 버튼 =====
        val bSignUp        = btn("1.1 회원가입") { doSignUp() }
        val bLogin         = btn("1.2 로그인") { doLogin() }
        val bFindId        = btn("1.3 아이디 찾기") { doFindId() }
        val bForgotPw      = btn("1.4 비밀번호 재설정 링크") { doForgotPw() }
        val bChangePw      = btn("1.5 비밀번호 변경(전용 API)") { doChangePw() }
        val bUpdateProfile = btn("1.6 프로필 수정(+선택 비번 변경)") { doUpdateProfile() }
        val bGetProfile    = btn("1.7 프로필 조회") { doGetProfile() }
        val bDeleteAccount = btn("1.8 계정 탈퇴") { confirmDelete() }

        // ===== 보안 토글 버튼 =====
        val bMalwareOff = btn("1.9 악성코드 탐지 OFF") { doToggleMalware(false) }
        val bMalwareOn  = btn("1.10 악성코드 탐지 ON") { doToggleMalware(true) }
        val bPhishOff   = btn("1.11 피싱 탐지 OFF") { doTogglePhishing(false) }
        val bPhishOn    = btn("1.12 피싱 탐지 ON") { doTogglePhishing(true) }

        // ===== 챗봇 입력/버튼 =====
        etChatMsg = input("챗봇 메시지 입력")
        etConvId = input("conversationId (선택)", InputType.TYPE_CLASS_TEXT)
        etFeedbackMsgId = input("피드백 대상 messageId")
        swHelpful = Switch(this).apply { text = "도움이 됨(Helpful)"; isChecked = true }
        etFeedbackComment = input("피드백 코멘트(선택)")

        val bChatSend   = btn("2.1 챗봇 메시지 전송") { doChatSend() }
        val bChatHist   = btn("2.2 대화 히스토리 조회") { doChatHistory() }
        val bChatFb     = btn("2.3 응답 피드백 전송") { doChatFeedback() }
        val bChatStat   = btn("2.4 챗봇 통계 조회") { doChatStats() }

        // ===== 레이아웃 구성 =====
        root.addView(title("회원 관리 (All-in-One)"))
        root.addView(etEmail); root.addView(etPw); root.addView(etPhone)
        root.addView(etCurPw); root.addView(etNewPw)
        root.addView(bSignUp); root.addView(bLogin); root.addView(bFindId)
        root.addView(bForgotPw); root.addView(bChangePw); root.addView(bUpdateProfile); root.addView(bGetProfile); root.addView(bDeleteAccount)
        root.addView(bMalwareOff); root.addView(bMalwareOn); root.addView(bPhishOff); root.addView(bPhishOn)

        root.addView(title("챗봇 테스트"))
        root.addView(etChatMsg); root.addView(etConvId); root.addView(bChatSend)
        root.addView(bChatHist)
        root.addView(etFeedbackMsgId); root.addView(swHelpful); root.addView(etFeedbackComment); root.addView(bChatFb)
        root.addView(bChatStat)

        root.addView(progress)
        root.addView(title("결과"))
        root.addView(tvResult)

        setContentView(rootScroll)
    }

    // =======================================================
    // (6) 기능 구현 — Auth / User / Security
    // =======================================================
    private fun doSignUp() = launchUi {
        val email = etEmail.text.toString().trim()
        val pw = etPw.text.toString().trim()
        val phone = etPhone.text.toString().filter { it.isDigit() }
        val res = authApi.signUp(SignUpRequest(email, pw, phone))
        handle(res) {
            saveToken(this@AuthOneFileActivity, it.token)
            goMain()
        }
    }

    private fun doLogin() = launchUi {
        val email = etEmail.text.toString().trim()
        val pw = etPw.text.toString().trim()
        val res = authApi.login(LoginRequest(email, pw))
        handle(res) {
            saveToken(this@AuthOneFileActivity, it.token)
            goMain()
        }
    }

    private fun doFindId() = launchUi {
        val phone = etPhone.text.toString().filter { it.isDigit() }
        val res = authApi.findId(FindIdRequest(phone))
        handle(res)
    }

    private fun doForgotPw() = launchUi {
        val email = etEmail.text.toString().trim()
        val res = authApi.forgotPassword(ForgotPasswordRequest(email))
        handle(res)
    }

    // 전용 비번 변경 API (/api/auth/change-password)
    private fun doChangePw() = launchUi {
        val cur = etCurPw.text.toString().trim()
        val next = etNewPw.text.toString().trim()
        val res = authApi.changePassword(ChangePasswordRequest(cur, next))
        handle(res) {
            // (선택) 비번 변경 후 토큰 무효화 & 재로그인 유도
            // clearToken(this@AuthOneFileActivity)
            // goLoginClearTask()
        }
    }

    // 프로필 + (선택)비번 변경 동시 처리 (/api/user/profile)
    private fun doUpdateProfile() = launchUi {
        val phone = etPhone.text.toString().filter { it.isDigit() }.ifEmpty { null }
        val cur = etCurPw.text.toString().trim()
        val next = etNewPw.text.toString().trim()
        val change = if (cur.isNotBlank() || next.isNotBlank()) {
            if (cur.isBlank() || next.isBlank()) {
                toast("현재/새 비밀번호를 모두 입력하세요.")
                return@launchUi
            }
            UpdateProfileBody.ChangePasswordBody(cur, next)
        } else null

        val body = UpdateProfileBody(phoneNumber = phone, changePassword = change)
        val res = userApi.updateProfile(body)
        handle(res) {
            // (선택) 비번 바꿨다면 토큰 재발급 플로우 사용 권장
            if (change != null) {
                // clearToken(this@AuthOneFileActivity)
                // goLoginClearTask()
            }
        }
    }

    private fun doGetProfile() = launchUi {
        val res = userApi.getProfile()
        handle(res)
    }

    private fun doToggleMalware(enabled: Boolean) = launchUi {
        val res = userApi.updateMalware(SecuritySettingRequest(isEnabled = enabled))
        handle(res)
    }

    private fun doTogglePhishing(enabled: Boolean) = launchUi {
        val res = userApi.updatePhishing(SecuritySettingRequest(isEnabled = enabled))
        handle(res)
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("계정을 삭제하시겠습니까?")
            .setMessage("탈퇴 후에는 데이터 복구가 불가능합니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ -> doDeleteAccount() }
            .show()
    }

    private fun doDeleteAccount() {
        lifecycleScope.launch {
            setLoading(true)
            try {
                val res = withContext(Dispatchers.IO) { userApi.deleteAccount() }
                if (res.isSuccessful) {
                    val msg = res.body()?.message ?: "계정이 성공적으로 삭제되었습니다."
                    tvResult.text = "✅ $msg"
                    toast(msg)
                    clearToken(this@AuthOneFileActivity)
                    goLoginClearTask()
                } else {
                    val err = res.errorBody()?.string()
                    tvResult.text = "❌ 실패 (${res.code()})\n${err ?: "요청 실패"}"
                    if (res.code() == 401) {
                        clearToken(this@AuthOneFileActivity)
                        goLoginClearTask()
                    }
                }
            } catch (e: Exception) {
                tvResult.text = "❌ 예외: ${e.message}"
                toast("네트워크 오류: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    // =======================================================
    // (6-2) 기능 구현 — Chatbot
    // =======================================================
    private fun doChatSend() = launchUi {
        val msg = etChatMsg.text.toString().trim()
        val conv = etConvId.text.toString().trim().ifEmpty { null }
        val res = chatbotApi.sendMessage(ChatbotMessageRequest(message = msg, conversationId = conv))
        handle(res) { body ->
            body.conversationId?.let { etConvId.setText(it) }
            tvResult.append("\n\n🗨️ BOT: ${body.reply}")
        }
    }

    private fun doChatHistory() = launchUi {
        val res = chatbotApi.getHistory()
        handle(res) { list ->
            val sb = StringBuilder("📜 대화 히스토리 (${list.size})")
            list.forEachIndexed { i, conv ->
                sb.append("\n\n[${i + 1}] convId=${conv.conversationId}")
                conv.messages.forEach { m ->
                    sb.append("\n - ${m.sender}: ${m.text} (${m.messageId})")
                }
            }
            tvResult.text = "✅ 성공\n$sb"
        }
    }

    private fun doChatFeedback() = launchUi {
        val msgId = etFeedbackMsgId.text.toString().trim()
        val helpful = swHelpful.isChecked
        val comment = etFeedbackComment.text.toString().trim().ifEmpty { null }
        val res = chatbotApi.submitFeedback(
            ChatbotFeedbackRequest(messageId = msgId, isHelpful = helpful, comment = comment)
        )
        handle(res)
    }

    private fun doChatStats() = launchUi {
        val res = chatbotApi.getStatistics()
        handle(res) { map ->
            val t = map.entries.joinToString("\n") { (k, v) -> "• $k: $v" }
            tvResult.text = "✅ 성공\n$t"
        }
    }

    // =======================================================
    // (7) 공통 유틸
    // =======================================================
    private fun <T> handle(res: Response<T>, onSuccess: (T) -> Unit = {}) {
        try {
            val b = res.body()
            if (res.isSuccessful && b != null) {
                tvResult.text = "✅ 성공\n$b"
                onSuccess(b)
            } else {
                val err = res.errorBody()?.string()
                tvResult.text = "❌ 실패 (${res.code()})\n${err ?: "요청 실패"}"
            }
        } catch (e: Exception) {
            tvResult.text = "❌ 예외: ${e.message}"
        }
    }

    private fun launchUi(block: suspend () -> Unit) {
        lifecycleScope.launch {
            setLoading(true)
            try {
                withContext(Dispatchers.IO) { block() }
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(show: Boolean) {
        progress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun goMain() {
        startActivity(Intent(this, MainPageActivity::class.java))
        finish()
    }

    private fun goLoginClearTask() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
    }
}
