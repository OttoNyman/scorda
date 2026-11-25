package org.tasks.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.tasks.reminders.AlarmActivity
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

private suspend fun testLevel(preferences: Preferences): AlertLevel =
    maxAlertLevel(preferences.enabledLevels().toList())

@AndroidEntryPoint
class ReminderTestReceiver : BroadcastReceiver() {
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var ringer: Ringer
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        scope.launch {
            try {
                val level = testLevel(preferences)
                val channel = if (level == AlertLevel.SILENT) {
                    NotificationManager.NOTIFICATION_CHANNEL_SILENT
                } else {
                    NotificationManager.NOTIFICATION_CHANNEL_DEFAULT
                }
                val title = getString(Res.string.reminder_test_passed)
                val id = TEST_NOTIFICATION_ID.toLong()
                val builder = NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_check_white_24dp)
                    .setContentTitle(title)
                    .setCategory(
                        if (level == AlertLevel.SUPER) NotificationCompat.CATEGORY_ALARM
                        else NotificationCompat.CATEGORY_REMINDER
                    )
                    .setAutoCancel(true)
                if (level == AlertLevel.SUPER && alarmOpensOnLockScreen(
                        preferences.getBoolean(R.string.p_alarm_on_lock_screen, false),
                        canUseFullScreenIntent(context),
                    )
                ) {
                    builder.setFullScreenIntent(
                        PendingIntent.getActivity(
                            context,
                            TEST_NOTIFICATION_ID,
                            AlarmActivity.newIntent(context, title, id),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                        true,
                    )
                } else if (level == AlertLevel.SUPER) {
                    builder
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .setPublicVersion(
                            NotificationCompat.Builder(context, channel)
                                .setSmallIcon(R.drawable.ic_check_white_24dp)
                                .setContentTitle(title)
                                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                .build(),
                        )
                }
                val notification = builder.build()
                notification.deleteIntent = PendingIntent.getBroadcast(
                    context,
                    TEST_NOTIFICATION_ID,
                    Intent(context, NotificationClearedReceiver::class.java)
                        .putExtra(NotificationManager.EXTRA_NOTIFICATION_ID, TEST_NOTIFICATION_ID.toLong()),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
                ringer.playEpisode(level, listOf(id))
                ringer.scheduleRepeats(listOf(id to level), 0)
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
    @Inject lateinit var notificationManager: NotificationManager
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
                // Swipe may have cleared the shade card; bring it back for Done/Snooze.
                notificationManager.restoreMissing(live)
                val sounding = live
                    .map { it to levelOf(it) }
                    .filter { it.second != AlertLevel.SILENT }
                if (sounding.isEmpty()) return@launch
                ringer.playEpisode(maxAlertLevel(sounding.map { it.second }), sounding.map { it.first })
                ringer.scheduleRepeats(sounding, index)
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
            return testLevel(preferences)
        }
        val task = taskDao.fetch(taskId) ?: return AlertLevel.ONCE
        return alertLevel(task, preferences.enabledLevels()) ?: AlertLevel.ONCE
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

private fun canUseFullScreenIntent(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return true
    return runCatching {
        context.getSystemService(android.app.NotificationManager::class.java)
            .canUseFullScreenIntent()
    }.getOrDefault(false)
}

class InsistentSilenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationManagerCompat.from(context).cancel(RingAlarm.INSISTENT_NOTIFICATION_ID)
    }
}
