package com.garytunak.quicktimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * If the device reboots while a timer is running, the foreground service is
 * killed along with it, but the end-time is still saved in prefs. Resume
 * ticking (or clear stale state if the timer would already be finished).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        if (TimerPrefs.isRunning(context) && TimerPrefs.remainingMillis(context) > 0) {
            val serviceIntent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_RESUME
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            TimerPrefs.clear(context)
            TimerWidgetProvider.updateAllWidgets(context)
        }
    }
}
