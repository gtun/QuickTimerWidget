package com.garytunak.quicktimer

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat

class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_ADD_TIME -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 0)
                val serviceIntent = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_ADD_TIME
                    putExtra(TimerService.EXTRA_SECONDS, seconds)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            ACTION_CANCEL -> {
                val serviceIntent = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_CANCEL
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }

    companion object {
        const val ACTION_ADD_TIME = "com.garytunak.quicktimer.widget.ADD_TIME"
        const val ACTION_CANCEL = "com.garytunak.quicktimer.widget.CANCEL"
        const val EXTRA_SECONDS = "extra_seconds"

        /** Pushes the current timer state to every placed instance of this widget. */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val remaining = TimerPrefs.remainingMillis(context)
            val running = TimerPrefs.isRunning(context) && remaining > 0
            val ringing = TimerPrefs.isRinging(context)

            val views = RemoteViews(context.packageName, R.layout.widget_timer)

            views.setTextViewText(
                R.id.timeText,
                when {
                    ringing -> context.getString(R.string.widget_done_label)
                    running -> TimerPrefs.formatRemaining(remaining)
                    else -> context.getString(R.string.widget_idle_label)
                }
            )
            // The same X button doubles as "cancel the running countdown" and "dismiss the
            // ringing alarm" - both map to TimerService.ACTION_CANCEL, which knows which one
            // it's currently looking at via TimerPrefs.
            views.setViewVisibility(R.id.btnCancel, if (running || ringing) View.VISIBLE else View.GONE)

            views.setOnClickPendingIntent(R.id.btn30, addPendingIntent(context, appWidgetId, 30))
            views.setOnClickPendingIntent(R.id.btn60, addPendingIntent(context, appWidgetId, 60))
            views.setOnClickPendingIntent(R.id.btn300, addPendingIntent(context, appWidgetId, 300))
            views.setOnClickPendingIntent(R.id.btn600, addPendingIntent(context, appWidgetId, 600))
            views.setOnClickPendingIntent(R.id.btnCancel, cancelPendingIntent(context, appWidgetId))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun addPendingIntent(context: Context, appWidgetId: Int, seconds: Int) =
            PendingIntentCompat.getBroadcast(
                context,
                0,
                Intent(context, TimerWidgetProvider::class.java).apply {
                    action = ACTION_ADD_TIME
                    putExtra(EXTRA_SECONDS, seconds)
                    // Unique data URI so each button's PendingIntent is distinct rather than
                    // colliding (extras alone don't factor into PendingIntent identity).
                    data = Uri.parse("quicktimer://widget/$appWidgetId/add/$seconds")
                },
                0,
                false
            )!!

        private fun cancelPendingIntent(context: Context, appWidgetId: Int) =
            PendingIntentCompat.getBroadcast(
                context,
                0,
                Intent(context, TimerWidgetProvider::class.java).apply {
                    action = ACTION_CANCEL
                    data = Uri.parse("quicktimer://widget/$appWidgetId/cancel")
                },
                0,
                false
            )!!
    }
}
