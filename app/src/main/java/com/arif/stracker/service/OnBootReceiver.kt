package com.arif.stracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

internal class OnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action?.equals(Intent.ACTION_BOOT_COMPLETED, ignoreCase = true) == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(Intent(context, MotionService::class.java))
            } else {
                context.startService(Intent(context, MotionService::class.java))
            }
        }
    }
}
