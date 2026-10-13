package com.cutepet.overlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var switchOn: Switch
    private lateinit var petPreview: PetView
    private lateinit var statusText: TextView
    private lateinit var seekSize: SeekBar
    private lateinit var seekBubble: SeekBar
    private lateinit var seekRest: SeekBar
    private lateinit var txtSize: TextView
    private lateinit var txtBubble: TextView
    private lateinit var txtRest: TextView
    private lateinit var switchWander: Switch
    private lateinit var petGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchOn = findViewById(R.id.switchOn)
        petPreview = findViewById(R.id.petPreview)
        statusText = findViewById(R.id.statusText)
        seekSize = findViewById(R.id.seekSize)
        seekBubble = findViewById(R.id.seekBubble)
        seekRest = findViewById(R.id.seekRest)
        txtSize = findViewById(R.id.txtSize)
        txtBubble = findViewById(R.id.txtBubble)
        txtRest = findViewById(R.id.txtRest)
        switchWander = findViewById(R.id.switchWander)
        petGroup = findViewById(R.id.petGroup)

        // load prefs
        switchOn.isChecked = petEnabled
        petPreview.petType = petType
        petPreview.hop(30f)
        switchWander.isChecked = wanderOn
        seekSize.progress = ((petSize - 0.7f) / 0.8f * 100).toInt().coerceIn(0, 100)
        seekBubble.progress = ((bubbleSec - 15) / 165f * 100).toInt().coerceIn(0, 100)
        seekRest.progress = ((restMin - 15) / 75f * 100).toInt().coerceIn(0, 100)
        updateLabels()
        when (petType) {
            0 -> petGroup.check(R.id.radioCat)
            1 -> petGroup.check(R.id.radioMochi)
            2 -> petGroup.check(R.id.radioBunny)
            3 -> petGroup.check(R.id.radioPanda)
        }

        switchOn.setOnCheckedChangeListener { _, on ->
            if (suppressSwitchCallback) return@setOnCheckedChangeListener
            if (on) enablePet() else disablePet()
        }

        findViewById<Button>(R.id.btnOverlayPerm).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            toast("Allow 'Display over other apps' then come back ♡")
        }
        findViewById<Button>(R.id.btnNotifPerm).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 11)
            } else toast("Notifications already allowed ♡")
        }
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            try {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } else toast("Already unrestricted ♡")
            } catch (_: Exception) { toast("Open battery settings manually") }
        }

        petGroup.setOnCheckedChangeListener { _, id ->
            petType = when (id) {
                R.id.radioMochi -> 1
                R.id.radioBunny -> 2
                R.id.radioPanda -> 3
                else -> 0
            }
            petPreview.petType = petType
            petPreview.happyBurst()
            restartIfRunning()
        }

        seekSize.setOnSeekBarChangeListener(simple(
            onChange = { petSize = 0.7f + it / 100f * 0.8f; updateLabels() },
            onStop = { restartIfRunning() }
        ))
        seekBubble.setOnSeekBarChangeListener(simple(
            onChange = { bubbleSec = 15 + (it / 100f * 165).toInt(); updateLabels() },
            onStop = { restartIfRunning() }
        ))
        seekRest.setOnSeekBarChangeListener(simple(
            onChange = { restMin = 15 + (it / 100f * 75).toInt(); updateLabels() },
            onStop = { restartIfRunning() }
        ))
        switchWander.setOnCheckedChangeListener { _, v -> wanderOn = v; restartIfRunning() }

        findViewById<Button>(R.id.btnTap).setOnClickListener { petPreview.happyBurst() }
        findViewById<Button>(R.id.btnSpin).setOnClickListener { petPreview.spin() }
        findViewById<Button>(R.id.btnSleep).setOnClickListener {
            val sleepy = petPreview.mood != PetView.Mood.SLEEPY
            petPreview.setSleepy(sleepy)
            toast(if (sleepy) "shhh… sleepy mode zzz" else "wake up! ♡")
        }
        findViewById<Button>(R.id.btnTestBubble).setOnClickListener {
            toast("Turn the pet ON to see bubbles over any app!")
        }

        refreshStatus()
    }

    private var suppressSwitchCallback = false

    override fun onResume() {
        super.onResume()
        refreshStatus()
        suppressSwitchCallback = true
        try {
            switchOn.isChecked = petEnabled && PetService.isRunning(this)
            if (switchOn.isChecked && !hasOverlay()) {
                switchOn.isChecked = false
                petEnabled = false
            }
        } finally {
            // post to avoid immediate listener race; listener checks flag
            switchOn.post { suppressSwitchCallback = false }
        }
    }

    private fun simple(
        onChange: (Int) -> Unit,
        onStop: (() -> Unit)? = null
    ) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { if (u) onChange(p) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) { onStop?.invoke() }
    }

    private fun updateLabels() {
        txtSize.text = "Pet size: %.2fx".format(petSize)
        txtBubble.text = "Bubble chatter: every ~${bubbleSec}s"
        txtRest.text = "Rest reminder: every ${restMin} min"
    }

    private fun hasOverlay() = Settings.canDrawOverlays(this)

    private fun enablePet() {
        if (!hasOverlay()) {
            toast("First allow 'Display over other apps' ♡")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            suppressSwitchCallback = true
            switchOn.isChecked = false
            switchOn.post { suppressSwitchCallback = false }
            return
        }
        petEnabled = true
        PetService.start(this)
        toast("Cute pet is ON! ♡ drag me, tap me!")
        refreshStatus()
    }

    private fun disablePet() {
        petEnabled = false
        PetService.stop(this)
        toast("Pet is resting… turn ON anytime ♡")
        refreshStatus()
    }

    private fun restartIfRunning() {
        // Soft refresh: re-deliver prefs via onStartCommand (no flicker, keeps position).
        // PetService.applyPrefsToViews() resizes + reschedules timers.
        if (PetService.isRunning(this) && petEnabled) {
            PetService.start(this)
        }
    }

    private fun refreshStatus() {
        val running = try { PetService.isRunning(this) } catch (_: Exception) { false }
        statusText.text = when {
            running -> "● running over all apps ♡ (drag to move, tap for hearts, long-press for settings)"
            petEnabled -> "○ enabled but not running — reopen app"
            !hasOverlay() -> "○ needs 'Display over other apps' permission"
            else -> "○ OFF — flip the switch to summon your pet!"
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
