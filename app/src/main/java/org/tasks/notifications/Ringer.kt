package org.tasks.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import org.tasks.R
import org.tasks.extensions.Context.canScheduleExactAlarms
import org.tasks.preferences.Preferences
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class Ringer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: Preferences,
) {
    private val idsLock = Any()
    private var ringingIds: Set<Long> = emptySet()
    private val playGate = Mutex()
    private val toneHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var episodeFinish: (() -> Unit)? = null
    @Volatile
    private var alarmCard: Long? = null

    fun soundState(): SoundState {
        val audio = context.getSystemService(AudioManager::class.java)
        return SoundState(
            inCall = audio.mode == AudioManager.MODE_IN_CALL ||
                audio.mode == AudioManager.MODE_IN_COMMUNICATION,
            ringerNormal = audio.ringerMode == AudioManager.RINGER_MODE_NORMAL,
            superBypassesSilent = preferences.getBoolean(R.string.p_super_bypass_silent, false),
            vibrateAllowed = audio.ringerMode != AudioManager.RINGER_MODE_SILENT,
        )
    }

    suspend fun playEpisode(level: AlertLevel, taskIds: List<Long>) {
        playGate.withLock { runCatching {
            val state = soundState()
            val next = route(level, state)
            if (next == Route.NONE) {
                if (vibrate(level, state)) buzz(repeating = false)
                return@runCatching
            }
            if (next != Route.ALARM && RingerService.isRinging()) {
                addRinging(taskIds)
                return@runCatching
            }
            replaceRinging(taskIds.toSet())
            if (vibrate(level, state) && next != Route.ALARM) {
                buzz(repeating = false)
            }
            when (next) {
                Route.NONE -> Unit
                Route.NOTIFICATION -> if (level == AlertLevel.PERSISTENT) {
                    pips(AudioManager.STREAM_NOTIFICATION, 1)
                } else {
                    play(
                        melody(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.USAGE_NOTIFICATION,
                    )
                }
                Route.CALL_TONE ->
                    pips(AudioManager.STREAM_VOICE_CALL, if (level == AlertLevel.SUPER) SUPER_PIPS else 1)
                Route.ALARM -> startAlarm(taskIds.first())
            }
        }.onFailure { Timber.w(it, "Reminder sound failed") } }
    }

    fun stop(ids: Collection<Long>) {
        val hit = synchronized(idsLock) {
            if (ringingIds.intersect(ids.toSet()).isEmpty()) {
                false
            } else {
                ringingIds = emptySet()
                true
            }
        }
        if (!hit) return
        RingerService.stopIfRunning(removeCard = alarmCard in ids)
        NotificationManagerCompat.from(context).cancel(RingAlarm.INSISTENT_NOTIFICATION_ID)
        episodeFinish?.invoke()
    }

    private fun replaceRinging(ids: Set<Long>) {
        synchronized(idsLock) { ringingIds = ids }
    }

    private fun addRinging(ids: Collection<Long>) {
        synchronized(idsLock) { ringingIds = ringingIds + ids }
    }

    /** One repeat chain per level, so each level keeps its own rhythm. */
    fun scheduleRepeats(tasks: List<Pair<Long, AlertLevel>>, index: Int) {
        tasks.groupBy({ it.second }, { it.first }).forEach { (level, ids) ->
            nextEpisodeDelay(level, index)?.let { RingAlarm.schedule(context, ids, index + 1, it) }
        }
    }

    fun melody(kind: Int): Uri {
        val custom = preferences.getStringValue(R.string.p_reminder_ringtone)
        if (!custom.isNullOrEmpty()) {
            return custom.toUri()
        }
        return RingtoneManager.getDefaultUri(kind)
    }

    private fun startAlarm(cardId: Long) {
        RingerService.prepareToStart()
        alarmCard = cardId
        val intent = Intent(context, RingerService::class.java)
            .putExtra(RingerService.EXTRA_URI, melody(RingtoneManager.TYPE_ALARM).toString())
            .putExtra(RingerService.EXTRA_CARD_ID, cardId.toInt())
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Timber.w(e, "Foreground ringer blocked, using an insistent notification")
            postInsistent()
        }
    }

    private fun postInsistent() {
        val notification = NotificationCompat.Builder(
            context,
            NotificationManager.NOTIFICATION_CHANNEL_INSISTENT,
        )
            .setSmallIcon(R.drawable.ic_check_white_24dp)
            .setContentTitle(context.getString(R.string.app_name))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(context).notify(RingAlarm.INSISTENT_NOTIFICATION_ID, notification)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            RingAlarm.INSISTENT_NOTIFICATION_ID,
            Intent(context, InsistentSilenceReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val triggerAt = currentTimeMillis() + SUPER_RING_MS
        if (context.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private suspend fun play(uri: Uri, usage: Int) {
        suspendCancellableCoroutine { continuation ->
            val player = MediaPlayer()
            val finished = AtomicBoolean(false)
            val finish: () -> Unit = {
                if (finished.compareAndSet(false, true)) {
                    episodeFinish = null
                    toneHandler.removeCallbacksAndMessages(player)
                    player.runCatching { stop() }
                    player.runCatching { release() }
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            episodeFinish = finish
            player.setOnCompletionListener { finish() }
            player.setOnErrorListener { _, _, _ ->
                finish()
                true
            }
            continuation.invokeOnCancellation { finish() }
            toneHandler.postAtTime({ finish() }, player, SystemClock.uptimeMillis() + MAX_TONE_MS)
            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(usage).build()
                )
                player.setDataSource(context, uri)
                player.prepare()
                player.start()
            } catch (e: Exception) {
                Timber.w(e, "Reminder sound failed")
                finish()
            }
        }
    }

    /** Each pair is two short 1200 Hz pips. */
    private suspend fun pips(stream: Int, pairs: Int) {
        val tone = ToneGenerator(stream, TONE_VOLUME)
        try {
            repeat(pairs) {
                tone.startTone(ToneGenerator.TONE_PROP_ACK)
                kotlinx.coroutines.delay(PIP_PAIR_MS)
            }
        } finally {
            tone.release()
        }
    }

    private fun buzz(repeating: Boolean) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (repeating) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), 0))
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(BUZZ_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private companion object {
        const val SUPER_PIPS = 3
        const val TONE_VOLUME = 80
        const val PIP_PAIR_MS = 700L
        const val BUZZ_MS = 200L
        const val MAX_TONE_MS = 5_000L
    }
}
