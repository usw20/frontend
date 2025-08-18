package com.cookandroid.phantom

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cookandroid.phantom.StorageCleanActivity
import com.cookandroid.phantom.EasyCleanActivity

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

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_page)

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
                // ✅ 보안 페이지로 이동할 때도 fade로 통일
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
        tabHome.setOnClickListener {
            // 이미 홈: 필요하면 스크롤 맨 위로
            // findViewById<android.widget.ScrollView>(R.id.scrollContainer)?.smoothScrollTo(0, 0)
        }
        tabMypage.setOnClickListener {
            if (currentTab() != Tab.MYPAGE) {
                startActivity(Intent(this, MypageActivity::class.java))
                // (선택) 마이페이지 이동도 동일한 fade로 유지
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        // 유령 둥둥 애니메이션
        ghostAnim = TranslateAnimation(0f, 0f, 0f, 30f).apply {
            duration = 1000
            repeatCount = TranslateAnimation.INFINITE
            repeatMode = TranslateAnimation.REVERSE
        }
        findViewById<LinearLayout>(R.id.shortcut_easy).setOnClickListener {
            startActivity(Intent(this, EasyCleanActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 용량정리 버튼 클릭 시 이동
        findViewById<android.widget.LinearLayout>(R.id.shortcut_delete).setOnClickListener {
            startActivity(android.content.Intent(this, StorageCleanActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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
