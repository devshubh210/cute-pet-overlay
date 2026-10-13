package com.cutepet.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val a = intent.action
        if (a == Intent.ACTION_BOOT_COMPLETED ||
            a == Intent.ACTION_MY_PACKAGE_REPLACED ||
            a == "android.intent.action.PACKAGE_REPLACED"
        ) {
            if (ctx.petEnabled) {
                try { PetService.start(ctx) } catch (_: Exception) {}
            }
        }
    }
}
