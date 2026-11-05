package org.tasks.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import kotlinx.coroutines.runBlocking
import org.tasks.R
import org.tasks.resources.getString
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.reminder_quiet

class RingerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private val stopPlayback = Runnable { shutdown() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (suppressStart) {
            suppressStart = false
            startInForeground()
            shutdown()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        val uri = intent?.getStringExtra(EXTRA_URI)?.toUri()
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        startInForeground()
        play(uri)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running === this) running = null
        releasePlayer()
        super.onDestroy()
    }

    fun requestStop() {
        handler.post { shutdown() }
    }

    private fun startInForeground() {
        val quiet = runBlocking { getString(Res.string.reminder_quiet) }
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, RingerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, NotificationManager.NOTIFICATION_CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_check_white_24dp)
            .setContentTitle(getString(R.string.app_name))
            .setSilent(true)
            .addAction(NotificationCompat.Action.Builder(0, quiet, stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun play(uri: Uri) {
        handler.removeCallbacks(stopPlayback)
        releasePlayer()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val audioManager = getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        focus = request
        audioManager.requestAudioFocus(request)
        val created = MediaPlayer()
        player = created
        try {
            created.setAudioAttributes(attributes)
            created.setDataSource(this, uri)
            created.isLooping = true
            created.prepare()
            created.start()
        } catch (_: Exception) {
            created.release()
            player = null
        }
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), 0),
        )
        handler.postDelayed(stopPlayback, SUPER_RING_MS)
    }

    private fun shutdown() {
        handler.removeCallbacks(stopPlayback)
        releasePlayer()
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        stopSelf()
    }

    private fun releasePlayer() {
        player?.runCatching {
            stop()
            release()
        }
        player = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        getSystemService(Vibrator::class.java)?.cancel()
    }

    companion object {
        const val ACTION_STOP = "org.tasks.notifications.RINGER_STOP"
        const val EXTRA_URI = "ringer_uri"
        private const val NOTIFICATION_ID = 0x5C0DB

        @Volatile
        private var running: RingerService? = null

        @Volatile
        private var suppressStart = false

        fun prepareToStart() {
            suppressStart = false
        }

        fun isRinging(): Boolean = running != null && !suppressStart

        fun stopIfRunning() {
            suppressStart = true
            running?.requestStop()
        }
    }
}
