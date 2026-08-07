package com.garytunak.quicktimer

import android.content.Context

/**
 * Single source of truth for the timer's state. We store only the absolute
 * end-time (epoch millis), never a "seconds remaining" counter, so the
 * correct remaining time can always be recomputed from the current clock -
 * even after the process/service was killed and restarted, or after a
 * device reboot.
 */
object TimerPrefs {

    private const val PREFS_NAME = "quick_timer_prefs"
    private const val KEY_END_TIME = "end_time_millis"
    private const val KEY_RUNNING = "is_running"
    private const val KEY_RINGING = "is_ringing"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RUNNING, false)

    /** True from the moment a countdown hits zero until the alarm is dismissed. */
    fun isRinging(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RINGING, false)

    fun getEndTime(context: Context): Long =
        prefs(context).getLong(KEY_END_TIME, 0L)

    /** Starts the timer now, or - if one is already running - extends it. */
    fun addSeconds(context: Context, seconds: Int): Long {
        val now = System.currentTimeMillis()
        val currentEnd = getEndTime(context)
        val base = if (isRunning(context) && currentEnd > now) currentEnd else now
        val newEnd = base + seconds * 1000L
        prefs(context).edit()
            .putLong(KEY_END_TIME, newEnd)
            .putBoolean(KEY_RUNNING, true)
            .apply()
        return newEnd
    }

    /** Countdown reached zero: no longer running, but the alarm now rings until dismissed. */
    fun markFinished(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_RUNNING, false)
            .putLong(KEY_END_TIME, 0L)
            .putBoolean(KEY_RINGING, true)
            .apply()
    }

    /** Fully idle: no countdown running, no alarm ringing. */
    fun clear(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_RUNNING, false)
            .putLong(KEY_END_TIME, 0L)
            .putBoolean(KEY_RINGING, false)
            .apply()
    }

    /** Milliseconds left, clamped to >= 0. Zero whenever no timer is running. */
    fun remainingMillis(context: Context): Long {
        if (!isRunning(context)) return 0L
        val remaining = getEndTime(context) - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    fun formatRemaining(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000 // round up so the last tick still reads e.g. "0:01"
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%d:%02d", m, s)
        }
    }
}
