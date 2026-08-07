package com.garytunak.quicktimer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private var alarmPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ADD_TIME -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 0)
                if (seconds > 0) {
                    TimerPrefs.addSeconds(this, seconds)
                }
                beginOrContinue()
            }
            ACTION_CANCEL -> {
                stopTimer()
            }
            ACTION_RESUME -> {
                // Fired after boot / process restart if a timer was already running.
                when {
                    TimerPrefs.isRunning(this) && TimerPrefs.remainingMillis(this) > 0 -> beginOrContinue()
                    TimerPrefs.isRinging(this) -> startRinging()
                    else -> {
                        TimerPrefs.clear(this)
                        TimerWidgetProvider.updateAllWidgets(this)
                        stopSelf()
                    }
                }
            }
            else -> {
                // System restarted the service (START_STICKY) with a null intent.
                when {
                    TimerPrefs.isRunning(this) && TimerPrefs.remainingMillis(this) > 0 -> beginOrContinue()
                    TimerPrefs.isRinging(this) -> startRinging()
                    else -> stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun beginOrContinue() {
        val remaining = TimerPrefs.remainingMillis(this)
        startForeground(NotificationHelper.NOTIF_ID_RUNNING, NotificationHelper.buildRunningNotification(this, remaining))
        TimerWidgetProvider.updateAllWidgets(this)
        startTicking()
    }

    private fun startTicking() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                val remaining = TimerPrefs.remainingMillis(this@TimerService)
                if (!TimerPrefs.isRunning(this@TimerService) || remaining <= 0) {
                    finishTimer()
                    return
                }
                val notification = NotificationHelper.buildRunningNotification(this@TimerService, remaining)
                getSystemService(android.app.NotificationManager::class.java)
                    ?.notify(NotificationHelper.NOTIF_ID_RUNNING, notification)
                TimerWidgetProvider.updateAllWidgets(this@TimerService)
                handler.postDelayed(this, 1000L)
            }
        }
        tickRunnable = runnable
        handler.post(runnable)
    }

    private fun finishTimer() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
        TimerPrefs.markFinished(this)
        startRinging()
    }

    /** Starts (or resumes, after a process restart) the looping alarm until it's dismissed. */
    private fun startRinging() {
        startForeground(NotificationHelper.NOTIF_ID_DONE, NotificationHelper.buildRingingNotification(this))
        TimerWidgetProvider.updateAllWidgets(this)
        playAlarmSound()
        startVibrating()
    }

    private fun stopTimer() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
        val wasRinging = TimerPrefs.isRinging(this)
        TimerPrefs.clear(this)
        if (wasRinging) {
            stopAlarmSound()
            stopVibrating()
            NotificationHelper.cancelRingingNotification(this)
        }
        stopForegroundCompat()
        TimerWidgetProvider.updateAllWidgets(this)
        stopSelf()
    }

    private fun playAlarmSound() {
        stopAlarmSound()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        try {
            alarmPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@TimerService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Missing/unreadable ringtone, no audio focus, etc. - the notification + vibration
            // still carry the alert, so there's nothing further to fall back to.
            alarmPlayer = null
        }
    }

    private fun stopAlarmSound() {
        alarmPlayer?.apply {
            try {
                if (isPlaying) stop()
            } catch (e: IllegalStateException) {
                // Already stopped/released.
            }
            release()
        }
        alarmPlayer = null
    }

    private fun startVibrating() {
        val vibrator = vibrator() ?: return
        // Buzz, pause, repeat - starting over from index 0 - until cancelled.
        val pattern = longArrayOf(0, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopVibrating() {
        vibrator()?.cancel()
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
        stopAlarmSound()
        stopVibrating()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_ADD_TIME = "com.garytunak.quicktimer.service.ADD_TIME"
        const val ACTION_CANCEL = "com.garytunak.quicktimer.service.CANCEL"
        const val ACTION_RESUME = "com.garytunak.quicktimer.service.RESUME"
        const val EXTRA_SECONDS = "extra_seconds"
    }
}
