package com.cutepet.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cute mascot drawn fully with Canvas. No image assets needed.
 * Supports 4 skins: 0=Cat (Mochi Cat), 1=Mochi Blob, 2=Bunny, 3=Panda
 * Moods: idle, happy, sleepy, excited, love
 */
class PetView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var petType: Int = 0
        set(v) { field = v; invalidate() }

    enum class Mood { IDLE, HAPPY, SLEEPY, EXCITED, LOVE }
    var mood: Mood = Mood.IDLE
        set(v) { field = v; invalidate() }

    // animation state
    private var timeMs: Long = 0
    private var blinkScale = 1f       // 1=open, 0.05=closed
    private var hopOffset = 0f
    private var tiltDeg = 0f
    private var squash = 1f
    private var lookX = 0f            // -1..1 eye look
    private var dragging = false

    // hearts / sparkle particles for happy
    data class Particle(var x: Float, var y: Float, var vy: Float, var life: Float, var kind: Int)
    private val particles = mutableListOf<Particle>()

    // paints
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val earPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val innerEarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B2B3A"); style = Paint.Style.FILL }
    private val eyeHiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB1C1"); style = Paint.Style.FILL; alpha = 180 }
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5B4A5A"); style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND }
    private val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val patchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val bodyColors = mapOf(
        0 to Color.parseColor("#FFFDF7"), // cat cream white
        1 to Color.parseColor("#FFE9F0"), // mochi pink
        2 to Color.parseColor("#F7F3FF"), // bunny lavender white
        3 to Color.parseColor("#FFFFFF")  // panda white
    )
    private val earColors = mapOf(
        0 to Color.parseColor("#FFFDF7"),
        1 to Color.parseColor("#FFE9F0"),
        2 to Color.parseColor("#F7F3FF"),
        3 to Color.parseColor("#3A3A44")
    )

    private val blinkHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var blinkRunnable: Runnable? = null
    private var breathRunnable: Runnable? = null

    init {
        scheduleBlink()
        // breathing loop
        val breath = object : Runnable {
            override fun run() {
                timeMs += 33
                // breathing squash
                if (!dragging && mood == Mood.IDLE) {
                    squash = 1f + 0.02f * sin(timeMs / 450.0).toFloat()
                    tiltDeg = 2.5f * sin(timeMs / 900.0).toFloat()
                }
                // particles
                val it = particles.iterator()
                while (it.hasNext()) {
                    val p = it.next()
                    p.y += p.vy
                    p.life -= 0.03f
                    if (p.life <= 0) it.remove()
                }
                invalidate()
                postDelayed(this, 33)
            }
        }
        breathRunnable = breath
        post(breath)
    }

    private fun scheduleBlink() {
        blinkRunnable?.let { blinkHandler.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                if (mood != Mood.SLEEPY) doBlink()
                blinkHandler.postDelayed(this, (2200 + Random.nextInt(2500)).toLong())
            }
        }
        blinkRunnable = r
        blinkHandler.postDelayed(r, 2000)
    }

    private fun doBlink() {
        val anim = ValueAnimator.ofFloat(1f, 0.06f, 1f).apply {
            duration = 220
            addUpdateListener { blinkScale = it.animatedValue as Float; invalidate() }
        }
        anim.start()
    }

    /** Cute hop */
    fun hop(height: Float = 46f) {
        val anim = ValueAnimator.ofFloat(0f, height, 0f).apply {
            duration = 480
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { hopOffset = -(it.animatedValue as Float); invalidate() }
        }
        anim.start()
    }

    fun happyBurst() {
        mood = Mood.HAPPY
        hop(64f)
        squashAnim()
        repeat(7) {
            particles.add(
                Particle(
                    x = 0.35f + Random.nextFloat() * 0.3f,
                    y = 0.25f + Random.nextFloat() * 0.2f,
                    vy = -0.012f - Random.nextFloat() * 0.012f,
                    life = 1f,
                    kind = if (it % 2 == 0) 0 else 1 // 0=heart 1=sparkle
                )
            )
        }
        postDelayed({ if (mood == Mood.HAPPY) mood = Mood.IDLE }, 2200)
    }

    fun loveBurst() {
        mood = Mood.LOVE
        hop(40f)
        repeat(9) {
            particles.add(
                Particle(
                    x = 0.3f + Random.nextFloat() * 0.4f,
                    y = 0.3f,
                    vy = -0.015f - Random.nextFloat() * 0.01f,
                    life = 1f, kind = 0
                )
            )
        }
        postDelayed({ if (mood == Mood.LOVE) mood = Mood.IDLE }, 2200)
    }

    fun setSleepy(sleepy: Boolean) {
        mood = if (sleepy) Mood.SLEEPY else Mood.IDLE
        if (sleepy) {
            repeat(3) {
                particles.add(Particle(x = 0.72f, y = 0.28f - it * 0.08f, vy = -0.006f, life = 2.5f, kind = 2))
            }
        } else particles.clear()
    }

    private fun squashAnim() {
        val anim = ValueAnimator.ofFloat(1f, 0.86f, 1.08f, 1f).apply {
            duration = 480
            addUpdateListener { squash = it.animatedValue as Float; invalidate() }
        }
        anim.start()
    }

    fun spin() {
        val anim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 600
            addUpdateListener { rotation = it.animatedValue as Float; invalidate() }
        }
        anim.start()
        postDelayed({ rotation = 0f }, 650)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultSize = (150 * resources.displayMetrics.density).toInt()
        val w = resolveSize(defaultSize, widthMeasureSpec)
        val h = resolveSize(defaultSize, heightMeasureSpec)
        // Keep it square: use the smaller of the two when both are EXACTLY specified
        // (service sets both to petPx; preview XML sets both to 150dp).
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        if (wMode == MeasureSpec.EXACTLY && hMode == MeasureSpec.EXACTLY) {
            val s = minOf(w, h)
            setMeasuredDimension(s, s)
        } else {
            setMeasuredDimension(w, h)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        blinkRunnable?.let { blinkHandler.removeCallbacks(it) }
        breathRunnable?.let { removeCallbacks(it) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val baseY = h * 0.56f + hopOffset * resources.displayMetrics.density / 3f

        canvas.save()
        canvas.rotate(tiltDeg, cx, baseY)

        val bodyCol = bodyColors[petType] ?: Color.WHITE
        bodyPaint.color = bodyCol
        footPaint.color = bodyCol
        earPaint.color = earColors[petType] ?: bodyCol

        val scaleX = 1f / squash.coerceIn(0.8f, 1.2f)
        val scaleY = squash.coerceIn(0.8f, 1.2f)

        // shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A000000") }
        val shadowW = w * 0.30f * (1f + hopOffset / 300f)
        canvas.drawOval(cx - shadowW, h * 0.86f, cx + shadowW, h * 0.90f, shadowPaint)

        canvas.save()
        canvas.scale(scaleX, scaleY, cx, baseY)

        val bw = w * 0.34f
        val bh = h * 0.30f

        // ---- ears ----
        when (petType) {
            0 -> { // cat triangle ears
                innerEarPaint.color = Color.parseColor("#FFC7D6")
                // left
                var p = Path().apply {
                    moveTo(cx - bw * 0.95f, baseY - bh * 0.55f)
                    lineTo(cx - bw * 0.75f, baseY - bh * 1.45f)
                    lineTo(cx - bw * 0.15f, baseY - bh * 0.80f)
                    close()
                }
                canvas.drawPath(p, earPaint)
                var q = Path().apply {
                    moveTo(cx - bw * 0.78f, baseY - bh * 0.72f)
                    lineTo(cx - bw * 0.68f, baseY - bh * 1.18f)
                    lineTo(cx - bw * 0.32f, baseY - bh * 0.82f)
                    close()
                }
                canvas.drawPath(q, innerEarPaint)
                // right
                p = Path().apply {
                    moveTo(cx + bw * 0.95f, baseY - bh * 0.55f)
                    lineTo(cx + bw * 0.75f, baseY - bh * 1.45f)
                    lineTo(cx + bw * 0.15f, baseY - bh * 0.80f)
                    close()
                }
                canvas.drawPath(p, earPaint)
                q = Path().apply {
                    moveTo(cx + bw * 0.78f, baseY - bh * 0.72f)
                    lineTo(cx + bw * 0.68f, baseY - bh * 1.18f)
                    lineTo(cx + bw * 0.32f, baseY - bh * 0.82f)
                    close()
                }
                canvas.drawPath(q, innerEarPaint)
            }
            2 -> { // bunny long ears
                innerEarPaint.color = Color.parseColor("#FFC7D6")
                canvas.drawRoundRect(cx - bw * 0.55f, baseY - bh * 2.3f, cx - bw * 0.05f, baseY - bh * 0.7f, 60f, 60f, earPaint)
                canvas.drawRoundRect(cx + bw * 0.05f, baseY - bh * 2.3f, cx + bw * 0.55f, baseY - bh * 0.7f, 60f, 60f, earPaint)
                canvas.drawRoundRect(cx - bw * 0.40f, baseY - bh * 2.05f, cx - bw * 0.18f, baseY - bh * 0.85f, 40f, 40f, innerEarPaint)
                canvas.drawRoundRect(cx + bw * 0.18f, baseY - bh * 2.05f, cx + bw * 0.40f, baseY - bh * 0.85f, 40f, 40f, innerEarPaint)
            }
            3 -> { // panda round ears
                canvas.drawCircle(cx - bw * 0.85f, baseY - bh * 0.85f, bw * 0.32f, earPaint)
                canvas.drawCircle(cx + bw * 0.85f, baseY - bh * 0.85f, bw * 0.32f, earPaint)
            }
            else -> { // mochi: tiny nubs
                canvas.drawCircle(cx - bw * 0.6f, baseY - bh * 0.95f, bw * 0.18f, earPaint)
                canvas.drawCircle(cx + bw * 0.6f, baseY - bh * 0.95f, bw * 0.18f, earPaint)
            }
        }

        // ---- feet ----
        val footW = bw * 0.34f
        val footH = bh * 0.30f
        val wobble = if (abs(hopOffset) > 2) 8f else 0f
        canvas.drawOval(cx - bw * 0.62f - footW / 2, baseY + bh * 0.72f - wobble, cx - bw * 0.62f + footW / 2, baseY + bh * 0.72f + footH, footPaint)
        canvas.drawOval(cx + bw * 0.62f - footW / 2, baseY + bh * 0.72f + wobble, cx + bw * 0.62f + footW / 2, baseY + bh * 0.72f + footH, footPaint)

        // ---- body ----
        canvas.drawOval(cx - bw, baseY - bh, cx + bw, baseY + bh, bodyPaint)

        // panda patches
        if (petType == 3) {
            patchPaint.color = Color.parseColor("#3A3A44")
            canvas.save()
            canvas.rotate(-18f, cx - bw * 0.42f, baseY - bh * 0.25f)
            canvas.drawOval(cx - bw * 0.68f, baseY - bh * 0.55f, cx - bw * 0.16f, baseY + bh * 0.05f, patchPaint)
            canvas.restore()
            canvas.save()
            canvas.rotate(18f, cx + bw * 0.42f, baseY - bh * 0.25f)
            canvas.drawOval(cx + bw * 0.16f, baseY - bh * 0.55f, cx + bw * 0.68f, baseY + bh * 0.05f, patchPaint)
            canvas.restore()
        }

        // ---- face ----
        val d = resources.displayMetrics.density
        val eyeDX = bw * 0.42f + lookX * 8f * d / 3f
        val eyeY = baseY - bh * 0.08f
        val eyeR = (11f * d).coerceAtLeast(18f)

        // blush
        canvas.drawOval(cx - bw * 0.78f, eyeY + 12f * d / 3f, cx - bw * 0.30f, eyeY + 26f * d / 3f, blushPaint)
        canvas.drawOval(cx + bw * 0.30f, eyeY + 12f * d / 3f, cx + bw * 0.78f, eyeY + 26f * d / 3f, blushPaint)

        if (mood == Mood.SLEEPY) {
            // closed happy eyes: curved lines
            mouthPaint.strokeWidth = 5f * d / 3f
            canvas.drawArc(cx - eyeDX - eyeR, eyeY - 10, cx - eyeDX + eyeR, eyeY + 22, 200f, 140f, false, mouthPaint)
            canvas.drawArc(cx + eyeDX - eyeR, eyeY - 10, cx + eyeDX + eyeR, eyeY + 22, 200f, 140f, false, mouthPaint)
            // tiny open mouth
            canvas.drawCircle(cx, eyeY + bh * 0.42f, 6f * d / 2.5f, eyePaint)
        } else {
            // open eyes with blink scale
            for (side in listOf(-1, 1)) {
                val ex = cx + side * eyeDX
                canvas.save()
                canvas.scale(1f, blinkScale.coerceAtLeast(0.06f), ex, eyeY)
                canvas.drawCircle(ex, eyeY, eyeR, eyePaint)
                canvas.drawCircle(ex + eyeR * 0.32f, eyeY - eyeR * 0.32f, eyeR * 0.38f, eyeHiPaint)
                canvas.drawCircle(ex - eyeR * 0.15f, eyeY + eyeR * 0.28f, eyeR * 0.14f, eyeHiPaint.apply { alpha = 220 })
                eyeHiPaint.alpha = 255
                canvas.restore()
            }
            // mouth
            mouthPaint.strokeWidth = 4.5f * d / 3f
            val my = eyeY + bh * 0.38f
            when (mood) {
                Mood.HAPPY, Mood.EXCITED -> {
                    // open happy mouth
                    val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8A5570") }
                    canvas.drawOval(cx - 13f * d / 3f, my - 4, cx + 13f * d / 3f, my + 20f * d / 3f, mp)
                    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF9FB6") }
                    canvas.drawOval(cx - 8f * d / 3f, my + 8f * d / 3f, cx + 8f * d / 3f, my + 18f * d / 3f, tp)
                }
                Mood.LOVE -> {
                    // w mouth
                    val p = Path().apply {
                        moveTo(cx - 18f * d / 3f, my)
                        quadTo(cx - 9f * d / 3f, my + 14f * d / 3f, cx, my)
                        quadTo(cx + 9f * d / 3f, my + 14f * d / 3f, cx + 18f * d / 3f, my)
                    }
                    canvas.drawPath(p, mouthPaint)
                }
                else -> {
                    val p = Path().apply {
                        moveTo(cx - 12f * d / 3f, my)
                        quadTo(cx, my + 10f * d / 3f, cx + 12f * d / 3f, my)
                    }
                    // tiny cat ω
                    canvas.drawPath(p, mouthPaint)
                    canvas.drawCircle(cx, my - 2, 2.2f * d / 2.5f, eyePaint)
                }
            }
        }

        canvas.restore() // un-squash

        // ---- particles (hearts/sparkles/Zzz) ----
        for (pt in particles) {
            val px = pt.x * w
            val py = pt.y * h + (1f - pt.life) * 30
            particlePaint.alpha = (pt.life.coerceIn(0f, 1f) * 255).toInt()
            when (pt.kind) {
                0 -> { // heart
                    particlePaint.color = Color.parseColor("#FF7FA5")
                    val s = 16f * d / 3f * pt.life.coerceAtLeast(0.4f)
                    // two circles + triangle
                    canvas.drawCircle(px - s * 0.35f, py - s * 0.2f, s * 0.45f, particlePaint)
                    canvas.drawCircle(px + s * 0.35f, py - s * 0.2f, s * 0.45f, particlePaint)
                    val hp = Path().apply {
                        moveTo(px - s * 0.72f, py)
                        lineTo(px + s * 0.72f, py)
                        lineTo(px, py + s * 0.85f)
                        close()
                    }
                    canvas.drawPath(hp, particlePaint)
                }
                1 -> { // sparkle
                    particlePaint.color = Color.parseColor("#FFD66B")
                    val s = 12f * d / 3f
                    val sp = Path().apply {
                        moveTo(px, py - s)
                        quadTo(px + s * 0.2f, py - s * 0.2f, px + s, py)
                        quadTo(px + s * 0.2f, py + s * 0.2f, px, py + s)
                        quadTo(px - s * 0.2f, py + s * 0.2f, px - s, py)
                        quadTo(px - s * 0.2f, py - s * 0.2f, px, py - s)
                        close()
                    }
                    canvas.drawPath(sp, particlePaint)
                }
                2 -> { // Zzz
                    particlePaint.color = Color.parseColor("#8E9BFF")
                    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = particlePaint.color; alpha = particlePaint.alpha
                        textSize = 34f * d / 3f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    canvas.drawText("z", px, py, tp)
                }
            }
        }
        particlePaint.alpha = 255

        canvas.restore()
    }

    // drag support is handled by service; this just gives press feedback
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                squash = 0.9f
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                squash = 1f
                invalidate()
            }
        }
        return false // let service handle drag/click via OnTouchListener on container
    }
}
