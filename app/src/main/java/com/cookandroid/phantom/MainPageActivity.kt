package com.cookandroid.phantom

import android.os.Bundle
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainPageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_page)
        val ghost = findViewById<ImageView>(R.id.ghostImage)
        val animation = TranslateAnimation(0f, 0f, 0f, 30f).apply {
            duration = 1000
            repeatCount = TranslateAnimation.INFINITE
            repeatMode = TranslateAnimation.REVERSE
        }
        ghost.startAnimation(animation)
    }
}
