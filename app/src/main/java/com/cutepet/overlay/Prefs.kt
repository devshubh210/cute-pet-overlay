package com.cutepet.overlay

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "cute_pet_prefs"

    const val KEY_ENABLED = "enabled"
    const val KEY_PET_TYPE = "pet_type" // 0=cat, 1=mochi, 2=bunny, 3=panda
    const val KEY_SIZE = "pet_size" // float 0.7..1.5
    const val KEY_BUBBLE_SEC = "bubble_sec" // int seconds
    const val KEY_REST_MIN = "rest_min" // int minutes
    const val KEY_WANDER = "wander" // bool auto-move

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var Context.petEnabled: Boolean
        get() = prefs(this).getBoolean(KEY_ENABLED, false)
        set(v) = prefs(this).edit().putBoolean(KEY_ENABLED, v).apply()

    var Context.petType: Int
        get() = prefs(this).getInt(KEY_PET_TYPE, 0)
        set(v) = prefs(this).edit().putInt(KEY_PET_TYPE, v).apply()

    var Context.petSize: Float
        get() = prefs(this).getFloat(KEY_SIZE, 1.0f)
        set(v) = prefs(this).edit().putFloat(KEY_SIZE, v).apply()

    var Context.bubbleSec: Int
        get() = prefs(this).getInt(KEY_BUBBLE_SEC, 35)
        set(v) = prefs(this).edit().putInt(KEY_BUBBLE_SEC, v).apply()

    var Context.restMin: Int
        get() = prefs(this).getInt(KEY_REST_MIN, 50)
        set(v) = prefs(this).edit().putInt(KEY_REST_MIN, v).apply()

    var Context.wanderOn: Boolean
        get() = prefs(this).getBoolean(KEY_WANDER, true)
        set(v) = prefs(this).edit().putBoolean(KEY_WANDER, v).apply()
}
