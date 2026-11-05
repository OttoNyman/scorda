package org.tasks.scheduling

import android.app.NotificationChannel
import android.media.AudioAttributes
import android.media.RingtoneManager
import org.tasks.notifications.schedulePlan
import org.tasks.notifications.AndroidReminderHealth
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.todoroo.andlib.utility.AndroidUtilities.preS
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.tasks.R
import org.tasks.analytics.Firebase
import org.tasks.injection.BaseWorker
import org.tasks.jobs.WorkManager
import org.tasks.notifications.NotificationManager
import org.tasks.resources.getString
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.reminder_channel
import tasks.kmp.generated.resources.reminder_channel_insistent
import tasks.kmp.generated.resources.reminder_channel_silent

@HiltWorker
class NotificationSchedulerWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    firebase: Firebase,
    private val notificationManager: NotificationManager,
    private val workManager: WorkManager,
) : BaseWorker(context, workerParams, firebase) {

    override suspend fun run(): Result {
        createNotificationChannels()
        val cancelExistingNotifications = inputData.getBoolean(EXTRA_CANCEL_EXISTING_NOTIFICATIONS, false)
        notificationManager.restoreNotifications(cancelExistingNotifications)
        workManager.triggerNotifications()
        val facts = AndroidReminderHealth(context).facts()
        workManager.updateNotificationSafety(schedulePlan(facts).safetyPoll)
        return Result.success()
    }

    private suspend fun createNotificationChannels() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.deleteNotificationChannel("notifications")
        val reminders = createNotificationChannel(
            NotificationManager.NOTIFICATION_CHANNEL_DEFAULT,
            getString(Res.string.reminder_channel),
            true,
        )
        reminders.setSound(null, null)
        reminders.enableVibration(false)
        notificationManager.createNotificationChannel(reminders)
        val silent = createNotificationChannel(
            NotificationManager.NOTIFICATION_CHANNEL_SILENT,
            getString(Res.string.reminder_channel_silent),
            false,
        )
        silent.setSound(null, null)
        silent.enableVibration(false)
        notificationManager.createNotificationChannel(silent)
        notificationManager.createNotificationChannel(
            insistentChannel(getString(Res.string.reminder_channel_insistent))
        )
        notificationManager.createNotificationChannel(
                createNotificationChannel(NotificationManager.NOTIFICATION_CHANNEL_TASKER, R.string.tasker_locale, true))
        notificationManager.createNotificationChannel(
                createNotificationChannel(
                        NotificationManager.NOTIFICATION_CHANNEL_TIMERS, R.string.TEA_timer_controls, true))
        if (preS()) {
            notificationManager.createNotificationChannel(
                createNotificationChannel(
                    NotificationManager.NOTIFICATION_CHANNEL_MISCELLANEOUS,
                    R.string.miscellaneous,
                    false
                )
            )
        }
    }

    private fun insistentChannel(name: String): NotificationChannel {
        val channel = NotificationChannel(
            NotificationManager.NOTIFICATION_CHANNEL_INSISTENT,
            name,
            android.app.NotificationManager.IMPORTANCE_HIGH,
        )
        val alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        channel.setSound(
            alarm,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
        channel.enableVibration(true)
        return channel
    }

    private fun createNotificationChannel(
            channelId: String, nameResId: Int, alert: Boolean): NotificationChannel =
        createNotificationChannel(channelId, context.getString(nameResId), alert)

    private fun createNotificationChannel(
            channelId: String, channelName: String, alert: Boolean): NotificationChannel {
        val importance = if (alert) android.app.NotificationManager.IMPORTANCE_HIGH else android.app.NotificationManager.IMPORTANCE_LOW
        val notificationChannel = NotificationChannel(channelId, channelName, importance)
        notificationChannel.enableLights(alert)
        notificationChannel.enableVibration(alert)
        notificationChannel.setShowBadge(alert)
        return notificationChannel
    }

    companion object {
        private const val EXTRA_CANCEL_EXISTING_NOTIFICATIONS = "extra_cancel_existing_notifications"
        private const val TAG_NOTIFICATION_SCHEDULER = "tag_notification_scheduler"

        fun enqueueWork(context: Context, cancelNotifications: Boolean = false) {
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                TAG_NOTIFICATION_SCHEDULER,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequest.Builder(NotificationSchedulerWork::class.java)
                    .setInputData(workDataOf(EXTRA_CANCEL_EXISTING_NOTIFICATIONS to cancelNotifications))
                    .build()
            )
        }
    }
}
