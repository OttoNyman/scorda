package org.tasks.notifications

import android.app.Notification
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
import org.tasks.R

/**
 * Rings a very persistent reminder for [SUPER_RING_MS].
 *
 * The service wears the task's own notification (same id, same Done and Snooze buttons), so the
 * user sees one card. When the ring times out the card is detached and stays; when the reminder is
 * handled the card is removed together with the service.
 */
class RingerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var foregroundId = 0
    private var wearsTaskCard = false
    private val timeout = Runnable { shutdown(removeCard = false) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (suppressStart) {
            suppressStart = false
            enterForeground(cardId = 0)
            shutdown(removeCard = true)
            return START_NOT_STICKY
        }
        val uri = intent?.getStringExtra(EXTRA_URI)?.toUri()
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        enterForeground(intent?.getIntExtra(EXTRA_CARD_ID, 0) ?: 0)
        play(uri)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running === this) running = null
        releasePlayer()
        super.onDestroy()
    }

    fun requestStop(removeCard: Boolean) {
        handler.post { shutdown(removeCard) }
    }

    private fun enterForeground(cardId: Int) {
        val card = cardId.takeIf { it != 0 }?.let { id ->
            getSystemService(android.app.NotificationManager::class.java)
                .activeNotifications
                .firstOrNull { it.id == id }
                ?.notification
        }
        val id = if (card != null) cardId else FALLBACK_ID
        if (foregroundId != 0 && foregroundId != id) {
            // A new id would make the system cancel the previous task's card.
            leaveForeground(remove = !wearsTaskCard)
        }
        wearsTaskCard = card != null
        foregroundId = id
        val notification = card ?: fallbackCard()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(id, notification)
        }
    }

    private fun fallbackCard(): Notification =
        NotificationCompat.Builder(this, NotificationManager.NOTIFICATION_CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_check_white_24dp)
            .setContentTitle(getString(R.string.app_name))
            .setSilent(true)
            .build()

    private fun play(uri: Uri) {
        handler.removeCallbacks(timeout)
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
        handler.postDelayed(timeout, SUPER_RING_MS)
    }

    private fun shutdown(removeCard: Boolean) {
        handler.removeCallbacks(timeout)
        releasePlayer()
        runCatching { leaveForeground(remove = removeCard || !wearsTaskCard) }
        foregroundId = 0
        stopSelf()
    }

    private fun leaveForeground(remove: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) {
            stopForeground(if (remove) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(remove)
        }
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
        const val EXTRA_URI = "ringer_uri"
        const val EXTRA_CARD_ID = "ringer_card"
        private const val FALLBACK_ID = 0x5C0DB

        @Volatile
        private var running: RingerService? = null

        @Volatile
        private var suppressStart = false

        fun prepareToStart() {
            suppressStart = false
        }

        fun isRinging(): Boolean = running != null && !suppressStart

        /** [removeCard]: the task's card goes too, because its reminder was handled. */
        fun stopIfRunning(removeCard: Boolean) {
            suppressStart = true
            running?.requestStop(removeCard)
        }
    }
}
