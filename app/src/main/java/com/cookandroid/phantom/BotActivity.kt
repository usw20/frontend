package com.cookandroid.phantom

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookandroid.phantom.R
import com.cookandroid.phantom.chat.ChatAdapter
import com.cookandroid.phantom.chat.ChatMessage
import com.cookandroid.phantom.chat.Sender
// ✅ DTO는 AuthOneFileActivity.kt에 있는 것을 사용해야 함 (패키지 com.cookandroid.phantom)
import com.cookandroid.phantom.ChatbotMessageRequest
import com.cookandroid.phantom.ChatbotMessageResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// ⚠️ ChatbotApi는 AuthOneFileActivity.kt 안에 이미 선언되어 있으므로 재선언하지 않습니다.

class BotActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var et: EditText
    private lateinit var btn: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var adapter: ChatAdapter

    // 백엔드 호출용
    private lateinit var chatbotApi: ChatbotApi
    private var conversationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bot)

        // 뒤로가기
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        // Retrofit(API) 준비 (ChatbotApi는 다른 파일에 이미 선언되어 있음)
        chatbotApi = buildChatbotRetrofit(this)

        rv  = findViewById(R.id.rvChat)
        et  = findViewById(R.id.etMessage)
        btn = findViewById(R.id.btnSend)

        adapter = ChatAdapter(mutableListOf())
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

        // 엔터 전송
        et.imeOptions = EditorInfo.IME_ACTION_SEND
        et.setSingleLine(true)

        // 시작 멘트
        adapter.add(
            ChatMessage(
                "안녕하세요! 팬텀 봇입니다. 스팸/피싱 의심 내용이나 보안 질문을 보내주세요.",
                Sender.BOT
            )
        )
        scrollToBottom()

        btn.setOnClickListener { sendMessage() }
        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage(); true
            } else false
        }
    }

    private fun sendMessage() {
        val text = et.text.toString().trim()
        if (text.isEmpty()) return

        // 1) 유저 메시지 표시
        adapter.add(ChatMessage(text, Sender.USER))
        et.setText("")
        scrollToBottom()

        // 2) 타이핑 표시
        adapter.add(ChatMessage("", Sender.TYPING))
        scrollToBottom()

        // 3) 서버 호출
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

            // 타이핑 제거
            adapter.removeLastIfTyping()

            result.onSuccess { res ->
                if (res.isSuccessful && res.body() != null) {
                    val body: ChatbotMessageResponse = res.body()!!
                    // ✅ AuthOneFileActivity의 DTO에 맞춰 reply 사용 (오타 repl 금지)
                    conversationId = body.conversationId
                    adapter.add(ChatMessage(body.reply, Sender.BOT))
                } else {
                    val errText = res.errorBody()?.string()
                    adapter.add(
                        ChatMessage(
                            "서버 오류: ${res.code()} ${errText ?: ""}",
                            Sender.BOT
                        )
                    )
                }
                scrollToBottom()
            }.onFailure { e ->
                adapter.add(
                    ChatMessage(
                        "네트워크 오류: ${e.localizedMessage ?: "알 수 없는 오류"}",
                        Sender.BOT
                    )
                )
                scrollToBottom()
            }
        }
    }

    private fun scrollToBottom() {
        rv.post { rv.scrollToPosition(adapter.itemCount - 1) }
    }
}

/* =======================
   같은 파일 내 유틸 (중복 선언/중복 타입 금지)
   ======================= */

// 토큰 부착 + Retrofit 빌더
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
                .addHeader("Authorization", "Bearer $token") // AuthOneFileActivity와 동일 포맷
                .build()
        } else chain.request()
        chain.proceed(req)
    }

    val client = OkHttpClient.Builder()
        .addInterceptor(auth)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8080/") // 로컬 스프링 서버(에뮬레이터). 배포 시 https://도메인/ 로 교체
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    return retrofit.create(ChatbotApi::class.java)
}
