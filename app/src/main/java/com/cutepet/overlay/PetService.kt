package com.cutepet.overlay

import android.animation.ValueAnimator
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt
import kotlin.random.Random

class PetService : Service() {

    companion object {
        const val CHANNEL_ID = "cute_pet_channel"
        const val NOTIF_ID = 101
        const val ACTION_TOGGLE = "com.cutepet.overlay.TOGGLE"
        const val ACTION_STOP = "com.cutepet.overlay.STOP"
        const val ACTION_SNOOZE = "com.cutepet.overlay.SNOOZE"

        @Volatile
        var running = false
            private set

        fun start(ctx: Context) {
            val i = Intent(ctx, PetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, PetService::class.java)) }
        fun isRunning(@Suppress("UNUSED_PARAMETER") ctx: Context): Boolean {
            if (running) return true
            // Fallback for edge cases (e.g. process restart): query activity manager.
            return try {
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                @Suppress("DEPRECATION")
                am.getRunningServices(Int.MAX_VALUE).any { it.service.className == PetService::class.java.name }
            } catch (_: Exception) { false }
        }
    }

    private var wm: WindowManager? = null
    private var root: FrameLayout? = null
    private var pet: PetView? = null
    private var bubble: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    private val handler = Handler(Looper.getMainLooper())
    private var wanderTask: Runnable? = null
    private var bubbleTask: Runnable? = null
    private var restTask: Runnable? = null
    private var hideBubbleTask: Runnable? = null

    private var screenW = 1080
    private var screenH = 1920
    private var petPx = 150
    private var moveAnimator: ValueAnimator? = null
    private var isSleepingForRest = false

    private val cuteLines = listOf(
        "hi hi! (≧◡≦) ♡", "you're doing great!", "drink some water? 💧",
        "pfft… I did a spin!", "tap me! tap me!", "I'm watching u work~",
        "stretchy time soon~", "you + me = best team!", "hehe that tickles!",
        "don't forget to blink! 👀", "I napped 3 secs. refreshed!", "uwu 💕",
        "let's finish this together!", "snack break? 🍪", "I'm your tiny guard!",
        "boop! ♡", "wag wag~", "stay cozy~"
    )
    private val tapLines = listOf(
        "ehehe! ♡", "boop back!", "hii!! (≧◡≦)", "that tickles~!",
        "again! again!", "💕💕💕", "purr purr~", "hehe!"
    )
    private val restLines = listOf(
        "rest time~ close eyes 20s? 🌙",
        "you've worked hard! stretch! 🧘",
        "look 20ft away for 20s 👀",
        "water + stretch + breathe 💧",
        "I'm sleepy too… rest with me? zzz"
    )

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        running = true
        createChannel()
        startForeground(NOTIF_ID, buildNotif("I'm on your screen! Tap me ♡"))
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        setupOverlay()
        startLoops()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                applicationContext.petEnabled = false
                cleanup(); stopSelf(); return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                // If service isn't set up yet (e.g. started only for snooze), ignore.
                if (root == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                snoozeRest(); return START_STICKY
            }
            ACTION_TOGGLE -> { cleanup(); stopSelf(); applicationContext.petEnabled = false; return START_NOT_STICKY }
        }
        // prefs changed while running?
        applyPrefsToViews()
        return START_STICKY
    }

    // ---------- overlay ----------
    private fun setupOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        val density = dm.density
        petPx = (150 * density * applicationContext.petSize).roundToInt()

        root = FrameLayout(this)
        pet = PetView(this).apply { petType = applicationContext.petType }
        bubble = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#5B4A5A"))
            gravity = Gravity.CENTER
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFFFFF"))
                cornerRadius = 34f * density
                setStroke((2 * density).toInt(), Color.parseColor("#FFC7D6"))
            }
            elevation = 12f * density
            visibility = View.GONE
            maxWidth = (220 * density).toInt()
        }

        root!!.addView(bubble, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; topMargin = 0 })
        root!!.addView(pet, FrameLayout.LayoutParams(petPx, petPx).apply {
            gravity = Gravity.TOP or Gravity.START; topMargin = (90 * density).toInt()
        })

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - petPx - 40
            y = screenH / 2
        }

        // touch: drag + tap + long-press
        // NOTE: OnTouchListener MUST return true on DOWN (and for handled events),
        // otherwise MOVE/UP are never delivered and drag/tap breaks.
        var downX = 0; var downY = 0; var startX = 0; var startY = 0
        var moved = false
        var downTime = 0L
        val touchSlop = (12 * density).toInt()
        root!!.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX.toInt(); downY = ev.rawY.toInt()
                    startX = params!!.x; startY = params!!.y
                    moved = false; downTime = SystemClock.uptimeMillis()
                    moveAnimator?.cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX.toInt() - downX
                    val dy = ev.rawY.toInt() - downY
                    if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) moved = true
                    if (moved) {
                        params!!.x = (startX + dx).coerceIn(0, screenW - petPx)
                        params!!.y = (startY + dy).coerceIn(0, screenH - petPx - 120)
                        try { wm!!.updateViewLayout(root, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val pressMs = SystemClock.uptimeMillis() - downTime
                    if (!moved) {
                        if (pressMs > 600) {
                            // long-press (no move, long hold) -> open settings
                            val li = Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            startActivity(li)
                        } else {
                            onPetTapped()
                        }
                    } else if (pressMs > 600) {
                        // dropped after long drag: spin = fun
                        pet?.spin()
                    } else {
                        pet?.hop(30f)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    moved = false
                    true
                }
                else -> false
            }
        }

        try { wm!!.addView(root, params) } catch (e: Exception) { e.printStackTrace(); stopSelf() }
    }

    private fun applyPrefsToViews() {
        if (root == null) return
        try {
            pet?.petType = applicationContext.petType
            val density = resources.displayMetrics.density
            petPx = (150 * density * applicationContext.petSize).roundToInt()
            pet?.layoutParams?.width = petPx
            pet?.layoutParams?.height = petPx
            pet?.requestLayout()
            // Re-apply timers so slider changes take effect without full restart.
            // Safe: schedule*() removes old callbacks first.
            scheduleBubble()
            scheduleRest()
        } catch (_: Exception) {}
    }

    // ---------- behaviours ----------
    private fun startLoops() {
        // wander
        wanderTask = object : Runnable {
            override fun run() {
                if (applicationContext.wanderOn && !isSleepingForRest) wanderOnce()
                handler.postDelayed(this, (3500 + Random.nextInt(3500)).toLong())
            }
        }
        handler.postDelayed(wanderTask!!, 2500)

        // random bubble chatter
        scheduleBubble()

        // rest reminder
        scheduleRest()
    }

    private fun scheduleBubble() {
        bubbleTask?.let { handler.removeCallbacks(it) }
        bubbleTask = Runnable {
            if (!isSleepingForRest) {
                showBubble(cuteLines.random())
                // tiny hop when talking
                if (Random.nextBoolean()) pet?.hop(28f)
            }
            val sec = applicationContext.bubbleSec.coerceIn(15, 180)
            val jitter = Random.nextInt(-8, 9)
            handler.postDelayed(bubbleTask!!, ((sec + jitter).coerceAtLeast(12) * 1000).toLong())
        }
        val sec = applicationContext.bubbleSec.coerceIn(15, 180)
        handler.postDelayed(bubbleTask!!, (sec * 1000).toLong())
    }

    private fun scheduleRest() {
        restTask?.let { handler.removeCallbacks(it) }
        restTask = Runnable {
            triggerRest()
            val min = applicationContext.restMin.coerceIn(5, 180)
            handler.postDelayed(restTask!!, (min * 60 * 1000).toLong())
        }
        val min = applicationContext.restMin.coerceIn(5, 180)
        handler.postDelayed(restTask!!, (min * 60 * 1000).toLong())
    }

    private fun snoozeRest() {
        val rt = restTask ?: return
        handler.removeCallbacks(rt)
        handler.postDelayed(rt, (10 * 60 * 1000).toLong())
        showBubble("ok! remind again in 10 min~ ♡")
        updateNotif("Rest snoozed 10 min ♡")
    }

    private fun wanderOnce() {
        val p = params ?: return
        val fromX = p.x; val fromY = p.y
        val toX = Random.nextInt(0, (screenW - petPx).coerceAtLeast(1))
        val toY = Random.nextInt(screenH / 4, ((screenH - petPx - 200).coerceAtLeast(screenH / 4 + 1)))
        moveAnimator?.cancel()
        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600 + Random.nextInt(900).toLong()
            addUpdateListener {
                val f = it.animatedFraction
                p.x = (fromX + (toX - fromX) * f).roundToInt()
                p.y = (fromY + (toY - fromY) * f).roundToInt() +
                        (-kotlin.math.abs(kotlin.math.sin(f * Math.PI * 3)) * 26).toInt()
                try { wm?.updateViewLayout(root, p) } catch (_: Exception) {}
            }
        }
        moveAnimator!!.start()
    }

    private fun onPetTapped() {
        // random cute reaction
        when (Random.nextInt(5)) {
            0 -> { pet?.happyBurst(); showBubble(tapLines.random()) }
            1 -> { pet?.loveBurst(); showBubble(tapLines.random()) }
            2 -> { pet?.hop(56f); pet?.mood = PetView.Mood.EXCITED; showBubble(tapLines.random())
                   handler.postDelayed({ pet?.mood = PetView.Mood.IDLE }, 1500) }
            3 -> { pet?.spin(); showBubble("wheee! 🌀") }
            else -> { pet?.happyBurst(); showBubble(tapLines.random()) }
        }
    }

    fun showBubble(text: String, long: Boolean = false) {
        val b = bubble ?: return
        handler.post {
            b.text = text
            b.visibility = View.VISIBLE
            b.alpha = 0f; b.scaleX = 0.7f; b.scaleY = 0.7f
            b.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).start()
            hideBubbleTask?.let { handler.removeCallbacks(it) }
            hideBubbleTask = Runnable { hideBubble() }
            handler.postDelayed(hideBubbleTask!!, if (long) 8000 else 4200)
        }
    }

    private fun hideBubble() {
        val b = bubble ?: return
        handler.post {
            b.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(300)
                .withEndAction { b.visibility = View.GONE }.start()
        }
    }

    private fun triggerRest() {
        isSleepingForRest = true
        pet?.setSleepy(true)
        showBubble(restLines.random(), long = true)
        updateNotif("Rest time! Look away 20s 🌙")
        // rest notification (heads-up)
        val restNotif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Cute Pet: rest time! 🌙")
            .setContentText("Stretch, drink water, look far away 20s. Tap pet when done!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(102, restNotif)
        // wake up after 45s
        handler.postDelayed({
            isSleepingForRest = false
            pet?.setSleepy(false)
            pet?.happyBurst()
            showBubble("all rested? let's go! ♡")
            updateNotif("I'm on your screen! Tap me ♡")
        }, 45_000)
    }

    // ---------- notification ----------
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Cute Pet", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Keeps your cute pet running over other apps"
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String): Notification {
        val stopI = PendingIntent.getService(this, 1,
            Intent(this, PetService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snoozeI = PendingIntent.getService(this, 2,
            Intent(this, PetService::class.java).apply { action = ACTION_SNOOZE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openI = PendingIntent.getActivity(this, 3,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.btn_star_big_on)
            .setContentTitle("Cute Pet running ♡")
            .setContentText(text)
            .setContentIntent(openI)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Turn OFF", stopI)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze rest", snoozeI)
            .build()
    }

    private fun updateNotif(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotif(text))
    }

    private fun cleanup() {
        try {
            wanderTask?.let { handler.removeCallbacks(it) }
            bubbleTask?.let { handler.removeCallbacks(it) }
            restTask?.let { handler.removeCallbacks(it) }
            hideBubbleTask?.let { handler.removeCallbacks(it) }
            moveAnimator?.cancel()
            if (root != null) wm?.removeViewImmediate(root)
        } catch (_: Exception) {}
        root = null; pet = null; bubble = null; wm = null
    }

    override fun onDestroy() {
        running = false
        cleanup()
        super.onDestroy()
    }
}
