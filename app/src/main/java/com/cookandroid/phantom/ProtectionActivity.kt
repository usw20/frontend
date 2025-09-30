package com.cookandroid.phantom

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class ProtectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_protection)

        val backButton = findViewById<ImageButton>(R.id.back_button)
        val stopButton = findViewById<Button>(R.id.stop_protection_button)

        // 뒤로가기 버튼 → 메인페이지 이동
        backButton.setOnClickListener {
            val intent = Intent(this, MainPageActivity::class.java)
            // 기존 스택의 다른 액티비티 제거하고 메인만 남기기
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        // 보호 끄기 버튼 → 메인페이지 이동
        stopButton.setOnClickListener {
            val intent = Intent(this, MainPageActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }
}
