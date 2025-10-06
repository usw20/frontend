package com.cookandroid.phantom

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val splashDelayMs = 2500L // 2~3초 범위에서 조절 가능

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ⬇️ 스플래시로 쓰는 레이아웃 (지금 파일명이 activity_main이면 그대로 둬도 OK)
        setContentView(R.layout.activity_main)
        // ✅ 지연 후 메인 페이지로 이동
        lifecycleScope.launch {
            delay(splashDelayMs)
            startActivity(Intent(this@MainActivity, MainPageActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
