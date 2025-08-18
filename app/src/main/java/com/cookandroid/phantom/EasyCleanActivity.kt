package com.cookandroid.phantom

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView

class EasyCleanActivity : AppCompatActivity() {

    private lateinit var tvCacheSize: TextView
    private lateinit var tvTempCount: TextView
    private lateinit var tvResult: TextView
    private lateinit var btnClean: MaterialButton

    private val ui = Handler(Looper.getMainLooper())
    private var working = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_easy_clean)
        supportActionBar?.hide()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        tvCacheSize = findViewById(R.id.tvCacheSize)
        tvTempCount = findViewById(R.id.tvTempCount)
        tvResult    = findViewById(R.id.tvResult)
        btnClean    = findViewById(R.id.btnCleanNow)

        // 초기값(예시)
        tvCacheSize.text = "420 MB"
        tvTempCount.text = "37개"

        btnClean.setOnClickListener { runDemoClean() }

        savedInstanceState?.let {
            working = it.getBoolean("working", false)
            tvCacheSize.text = it.getString("cache", tvCacheSize.text.toString())
            tvTempCount.text = it.getString("temp", tvTempCount.text.toString())
            tvResult.text    = it.getString("result", "")
            setWorkingUi(working)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("working", working)
        outState.putString("cache", tvCacheSize.text?.toString())
        outState.putString("temp", tvTempCount.text?.toString())
        outState.putString("result", tvResult.text?.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
    }

    private fun setWorkingUi(busy: Boolean) {
        btnClean.isEnabled = !busy
        btnClean.text = if (busy) "정리 중..." else "간편정리 실행"
        findViewById<android.view.View>(R.id.progressWrap).visibility =
            if (busy) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun runDemoClean() {
        if (working) return
        working = true
        setWorkingUi(true)
        tvResult.text = ""

        // 숫자 애니메이션 (420→0MB, 37→0개)
        animateNumber(420) { v -> tvCacheSize.text = "$v MB" }
        animateNumber(37)  { v -> tvTempCount.text = "${v}개" }

        ui.postDelayed({
            working = false
            setWorkingUi(false)
            tvResult.text = "캐시와 임시파일 정리가 완료됐어요."
            Toast.makeText(this, "간편정리 완료 (데모)", Toast.LENGTH_SHORT).show()
        }, 1500)
    }


    private fun animateNumber(from: Int, onUpdate: (Int) -> Unit) {
        ValueAnimator.ofInt(from, 0).apply {
            duration = 900
            addUpdateListener { onUpdate(it.animatedValue as Int) }
            start()
        }
    }
}
