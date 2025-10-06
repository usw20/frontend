package com.cookandroid.phantom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class InfoHostActivity : AppCompatActivity() {

    enum class Page { USAGE, SECURITY_KNOWLEDGE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info_host)

        val page = intent.getSerializableExtra(EXTRA_PAGE) as? Page ?: Page.USAGE
        val title = findViewById<TextView>(R.id.tvTitle)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val fragment = when (page) {
            Page.USAGE -> {
                title.text = "사용법 안내"
                UsageGuideFragment()
            }
            Page.SECURITY_KNOWLEDGE -> {
                title.text = "모바일 보안 상식"
                SecurityKnowledgeFragment()
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    companion object {
        private const val EXTRA_PAGE = "extra_page"
        fun start(context: Context, page: Page) {
            context.startActivity(
                Intent(context, InfoHostActivity::class.java).putExtra(EXTRA_PAGE, page)
            )
        }
    }
}
