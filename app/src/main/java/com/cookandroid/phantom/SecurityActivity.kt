package com.cookandroid.phantom

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SecurityActivity : ComponentActivity() {

    companion object {
        const val ACTION_INC_SPAM = "com.cookandroid.phantom.ACTION_INC_SPAM"
        const val ACTION_INC_MALWARE = "com.cookandroid.phantom.ACTION_INC_MALWARE"
        const val EXTRA_MESSAGE = "message"
    }

    // 색상 (아이콘은 고정, 글자만 이 색으로)
    private val PURPLE = 0xFF660099.toInt()
    private val BLACK  = 0xFF000000.toInt()

    private lateinit var tvSpam: TextView
    private lateinit var tvMalware: TextView
    private lateinit var recycler: RecyclerView
    private val logAdapter = LogAdapter()

    private var spamCount = 0
    private var malwareCount = 0
    private val logs = ArrayList<DetectLog>() // 최근 100개 유지

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val msg = intent.getStringExtra(EXTRA_MESSAGE) ?: "탐지됨"
            when (intent.action) {
                ACTION_INC_SPAM -> {
                    spamCount += 1
                    animateNumber(tvSpam, spamCount)
                    addLog(DetectType.SPAM, msg)
                }
                ACTION_INC_MALWARE -> {
                    malwareCount += 1
                    animateNumber(tvMalware, malwareCount)
                    addLog(DetectType.MALWARE, msg)
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security)

        // 상단 카드/리스트
        tvSpam = findViewById(R.id.tvSpam)
        tvMalware = findViewById(R.id.tvMalware)
        recycler = findViewById(R.id.recyclerLogs)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = logAdapter

        // 하단 탭 하이라이트 & 이동 (아이콘은 그대로 두고 텍스트만 색 변경)
        val tabSecurity = findViewById<View>(R.id.tab_security)
        val tabHome     = findViewById<View>(R.id.tab_home)
        val tabMypage   = findViewById<View>(R.id.tab_mypage)

        val tvSecurity = findViewById<TextView>(R.id.tvSecurity)
        val tvHome     = findViewById<TextView>(R.id.tvHome)
        val tvMypage   = findViewById<TextView>(R.id.tvMypage)

        // 현재 화면 = 보안 → "글자"만 보라색
        tvSecurity.setTextColor(PURPLE)
        tvHome.setTextColor(BLACK)
        tvMypage.setTextColor(BLACK)

        tabHome.setOnClickListener {
            startActivity(Intent(this, MainPageActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            // ✅ 메인 ↔ 보안 전환 애니메이션 통일 (fade)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        tabSecurity.setOnClickListener {
            // 현재 페이지이므로 동작 없음 (필요하면 스크롤 상단 이동 등)
        }
        tabMypage.setOnClickListener {
            startActivity(Intent(this, MypageActivity::class.java))
            // (선택) 마이페이지 이동도 동일한 fade 사용
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 브로드캐스트 수신 등록 (API 33+ 플래그 필수)
        val filter = IntentFilter().apply {
            addAction(ACTION_INC_SPAM)
            addAction(ACTION_INC_MALWARE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(receiver) }
    }

    private fun addLog(type: DetectType, message: String) {
        val time = timeFmt.format(Date())
        val newList = ArrayList<DetectLog>(logs.size + 1)
        newList.add(DetectLog(type, message, time))
        newList.addAll(logs)
        val trimmed = if (newList.size > 100) ArrayList(newList.subList(0, 100)) else newList
        logs.clear(); logs.addAll(trimmed)
        logAdapter.submitList(ArrayList(logs))
    }

    private fun animateNumber(target: TextView, to: Int) {
        val from = target.text.toString().toIntOrNull() ?: 0
        if (from == to) return
        ValueAnimator.ofInt(from, to).apply {
            duration = 350
            addUpdateListener { target.text = (it.animatedValue as Int).toString() }
            start()
        }
    }

    enum class DetectType { SPAM, MALWARE }
    data class DetectLog(val type: DetectType, val message: String, val time: String)

    private class LogAdapter :
        ListAdapter<DetectLog, LogVH>(object : DiffUtil.ItemCallback<DetectLog>() {
            override fun areItemsTheSame(oldItem: DetectLog, newItem: DetectLog) = oldItem === newItem
            override fun areContentsTheSame(oldItem: DetectLog, newItem: DetectLog) = oldItem == newItem
        }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false) as ViewGroup
            return LogVH(view)
        }
        override fun onBindViewHolder(holder: LogVH, position: Int) = holder.bind(getItem(position))
    }

    private class LogVH(private val root: ViewGroup) : RecyclerView.ViewHolder(root) {
        private val title = root.findViewById<TextView>(android.R.id.text1)
        private val subtitle = root.findViewById<TextView>(android.R.id.text2)
        fun bind(item: DetectLog) {
            title.text = if (item.type == DetectType.SPAM) "SPAM" else "MALWARE"
            title.setTextColor(0xFF000000.toInt())
            subtitle.text = "${item.time} · ${item.message}"
            subtitle.setTextColor(0x99000000.toInt())
        }
    }
}
