package com.cookandroid.phantom

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MypageActivity : AppCompatActivity() {

    private val PURPLE = Color.parseColor("#660099")
    private val BLACK  = Color.parseColor("#000000")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mypage)

        // 하단 탭
        val tabSecurity = findViewById<View>(R.id.tab_security)
        val tabHome     = findViewById<View>(R.id.tab_home)
        val tabMypage   = findViewById<View>(R.id.tab_mypage)

        val tvSecurity  = findViewById<TextView>(R.id.tvSecurity)
        val tvHome      = findViewById<TextView>(R.id.tvHome)
        val tvMypage    = findViewById<TextView>(R.id.tvMypage)

        // 현재 화면: 마이페이지 강조 (아이콘은 그대로, 글자색만)
        tvSecurity.setTextColor(BLACK)
        tvHome.setTextColor(BLACK)
        tvMypage.setTextColor(PURPLE)

        // 탭 이동: 모두 페이드 애니메이션 통일
        tabSecurity.setOnClickListener {
            startActivity(Intent(this, SecurityActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        tabHome.setOnClickListener {
            startActivity(Intent(this, MainPageActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        tabMypage.setOnClickListener {
            // 현재 페이지라서 동작 없음
        }
    }
}
