package com.cookandroid.phantom

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.cookandroid.phantom.data.local.TokenDataStore
import kotlinx.coroutines.launch

// 검색 애니용
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.animation.AccelerateDecelerateInterpolator

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

    // 상단 큰 유령 & 둥둥 애니(깜빡임 방지: pulse 제거)
    private var ghost: ImageView? = null
    private var ghostFloatAnim: TranslateAnimation? = null

    // 토큰 저장소
    private lateinit var tokenStore: TokenDataStore

    // --- 말풍선: 동그라미(팬텀봇 아이콘) 안 미니 풍선 ---
    private var miniBubble: TextView? = null
    private val ui = Handler(Looper.getMainLooper())

    // 반복 설정(지속 깜빡임 아님: 주기적으로 한번 나타났다 사라짐)
    private val MINI_AUTO_DISMISS_MS = 2000L
    private val MINI_GAP_MS = 1200L
    private val miniMessages = arrayOf("Hi!", "Ready", "👋")
    private var miniMsgIdx = 0
    private var miniLoopRunning = false

    // (선택) 아이콘 옆 오버레이용(현재 미사용)
    private var bubbleView: TextView? = null

    // --- 스팸/피싱 유령 “검색 중” 애니 (깜빡임 유발 스캔/알파 스윕 제거) ---
    private var spamSearchSet: AnimatorSet? = null

    // --- 악성코드 카드: 빨간 느낌표 배지 ---
    private var badgeAlert: TextView? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_page)

        // TokenDataStore
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

        // 악성코드 카드 배지 (레이아웃에 추가한 @id/badgeAlert)
        badgeAlert = findViewById(R.id.badgeAlert)

        // 홈 탭 하이라이트
        highlightTab(Tab.HOME)

        // 탭 이동
        tabSecurity.setOnClickListener {
            if (currentTab() != Tab.SECURITY) {
                startActivity(Intent(this, SecurityActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
        tabHome.setOnClickListener { /* 이미 홈 */ }
        tabMypage.setOnClickListener {
            if (currentTab() != Tab.MYPAGE) {
                startActivity(Intent(this, MypageActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        // 상단 유령 둥둥(알파 변화 없음)
        ghostFloatAnim = TranslateAnimation(0f, 0f, 0f, 30f).apply {
            duration = 1000
            repeatCount = TranslateAnimation.INFINITE
            repeatMode = TranslateAnimation.REVERSE
        }

        // ====== 가운데 카드 버튼들 ======
        val shortcutEasy: View   = findViewById(R.id.shortcut_easy)
        val shortcutDelete: View = findViewById(R.id.shortcut_delete)
        val shortcutSpam: View   = findViewById(R.id.shortcut_spam)

        applyEnterAnimation(shortcutEasy,   R.anim.slide_up, 100)
        applyEnterAnimation(shortcutDelete, R.anim.slide_up, 200)
        applyEnterAnimation(shortcutSpam,   R.anim.slide_up, 300)

        val bounce = AnimationUtils.loadAnimation(this, R.anim.scale_bounce)
        shortcutEasy.setOnClickListener { v ->
            v.startAnimation(bounce.withEnd { checkLoginAndNavigate(AppScanActivity::class.java) })
        }
        shortcutDelete.setOnClickListener { v ->
            v.startAnimation(bounce.withEnd { checkLoginAndNavigate(SpamCheckActivity::class.java) })
        }
        shortcutSpam.setOnClickListener { v ->
            v.startAnimation(bounce.withEnd { checkLoginAndNavigate(BotActivity::class.java) })
        }

        // 정보 카드 이동
        findViewById<View>(R.id.btnOpenUsage).setOnClickListener {
            InfoHostActivity.start(this, InfoHostActivity.Page.USAGE)
        }
        findViewById<View>(R.id.btnOpenKnowledge).setOnClickListener {
            InfoHostActivity.start(this, InfoHostActivity.Page.SECURITY_KNOWLEDGE)
        }

        // ====== 미니 말풍선 초기 배치 ======
        findViewById<FrameLayout>(R.id.botIconContainer)?.post {
            attachMiniBubbleInBotIcon()
        }

        // ====== 스팸 유령 애니: 레이아웃 이후 세팅 & 즉시 시작 ======
        val spamIconContainer = findViewById<FrameLayout>(R.id.spamIconContainer)
        val ivSpamGhost = findViewById<ImageView>(R.id.ivSpamGhost)

        spamIconContainer?.viewTreeObserver?.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                spamIconContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                setupSpamSearchingAnim(spamIconContainer, ivSpamGhost)
                startSpamSearchingAnim() // 즉시 시작
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // 상단 유령: 둥둥만 (pulse 제거)
        ghost?.startAnimation(ghostFloatAnim)
        highlightTab(Tab.HOME)

        // 미니 말풍선 반복 시작
        if (!miniLoopRunning) {
            miniLoopRunning = true
            scheduleNextMiniBubble(0L)
        }

        // 스팸 유령 검색 애니 시작
        startSpamSearchingAnim()

        // 🔴 악성코드 배지 애니 시작 (톡-하고 뜨고 살짝 맥동 반복)
        badgeAlert?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.badge_pop_pulse))
    }

    override fun onPause() {
        super.onPause()
        ghost?.clearAnimation()
        bubbleView?.clearAnimation()
        miniBubble?.clearAnimation()
        badgeAlert?.clearAnimation()
        ui.removeCallbacksAndMessages(null)
        miniLoopRunning = false

        // 스팸 유령 검색 애니 정지
        stopSpamSearchingAnim()
    }

    // ------------------- 네비게이션 -------------------

    private fun checkLoginAndNavigate(destination: Class<*>) {
        lifecycleScope.launch {
            val token = tokenStore.getToken()
            if (token.isNullOrEmpty()) startActivity(Intent(this@MainPageActivity, LoginActivity::class.java))
            else startActivity(Intent(this@MainPageActivity, destination))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    // ------------------- 탭/스타일 -------------------

    private enum class Tab { HOME, SECURITY, MYPAGE }
    private fun currentTab(): Tab = Tab.HOME

    private fun highlightTab(current: Tab) {
        fun TextView.tint(c: Int) = setTextColor(c)
        when (current) {
            Tab.HOME     -> { tvHome.tint(PURPLE); tvSecurity.tint(BLACK); tvMypage.tint(BLACK) }
            Tab.SECURITY -> { tvSecurity.tint(PURPLE); tvHome.tint(BLACK); tvMypage.tint(BLACK) }
            Tab.MYPAGE   -> { tvMypage.tint(PURPLE); tvHome.tint(BLACK); tvSecurity.tint(BLACK) }
        }
    }

    // ------------------- 애니 유틸 -------------------

    private fun applyEnterAnimation(target: View, animRes: Int, startDelayMs: Long) {
        val anim = AnimationUtils.loadAnimation(this, animRes).apply { startOffset = startDelayMs }
        target.startAnimation(anim)
    }

    private inline fun Animation.withEnd(crossinline onEnd: () -> Unit): Animation {
        setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) { onEnd() }
        })
        return this
    }

    // ------------------- (A) 동그라미 안 미니 말풍선 -------------------

    private fun attachMiniBubbleInBotIcon() {
        val container = findViewById<FrameLayout>(R.id.botIconContainer) ?: return
        if (miniBubble != null) return

        miniBubble = TextView(this).apply {
            id = View.generateViewId()
            background = getDrawable(R.drawable.bg_bubble_body) // 둥근 배경
            text = ""
            setTextColor(Color.BLACK)
            textSize = 10f
            setPadding(dp(6), dp(2), dp(6), dp(2))
            maxLines = 1
            alpha = 0f
        }

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = dp(6)
        }
        container.addView(miniBubble, lp)

        // 살짝 오른쪽
        miniBubble?.translationX = dp(8).toFloat()
    }

    private fun showMiniBubbleInBotIcon(text: String, autoDismissMs: Long = MINI_AUTO_DISMISS_MS) {
        val tv = miniBubble ?: return
        tv.clearAnimation()
        tv.text = ""
        tv.alpha = 1f
        // 팝인(Scale 중심)만 — 알파는 천천히 올라가지만 깜빡임 없음
        tv.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bubble_pop_in))
        typewriter(tv, text, perCharDelay = 18L)
        if (autoDismissMs > 0) ui.postDelayed({ hideMiniBubbleInBotIcon() }, autoDismissMs)
    }

    private fun hideMiniBubbleInBotIcon() {
        val tv = miniBubble ?: return
        tv.clearAnimation()
        // 부드럽게 사라짐(짧은 페이드) — 지속 깜빡임 없음
        val fade = AnimationUtils.loadAnimation(this, android.R.anim.fade_out).apply { duration = 160 }
        tv.startAnimation(fade)
        ui.postDelayed({
            tv.alpha = 0f
            tv.text = ""
            tv.clearAnimation()
        }, 160)
    }

    private fun scheduleNextMiniBubble(delay: Long) {
        if (!miniLoopRunning) return
        ui.postDelayed({
            if (!miniLoopRunning) return@postDelayed
            attachMiniBubbleInBotIcon()
            val msg = miniMessages[miniMsgIdx]
            miniMsgIdx = (miniMsgIdx + 1) % miniMessages.size
            showMiniBubbleInBotIcon(msg, MINI_AUTO_DISMISS_MS)
            scheduleNextMiniBubble(MINI_AUTO_DISMISS_MS + MINI_GAP_MS)
        }, delay)
    }

    // ------------------- (B) 스팸/피싱 유령 “검색 중” 애니 (알파 변화 없음) -------------------

    private fun setupSpamSearchingAnim(container: FrameLayout?, ghost: ImageView?) {
        container ?: return; ghost ?: return

        // 중복 생성 방지 & 성능
        spamSearchSet?.cancel()
        ghost.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val dx = dp(6).toFloat()
        val dy = dp(5).toFloat()

        val moveX = ObjectAnimator.ofFloat(
            ghost, View.TRANSLATION_X,
            0f, +dx, 0f, -dx * 0.6f, 0f, +dx * 0.4f, 0f
        ).apply {
            duration = 2200
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ObjectAnimator.INFINITE
        }

        val moveY = ObjectAnimator.ofFloat(
            ghost, View.TRANSLATION_Y,
            0f, -dy, 0f, +dy * 0.5f, 0f, -dy * 0.3f, 0f
        ).apply {
            duration = 2200
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ObjectAnimator.INFINITE
        }

        val nod = ObjectAnimator.ofFloat(
            ghost, View.ROTATION,
            0f, -8f, 0f, 8f, 0f
        ).apply {
            duration = 1800
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ObjectAnimator.INFINITE
        }

        val zoom = ObjectAnimator.ofPropertyValuesHolder(
            ghost,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 0.96f, 1.04f, 1.0f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 0.96f, 1.04f, 1.0f)
        ).apply {
            duration = 2000
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ObjectAnimator.INFINITE
        }

        spamSearchSet = AnimatorSet().apply {
            playTogether(moveX, moveY, nod, zoom)
        }
    }

    private fun startSpamSearchingAnim() {
        spamSearchSet?.let { if (!it.isStarted) it.start() }
    }

    private fun stopSpamSearchingAnim() {
        spamSearchSet?.cancel()
    }

    // ------------------- (옵션) 아이콘 '옆' 오버레이 말풍선 예시 -------------------

    private fun attachBubbleNextToBotIcon() {
        val root = findViewById<ConstraintLayout>(R.id.main_page)
        val botIcon = findViewById<ImageView>(R.id.ivBotGhost) ?: return
        if (bubbleView == null) {
            bubbleView = TextView(this).apply {
                id = View.generateViewId()
                background = getDrawable(R.drawable.bg_bubble_body)
                text = ""
                setTextColor(Color.BLACK)
                textSize = 13f
                gravity = Gravity.START
                setPadding(dp(10), dp(6), dp(10), dp(6))
                alpha = 0f
            }
            root.addView(
                bubbleView,
                ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val rootLoc = IntArray(2); root.getLocationOnScreen(rootLoc)
        val iconLoc = IntArray(2); botIcon.getLocationOnScreen(iconLoc)
        val rect = Rect(iconLoc[0], iconLoc[1], iconLoc[0] + botIcon.width, iconLoc[1] + botIcon.height)

        val left = (rect.right - rootLoc[0]) + dp(6).toFloat()
        val centerY = ((rect.top + rect.bottom) / 2f) - rootLoc[1]
        val top = centerY - dp(18).toFloat()

        bubbleView!!.translationX = left
        bubbleView!!.translationY = top
    }

    private fun showBotIconBubble(text: String, autoDismissMs: Long = 0L) {
        val tv = bubbleView ?: return
        tv.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bubble_pop_in))
        tv.alpha = 1f
        typewriter(tv, text, perCharDelay = 24L)
        if (autoDismissMs > 0) ui.postDelayed({ hideBotIconBubble() }, autoDismissMs)
    }

    private fun hideBotIconBubble() {
        val tv = bubbleView ?: return
        tv.clearAnimation()
        val fade = AnimationUtils.loadAnimation(this, android.R.anim.fade_out).apply { duration = 180 }
        tv.startAnimation(fade)
        ui.postDelayed({
            tv.alpha = 0f
            tv.text = ""
            tv.clearAnimation()
        }, 180)
    }

    // ------------------- 공통 유틸 -------------------

    private fun typewriter(tv: TextView, fullText: String, perCharDelay: Long) {
        tv.text = ""
        var i = 0
        val task = object : Runnable {
            override fun run() {
                if (i <= fullText.length) {
                    tv.text = fullText.substring(0, i++)
                    ui.postDelayed(this, perCharDelay)
                }
            }
        }
        ui.post(task)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
