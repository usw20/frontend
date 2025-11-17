package com.cookandroid.phantom

import android.os.Bundle
import android.view.animation.TranslateAnimation
import android.view.animation.ScaleAnimation
import android.view.animation.AnimationSet
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookandroid.phantom.R
import com.cookandroid.phantom.chat.ChatAdapter
import com.cookandroid.phantom.chat.ChatMessage
import com.cookandroid.phantom.chat.Sender
import com.cookandroid.phantom.ChatbotMessageRequest
import com.cookandroid.phantom.ChatbotMessageResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class BotActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var et: EditText
    private lateinit var btn: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var adapter: ChatAdapter
    private lateinit var chatbotApi: ChatbotApi

    private var ivGhost: ImageView? = null
    private var conversationId: String? = null

    // 👻 애니메이션들
    private lateinit var ghostFloatAnim: TranslateAnimation
    private lateinit var ghostTalkAnim: AnimationSet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bot)

        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        chatbotApi = buildChatbotRetrofit(this)

        rv = findViewById(R.id.rvChat)
        et = findViewById(R.id.etMessage)
        btn = findViewById(R.id.btnSend)
        ivGhost = findViewById(R.id.ivBotAvatarOverlay)

        // ✅ 기본 떠다니는 애니메이션
        ghostFloatAnim = TranslateAnimation(0f, 0f, 0f, 30f).apply {
            duration = 1000L
            repeatCount = TranslateAnimation.INFINITE
            repeatMode = TranslateAnimation.REVERSE
        }

        // ✅ 말하는 애니메이션 (튕기는 효과)
        ghostTalkAnim = AnimationSet(true).apply {
            // 위아래로 빠르게 튕기기
            val bounce = TranslateAnimation(0f, 0f, 0f, -15f).apply {
                duration = 200L
                repeatCount = 5
                repeatMode = TranslateAnimation.REVERSE
            }
            // 살짝 커졌다 작아지기
            val scale = ScaleAnimation(
                1f, 1.1f, 1f, 1.1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 200L
                repeatCount = 5
                repeatMode = ScaleAnimation.REVERSE
            }
            addAnimation(bounce)
            addAnimation(scale)

            // 애니메이션 끝나면 다시 떠다니기
            setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    ivGhost?.startAnimation(ghostFloatAnim)
                }
            })
        }

        ivGhost?.startAnimation(ghostFloatAnim)

        adapter = ChatAdapter(mutableListOf())
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

        et.imeOptions = EditorInfo.IME_ACTION_SEND
        et.setSingleLine(true)

        adapter.add(
            ChatMessage(
                "안녕하세요! 팬텀 봇입니다. 스팸/피싱 의심 내용이나 보안 질문을 보내주세요.",
                Sender.BOT
            )
        )
        scrollToBottom()
        // 시작 메시지 보낼 때 말하는 애니메이션
        playTalkAnimation()

        btn.setOnClickListener { sendMessage() }
        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage(); true
            } else false
        }
    }

    override fun onResume() {
        super.onResume()
        ivGhost?.startAnimation(ghostFloatAnim)
    }

    override fun onPause() {
        ivGhost?.clearAnimation()
        super.onPause()
    }

    // 👻 봇이 말할 때 애니메이션 재생
    private fun playTalkAnimation() {
        ivGhost?.clearAnimation()
        ivGhost?.startAnimation(ghostTalkAnim)
    }

    private fun sendMessage() {
        val text = et.text.toString().trim()
        if (text.isEmpty()) return

        adapter.add(ChatMessage(text, Sender.USER))
        et.setText("")
        scrollToBottom()

        adapter.add(ChatMessage("", Sender.TYPING))
        scrollToBottom()

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    chatbotApi.sendMessage(
                        ChatbotMessageRequest(
                            message = text,
                            conversationId = conversationId
                        )
                    )
                }
            }

            adapter.removeLastIfTyping()

            result.onSuccess { res ->
                if (res.isSuccessful && res.body() != null) {
                    val body: ChatbotMessageResponse = res.body()!!
                    conversationId = body.conversationId
                    adapter.add(ChatMessage(body.reply, Sender.BOT))
                    // ✅ 봇이 답장할 때 말하는 애니메이션!
                    playTalkAnimation()
                } else {
                    val errText = res.errorBody()?.string()
                    adapter.add(
                        ChatMessage(
                            "서버 오류: ${res.code()} ${errText ?: ""}",
                            Sender.BOT
                        )
                    )
                    playTalkAnimation()
                }
                scrollToBottom()
            }.onFailure { e ->
                adapter.add(
                    ChatMessage(
                        "네트워크 오류: ${e.localizedMessage ?: "알 수 없는 오류"}",
                        Sender.BOT
                    )
                )
                playTalkAnimation()
                scrollToBottom()
            }
        }
    }

    private fun scrollToBottom() {
        rv.post { rv.scrollToPosition(adapter.itemCount - 1) }
    }
}

/* =======================
   Retrofit + Token 유틸
   ======================= */
private const val PREFS = "phantom_prefs"
private const val KEY_TOKEN = "jwt_token"

private fun getToken(ctx: android.content.Context): String? =
    ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        .getString(KEY_TOKEN, null)

private fun buildChatbotRetrofit(ctx: android.content.Context): ChatbotApi {
    val auth = Interceptor { chain ->
        val token = getToken(ctx)
        val req = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else chain.request()
        chain.proceed(req)
    }

    val client = OkHttpClient.Builder()
        .addInterceptor(auth)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl("https://unparticularised-carneous-michaela.ngrok-free.dev/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    return retrofit.create(ChatbotApi::class.java)
}