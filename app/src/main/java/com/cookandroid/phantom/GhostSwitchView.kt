package com.cookandroid.phantom

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.sin

class GhostSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /* ----- 상태 ----- */
    private var isChecked = false
    private var ghostTx = 0f                  // 유령의 X 슬라이드 (토글 이동)
    private var bobbleY = 0f                  // 유령 전체 bobble(4.3s)
    private var eyesSwayX = 0f                // 눈 sway-more(±4dp, 2s, 1s delay)
    private var blinkT = 0f                   // blink(4.25s, 0~1)

    private val d = resources.displayMetrics.density

    /* ----- 사이즈 ----- */
    private val switchW = 100f * d
    private val switchH = 25f * d
    private val head = 34f * d
    private val headR = head / 2f
    private val trackPad = 5f * d
    private val slideDur = 400L

    /* ----- 페인트 ----- */
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(241, 232, 212) // 살색
        style = Paint.Style.FILL
        setShadowLayer(12f * d, 0f, 5f * d, Color.argb(160, 255, 255, 255)) // 화이트 글로우
    }

    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(241, 232, 212)
        style = Paint.Style.FILL
    }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    /* ----- 애니메이터 ----- */
    private var slideAnimator: ObjectAnimator? = null
    private var bobbleAll: ValueAnimator? = null
    private var eyesSway: ValueAnimator? = null
    private var blink: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // 그림자 사용
        startAnimations()
    }

    private fun startAnimations() {
        // ghost 전체 bobble: 4.3s, Y로 15% (CSS: translateY(15%))
        bobbleAll = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4300
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val p = it.animatedValue as Float
                bobbleY = sin(p * Math.PI).toFloat() * (head * 0.15f)
                invalidate()
            }
            start()
        }

        // 눈 sway-more: 2s, 1s 딜레이, ±4dp
        eyesSway = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            startDelay = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            val amp = 4f * d
            addUpdateListener {
                val t = it.animatedFraction
                eyesSwayX = sin(t * Math.PI * 2).toFloat() * amp
                invalidate()
            }
            start()
        }

        // blink: 4.25s — 40%~50% 구간에서 눈이 얇아짐
        blink = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4250
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                blinkT = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /* ---------- 측정 ---------- */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredW = (switchW + head + 20f * d).toInt()
        val desiredH = (switchH + head + 20f * d).toInt()
        setMeasuredDimension(desiredW, desiredH)
    }

    /* ---------- 그리기 ---------- */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f + head / 4f

        // 1) 트랙
        trackPaint.color = if (isChecked) Color.parseColor("#EF4C45") else Color.GRAY
        val track = RectF(
            cx - switchW / 2f,
            cy - switchH / 2f,
            cx + switchW / 2f,
            cy + switchH / 2f
        )
        canvas.drawRoundRect(track, switchH / 2f, switchH / 2f, trackPaint)

        // 2) 유령 기준점
        val gX = cx - switchW / 2f + headR + trackPad + ghostTx
        val gY = cy - head / 3f + bobbleY

        // 3) 몸통(원)
        canvas.drawCircle(gX, gY, headR, ghostPaint)

        // 4) 밴드(:before) — 눈과 반대 방향으로 움직이게(부호 반전)
        // eyesSwayX: ±4dp, 밴드는 ±2dp라서 0.5배
        val bandSwayX = -0.5f * eyesSwayX
        val bandH = 12f * d
        val bandW = head * 1.50f
        val bandLeft = gX - (bandW / 2f) + bandSwayX
        val bandRight = gX + (bandW / 2f) + bandSwayX
        val bandTop = gY - bandH / 2f
        val bandRect = RectF(bandLeft, gY - bandH / 2f, bandRight, gY + bandH / 2f)
        canvas.drawRoundRect(bandRect, bandH / 2f, bandH / 2f, bandPaint)

        // 5) 눈(:after) — sway-more + blink
        val eyeOpenH = 8f * d
        val eyeClosedH = 2f * d
        val eyeH = if (blinkT in 0.40f..0.50f) eyeClosedH else eyeOpenH

        val eyeGap = head / 5.5f
        val eyeW = 3f * d
        val eyeTop = gY - eyeH / 2f

        // 왼쪽 눈
        val leftEyeCx = gX - eyeGap + eyesSwayX
        val leftEye = RectF(
            leftEyeCx - eyeW / 2f, eyeTop,
            leftEyeCx + eyeW / 2f, eyeTop + eyeH
        )
        canvas.drawRoundRect(leftEye, eyeW / 2f, eyeW / 2f, eyePaint)

        // 오른쪽 눈
        val rightEyeCx = gX + eyeGap + eyesSwayX
        val rightEye = RectF(
            rightEyeCx - eyeW / 2f, eyeTop,
            rightEyeCx + eyeW / 2f, eyeTop + eyeH
        )
        canvas.drawRoundRect(rightEye, eyeW / 2f, eyeW / 2f, eyePaint)
    }

    /* ---------- 토글 ---------- */
    fun toggle() {
        val maxTx = switchW - head - 2f * trackPad
        val target = if (!isChecked) maxTx else 0f
        animateSlide(target) { checked ->
            isChecked = checked
            onCheckedChange?.invoke(checked)
        }
    }

    fun setChecked(checked: Boolean, animate: Boolean = true) {
        if (isChecked == checked) return
        val maxTx = switchW - head - 2f * trackPad
        val target = if (checked) maxTx else 0f
        if (animate) {
            animateSlide(target) { isChecked = checked }
        } else {
            isChecked = checked
            ghostTx = target
            invalidate()
            onCheckedChange?.invoke(checked)
        }
    }

    fun isChecked(): Boolean = isChecked

    private fun animateSlide(target: Float, end: (Boolean) -> Unit) {
        slideAnimator?.cancel()
        slideAnimator = ObjectAnimator.ofFloat(this, "ghostTranslation", ghostTx, target).apply {
            duration = slideDur
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { invalidate() }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    end(target > 0f)
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            start()
        }
    }

    /* ObjectAnimator용 프로퍼티 */
    fun setGhostTranslation(v: Float) { ghostTx = v; invalidate() }
    fun getGhostTranslation(): Float = ghostTx

    private var onCheckedChange: ((Boolean) -> Unit)? = null
    fun setOnCheckedChangeListener(l: (Boolean) -> Unit) { onCheckedChange = l }

    /* ---------- 정리 ---------- */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        listOf(bobbleAll, eyesSway, blink).forEach { it?.cancel() }
        slideAnimator?.cancel()
    }
}
