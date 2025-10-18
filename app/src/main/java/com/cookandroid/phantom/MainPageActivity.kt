package com.cookandroid.phantom

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cookandroid.phantom.data.local.TokenDataStore
import kotlinx.coroutines.launch

class MainPageActivity : AppCompatActivity() {

    // 색상
    private val PURPLE = Color.parseColor("#660099")
    private val BLACK  = Color.parseColor("#000000")

    // 탭 뷰들
    private lateinit var tabSecurity: LinearLayout
    private lateinit var tabHome: LinearLayout
    private lateinit var tabMypage: LinearLayout

    private lateinit var ivSecurity: ImageView
    private lateinit var ivHome: ImageView
    private lateinit var ivMypage: ImageView
    private lateinit var tvSecurity: TextView
    private lateinit var tvHome: TextView
    private lateinit var tvMypage: TextView

    // 유령 애니메이션
    private var ghostAnim: TranslateAnimation? = null
    private lateinit var ghost: ImageView

    // 토큰 저장소
    private lateinit var tokenStore: TokenDataStore

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_page)

        // TokenDataStore 초기화
        tokenStore = TokenDataStore(this)

        // findViews
        tabSecurity = findViewById(R.id.tab_security)
        tabHome     = findViewById(R.id.tab_home)
        tabMypage   = findViewById(R.id.tab_mypage)

        ivSecurity = findViewById(R.id.ivSecurity)
        ivHome     = findViewById(R.id.ivHome)
        ivMypage   = findViewById(R.id.ivMypage)
        tvSecurity = findViewById(R.id.tvSecurity)
        tvHome     = findViewById(R.id.tvHome)
        tvMypage   = findViewById(R.id.tvMypage)

        ghost = findViewById(R.id.ghostImage)

        // 홈 화면이므로 "홈"만 보라색으로 하이라이트 (아이콘은 그대로, 글자만 변경)
        highlightTab(current = Tab.HOME)

        // 탭 이동
        tabSecurity.setOnClickListener {
            if (currentTab() != Tab.SECURITY) {
                startActivity(Intent(this, SecurityActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
        tabHome.setOnClickListener {
            // 이미 홈: 필요하면 스크롤 맨 위로
        }
        tabMypage.setOnClickListener {
            if (currentTab() != Tab.MYPAGE) {
                startActivity(Intent(this, MypageActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        // 유령 둥둥 애니메이션
        ghostAnim = TranslateAnimation(0f, 0f, 0f, 30f).apply {
            duration = 1000
            repeatCount = TranslateAnimation.INFINITE
            repeatMode = TranslateAnimation.REVERSE
        }

        // 간편정리 버튼 - 로그인 체크
        findViewById<LinearLayout>(R.id.shortcut_easy).setOnClickListener {
            checkLoginAndNavigate(EasyCleanActivity::class.java)
        }

        // 스팸 버튼 - 로그인 체크
        findViewById<LinearLayout>(R.id.shortcut_delete).setOnClickListener {
            checkLoginAndNavigate(`SpamCheckActivity`::class.java)
        }

        // 스팸차단 버튼 - 로그인 체크
        findViewById<View>(R.id.shortcut_spam).setOnClickListener {
            checkLoginAndNavigate(BotActivity::class.java)
        }

        findViewById<View>(R.id.btnOpenUsage).setOnClickListener {
            InfoHostActivity.start(this, InfoHostActivity.Page.USAGE)
        }
        findViewById<View>(R.id.btnOpenKnowledge).setOnClickListener {
            InfoHostActivity.start(this, InfoHostActivity.Page.SECURITY_KNOWLEDGE)
        }
    }

    override fun onResume() {
        super.onResume()
        ghost.startAnimation(ghostAnim)
        highlightTab(Tab.HOME)
    }

    override fun onPause() {
        super.onPause()
        ghost.clearAnimation()
    }

    /**
     * 로그인 상태를 확인하고 페이지로 이동하는 함수
     * - 로그인되어 있으면: 목적지 Activity로 이동
     * - 로그인 안 되어 있으면: LoginActivity로 이동
     */
    private fun checkLoginAndNavigate(destination: Class<*>) {
        lifecycleScope.launch {
            val token = tokenStore.getToken()

            if (token.isNullOrEmpty()) {
                // 로그인 안 됨 -> 로그인 페이지로
                val intent = Intent(this@MainPageActivity, LoginActivity::class.java).apply {
                    // 로그인 후 돌아올 수 있도록 플래그 설정 (선택사항)
                    // 또는 단순히 로그인 페이지로만 이동
                }
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                // 로그인 됨 -> 목적지로 이동
                startActivity(Intent(this@MainPageActivity, destination))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
    }

    // ---- 유틸 ----
    private enum class Tab { HOME, SECURITY, MYPAGE }

    private fun currentTab(): Tab = Tab.HOME // 이 화면은 항상 홈

    private fun highlightTab(current: Tab) {
        fun TextView.tint(color: Int) { setTextColor(color) }

        when (current) {
            Tab.HOME -> {
                tvHome.tint(PURPLE)
                tvSecurity.tint(BLACK)
                tvMypage.tint(BLACK)
            }
            Tab.SECURITY -> {
                tvSecurity.tint(PURPLE)
                tvHome.tint(BLACK)
                tvMypage.tint(BLACK)
            }
            Tab.MYPAGE -> {
                tvMypage.tint(PURPLE)
                tvHome.tint(BLACK)
                tvSecurity.tint(BLACK)
            }
        }
    }
}