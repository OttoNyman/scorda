package org.tasks.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.data.dao.NotificationDao
import org.tasks.data.dao.TaskDao
import org.tasks.injection.ApplicationScope
import org.tasks.preferences.Preferences
import org.tasks.resources.getString
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.reminder_test_passed
import javax.inject.Inject

@AndroidEntryPoint
class ReminderTestReceiver : BroadcastReceiver() {
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var ringer: Ringer
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        scope.launch {
            try {
                val level = alertLevel(preferences.defaultRingMode())
                val channel = if (level == AlertLevel.SILENT) {
                    NotificationManager.NOTIFICATION_CHANNEL_SILENT
                } else {
                    NotificationManager.NOTIFICATION_CHANNEL_DEFAULT
                }
                val notification = NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_check_white_24dp)
                    .setContentTitle(getString(Res.string.reminder_test_passed))
                    .setAutoCancel(true)
                    .build()
                notification.deleteIntent = PendingIntent.getBroadcast(
                    context,
                    TEST_NOTIFICATION_ID,
                    Intent(context, NotificationClearedReceiver::class.java)
                        .putExtra(NotificationManager.EXTRA_NOTIFICATION_ID, TEST_NOTIFICATION_ID.toLong()),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
                val id = TEST_NOTIFICATION_ID.toLong()
                ringer.playEpisode(level, listOf(id))
                val delay = nextEpisodeDelay(level, 0) ?: return@launch
                ringer.scheduleNext(listOf(id), 1, delay)
            } finally {
                pending.finish()
            }
        }
    }
}

@AndroidEntryPoint
class RingReceiver : BroadcastReceiver() {
    @Inject lateinit var notificationDao: NotificationDao
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var ringer: Ringer
    @Inject lateinit var preferences: Preferences
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val ids = taskIds(intent)
        val index = intent.getIntExtra(RingAlarm.EXTRA_INDEX, 0)
        if (ids.isEmpty()) return
        val pending = goAsync()
        scope.launch {
            try {
                val live = ids.filter { stillDue(context, it) }
                if (live.isEmpty()) return@launch
                val testOnly = live.all { it == TEST_NOTIFICATION_ID.toLong() }
                if (!testOnly && preferences.isCurrentlyQuietHours()) return@launch
                val sounding = live
                    .map { it to levelOf(it) }
                    .filter { it.second != AlertLevel.SILENT }
                if (sounding.isEmpty()) return@launch
                val levels = sounding.map { it.second }
                ringer.playEpisode(maxAlertLevel(levels), sounding.map { it.first })
                val delay = nextBatchDelay(levels, index) ?: return@launch
                val repeating = sounding
                    .filter { nextEpisodeDelay(it.second, index) != null }
                    .map { it.first }
                if (repeating.isNotEmpty()) {
                    ringer.scheduleNext(repeating, index + 1, delay)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun taskIds(intent: Intent): List<Long> {
        intent.getLongArrayExtra(RingAlarm.EXTRA_TASK_IDS)?.let { return it.toList() }
        val taskId = intent.getLongExtra(RingAlarm.EXTRA_TASK_ID, 0L)
        return if (taskId > 0L || taskId == TEST_NOTIFICATION_ID.toLong()) listOf(taskId) else emptyList()
    }

    private suspend fun levelOf(taskId: Long): AlertLevel {
        if (taskId == TEST_NOTIFICATION_ID.toLong()) {
            return alertLevel(preferences.defaultRingMode())
        }
        val task = taskDao.fetch(taskId) ?: return AlertLevel.ONCE
        return alertLevel(task.ringFlags)
    }

    private suspend fun stillDue(context: Context, taskId: Long): Boolean {
        if (taskId == TEST_NOTIFICATION_ID.toLong()) return testNotificationPosted(context)
        if (!notificationDao.hasNotification(taskId)) return false
        val task = taskDao.fetch(taskId) ?: return false
        return !task.isCompleted && !task.isDeleted
    }

    private fun testNotificationPosted(context: Context): Boolean =
        context.getSystemService(android.app.NotificationManager::class.java)
            .activeNotifications
            .any { it.id == TEST_NOTIFICATION_ID }
}

class InsistentSilenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationManagerCompat.from(context).cancel(RingAlarm.INSISTENT_NOTIFICATION_ID)
    }
}
