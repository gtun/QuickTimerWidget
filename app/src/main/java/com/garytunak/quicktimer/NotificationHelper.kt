package com.garytunak.quicktimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    const val CHANNEL_RUNNING = "timer_running"

    // "_v2" forces a fresh channel on upgrade: channel sound/vibration settings are frozen
    // the moment a channel is first created, so bumping the id is the only way to change them
    // for people who already had the app installed under the old channel.
    const val CHANNEL_DONE = "timer_done_v2"

    const val NOTIF_ID_RUNNING = 1
    const val NOTIF_ID_DONE = 2

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val running = NotificationChannel(
            CHANNEL_RUNNING,
            context.getString(R.string.channel_running_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_running_desc)
            setSound(null, null)
            enableVibration(false)
        }

        val done = NotificationChannel(
            CHANNEL_DONE,
            context.getString(R.string.channel_done_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_done_desc)
            // TimerService drives a looping alarm sound + vibration itself so it can keep
            // going until dismissed; the channel's own one-shot sound/vibration would just
            // double up with that, so it stays silent.
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannel(running)
        manager.createNotificationChannel(done)
    }

    fun buildRunningNotification(context: Context, remainingMillis: Long): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.notif_running_title))
            .setContentText(TimerPrefs.formatRemaining(remainingMillis) + " " + context.getString(R.string.notif_running_suffix))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.notif_action_cancel), cancelIntent)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * The "time's up" notification. Stays ongoing (can't be swiped away) for as long as
     * TimerService is ringing - the only way out is the Dismiss action, which stops the
     * looping alarm sound/vibration in TimerService and cancels this notification.
     */
    fun buildRingingNotification(context: Context): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.notif_done_title))
            .setContentText(context.getString(R.string.notif_done_text))
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.notif_action_dismiss), dismissIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    fun cancelRingingNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_DONE)
    }
}
