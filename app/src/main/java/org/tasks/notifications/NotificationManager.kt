package org.tasks.notifications

import org.tasks.constructor.flags
import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat.InterruptionFilter
import androidx.core.os.ConfigurationCompat
import com.todoroo.andlib.utility.AndroidUtilities.atLeastTiramisu
import com.todoroo.andlib.utility.AndroidUtilities.preUpsideDownCake
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.R
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.NotificationDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Notification
import org.tasks.filters.NotificationsFilter
import org.tasks.intents.TaskIntents
import org.tasks.jobs.WorkManager
import org.tasks.markdown.MarkdownProvider
import org.tasks.preferences.PermissionChecker
import org.tasks.preferences.Preferences
import org.tasks.receivers.CompleteTaskReceiver
import android.os.Build
import org.tasks.reminders.AlarmActivity
import org.tasks.reminders.NotificationActivity
import org.tasks.reminders.SnoozeActivity
import org.jetbrains.compose.resources.StringResource
import org.tasks.reminders.SnoozeOption
import org.tasks.reminders.snoozeOptions
import org.tasks.resources.getString
import org.tasks.themes.ColorProvider
import org.tasks.time.DateTime
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.rmd_NoA_done
import tasks.kmp.generated.resources.rmd_NoA_snooze
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: Preferences,
    private val notificationDao: NotificationDao,
    private val taskDao: TaskDao,
    private val locationDao: LocationDao,
    private val refreshBroadcaster: RefreshBroadcaster,
    private val notificationManager: ThrottledNotificationManager,
    private val markdownProvider: MarkdownProvider,
    private val permissionChecker: PermissionChecker,
    private val workManager: WorkManager,
    private val ringer: Ringer,
    private val featureGate: org.tasks.constructor.FeatureGate =
        org.tasks.constructor.FeatureGate(org.tasks.constructor.Preset.Full.flags),
    private val pinNotifier: PinNotifier? = null,
) : Notifier {
    @InterruptionFilter
    val currentInterruptionFilter: Int
        get() = notificationManager.currentInterruptionFilter

    private val colorProvider = ColorProvider(context)

    @Volatile
    private var cachedLocaleContext: Pair<Locale, Context>? = null

    private val localeContext: Context
        get() {
            if (atLeastTiramisu()) {
                return context
            }
            val locale = Locale.getDefault()
            val configuration = context.resources.configuration
            if (ConfigurationCompat.getLocales(configuration)[0] == locale) {
                return context
            }
            cachedLocaleContext?.takeIf { it.first == locale }?.let { return it.second }
            return context
                .createConfigurationContext(
                    Configuration(configuration).apply { setLocale(locale) }
                )
                .also { cachedLocaleContext = locale to it }
        }
    private val queue = NotificationLimiter(MAX_NOTIFICATIONS)

    @SuppressLint("CheckResult")
    override suspend fun cancel(id: Long, reason: CancelReason) {
        if (id == SUMMARY_NOTIFICATION_ID.toLong()) {
            cancel(notificationDao.getAll() + id, reason)
        } else {
            cancel(listOf(id), reason)
        }
    }

    @SuppressLint("CheckResult")
    override suspend fun cancel(ids: List<Long>, reason: CancelReason) {
        Timber.d("Cancelling notifications for $ids reason=$reason")
        coroutineScope {
            launch {
                for (id in ids) {
                    notificationManager.cancel(id.toInt())
                }
            }
        }
        queue.remove(ids)
        notificationDao.deleteAll(ids)
        if (reason != CancelReason.EDIT) {
            ringer.stop(ids)
        }
        coroutineScope {
            launch {
                notifyTasks(emptyList(), alert = false)
            }
        }
        // after the cancels above ran: a pinned task whose reminder just went gets its quiet card back.
        // Not on completion or deletion: the row is not saved yet, the card would come back for a done
        // task. TaskCompleter refreshes the pins once the completion is in.
        if (reason !in PIN_ENDING) {
            refreshPins()
        }
    }

    @SuppressLint("CheckResult")
    override suspend fun cancelAll(reason: CancelReason) =
        cancel(SUMMARY_NOTIFICATION_ID.toLong(), reason)

    /** Never fails the caller: a cancel or a completion must not stop on the shade's pinned cards. */
    override suspend fun refreshPins() {
        try {
            pinNotifier?.reconcile()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Pinned cards")
        }
    }

    override fun triggerNotifications() {
        workManager.triggerNotifications()
    }

    @SuppressLint("MissingPermission")
    suspend fun restoreMissing(taskIds: List<Long>) {
        if (taskIds.isEmpty() || !permissionChecker.canNotify()) return
        val active = context.getSystemService(android.app.NotificationManager::class.java)
            .activeNotifications
            // a pinned card shares the task's id but is not its reminder: the reminder is still missing
            .filter { it.notification.channelId != NOTIFICATION_CHANNEL_PINNED }
            .map { it.id.toLong() }
            .toSet()
        val missing = taskIds.filter { it !in active && it != TEST_NOTIFICATION_ID.toLong() }
        if (missing.isEmpty()) return
        val rows = notificationDao.getAllOrdered().filter { it.taskId in missing }
        if (rows.isEmpty()) return
        createNotifications(notifications = rows, alert = true, useGroupKey = false)
    }

    @SuppressLint("MissingPermission")
    suspend fun restoreNotifications(cancelExisting: Boolean) {
        if (!permissionChecker.canNotify()) {
            Timber.w("Notifications disabled")
            return
        }
        val notifications = notificationDao.getAllOrdered()
        if (cancelExisting) {
            for (notification in notifications) {
                notificationManager.cancel(notification.taskId.toInt())
            }
        }
        if (preferences.bundleNotifications() && notifications.size > 1) {
            updateSummary(emptyList())
            val posted = createNotifications(
                    notifications = notifications,
                    alert = false,
            )
            if (posted.size != notifications.size) {
                updateSummary(emptyList())
            }
        } else {
            createNotifications(
                    notifications = notifications,
                    alert = false,
                    useGroupKey = false,
                )
            cancelSummaryNotification()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun notifyTasks(
        newNotifications: List<Notification>,
        alert: Boolean,
    ): Collection<Long> {
        if (!permissionChecker.canNotify()) {
            Timber.w("Notifications disabled, holding ${newNotifications.size}")
            return emptyList()
        }
        val existingNotifications = notificationDao.getAllOrdered()
        notificationDao.insertAll(newNotifications)
        val totalCount = existingNotifications.size + newNotifications.size
        var summariseAfterCleanup = false
        val posted = when {
            totalCount == 0 -> {
                cancelSummaryNotification()
                emptyList()
            }
            preferences.bundleNotifications() -> {
                updateSummary(emptyList())
                if (existingNotifications.size == 1) {
                    createNotifications(
                            notifications = existingNotifications,
                            alert = false,
                    )
                }
                when {
                    newNotifications.size == 1 -> createNotifications(
                        notifications = newNotifications,
                        alert = alert,
                    )
                    newNotifications.size > 1 -> createNotifications(
                            notifications = newNotifications,
                            alert = false,
                            superMayAlert = alert,
                        ).also { summariseAfterCleanup = true }
                    else -> emptyList()
                }
            }
            else -> createNotifications(
                notifications = newNotifications,
                alert = alert,
                useGroupKey = false,
            )
        }
        val discarded = undeliveredRows(
            attempted = newNotifications.map { it.taskId },
            delivered = posted,
            existing = existingNotifications.mapTo(mutableSetOf()) { it.taskId },
        )
        if (discarded.isNotEmpty()) {
            Timber.w("Undelivered, dropping rows: $discarded")
            notificationDao.deleteAll(discarded)
        }
        if (summariseAfterCleanup && posted.isNotEmpty()) {
            updateSummary(newNotifications)
        } else if (discarded.isNotEmpty() && preferences.bundleNotifications()) {
            updateSummary(emptyList())
        }
        refreshBroadcaster.broadcastRefresh()
        return posted
    }

    @SuppressLint("MissingPermission")
    private suspend fun createNotifications(
        notifications: List<Notification>,
        alert: Boolean,
        useGroupKey: Boolean = true,
        superMayAlert: Boolean = false,
    ): List<Long> {
        if (permissionChecker.canNotify()) {
            preferences.warnNotificationsDisabled = true
        } else {
            Timber.w("Notifications disabled")
            return emptyList()
        }
        if (notifications.isEmpty()) {
            Timber.d("No notifications to post")
            return emptyList()
        }
        Timber.d("Posting notifications alert=$alert useGroupKey=$useGroupKey\n${notifications.joinToString("\n")}")
        val built = notifications.map { it to buildTaskNotification(it) }
        val alertIndex = when {
            alert -> built.indexOfFirst { (_, result) ->
                result is TaskNotification.Ready && result.level == AlertLevel.SUPER
            }.takeIf { it >= 0 }
                ?: built.indexOfFirst { (_, result) ->
                    result is TaskNotification.Ready && result.level != AlertLevel.SILENT
                }
            superMayAlert -> built.indexOfFirst { (_, result) ->
                result is TaskNotification.Ready && result.level == AlertLevel.SUPER
            }
            else -> -1
        }
        val posted = mutableListOf<Long>()
        built.forEachIndexed { index, (notification, result) ->
            when (result) {
                TaskNotification.Stale -> {
                    Timber.d("Cancelling notification for ${notification.taskId} reason=${CancelReason.STALE}")
                    notificationManager.cancel(notification.taskId.toInt())
                    notificationDao.delete(notification.taskId)
                }
                is TaskNotification.Ready -> {
                    val thisAlert = index == alertIndex
                    val builder = result.builder
                    if (!thisAlert) {
                        builder.setFullScreenIntent(null, false)
                    }
                    builder
                            .setGroup(if (useGroupKey) GROUP_KEY else notification.taskId.toString())
                            .setGroupAlertBehavior(
                                    if (thisAlert) NotificationCompat.GROUP_ALERT_CHILDREN else NotificationCompat.GROUP_ALERT_SUMMARY)
                    val notifyResult = notify(
                        notification.taskId,
                        builder,
                        thisAlert,
                        notification.type,
                    )
                    if (notifyResult.posted) {
                        val reminderTime = DateTime(notification.timestamp).endOfMinute().millis
                        taskDao.setLastNotified(notification.taskId, reminderTime)
                        posted.add(notification.taskId)
                    }
                    if (notifyResult.evicted.isNotEmpty()) {
                        posted.removeAll(notifyResult.evicted.toSet())
                    }
                }
            }
        }
        return posted
    }

    data class NotifyResult(val posted: Boolean, val evicted: List<Long> = emptyList())

    @SuppressLint("MissingPermission")
    suspend fun notify(
            notificationId: Long,
            builder: NotificationCompat.Builder,
            alert: Boolean,
            type: Int? = null,
    ): NotifyResult {
        if (!permissionChecker.canNotify()) {
            return NotifyResult(posted = false)
        }
        if (preUpsideDownCake()) {
            builder.setLocalOnly(!preferences.getBoolean(R.string.p_wearable_notifications, true))
        }
        if (!alert) {
            builder.setSilent(true)
        }
        val notification = builder.build()
        if (preferences.usePersistentReminders()) {
            notification.flags = notification.flags or
                    NotificationCompat.FLAG_NO_CLEAR or
                    NotificationCompat.FLAG_ONGOING_EVENT
        }
        val deleteIntent = Intent(context, NotificationClearedReceiver::class.java)
        deleteIntent.putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        type?.let { deleteIntent.putExtra(EXTRA_NOTIFICATION_TYPE, it) }
        notification.deleteIntent = PendingIntent.getBroadcast(
            context,
            notificationId.toInt(),
            deleteIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val evicted = queue.add(notificationId)
        if (evicted.size > 0) {
            cancel(evicted, CancelReason.EVICTED)
        }
        notificationManager.notify(notificationId.toInt(), notification)
        return NotifyResult(posted = true, evicted = evicted)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun updateSummary(newNotifications: List<Notification>) {
        val tasks = taskDao.activeNotifications()
        val taskCount = tasks.size
        if (taskCount == 0) {
            cancelSummaryNotification()
            return
        }
        Timber.d("Updating summary taskCount=$taskCount newNotifications=$newNotifications")
        val taskIds = tasks.map { it.id }
        val localized = localeContext
        val summaryTitle =
            localized.resources.getQuantityString(R.plurals.task_count, taskCount, taskCount)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(summaryTitle)
        val titles: MutableList<String?> = ArrayList()
        val ticker: MutableList<String?> = ArrayList()
        for (task in tasks) {
            val title = task.title
            style.addLine(title)
            titles.add(title)
        }
        for (notification in newNotifications) {
            tasks.find { it.id == notification.taskId }?.let { ticker.add(it.title) }
        }
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_DEFAULT)
                .setContentTitle(summaryTitle)
                .setContentText(
                        titles.joinToString(localized.getString(R.string.list_separator_with_space)))
                .setShowWhen(true)
                .setSmallIcon(R.drawable.ic_done_all_white_24dp)
                .setStyle(style)
                .setOnlyAlertOnce(true)
                .setContentIntent(
                        PendingIntent.getActivity(
                                context,
                                0,
                                TaskIntents.getTaskListIntent(context, NotificationsFilter.create()),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                )
                .setGroupSummary(true)
                .setGroup(GROUP_KEY)
                .setTicker(
                        ticker.joinToString(localized.getString(R.string.list_separator_with_space)))
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        notificationDao.latestTimestamp()?.let { builder.setWhen(it) }
        val snoozeIntent = SnoozeActivity.newIntent(context, taskIds)
        builder.addAction(
                R.drawable.ic_snooze_white_24dp,
                localized.getString(R.string.snooze_all),
                PendingIntent.getActivity(
                    context,
                    0,
                    snoozeIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                )
        )
        notify(SUMMARY_NOTIFICATION_ID.toLong(), builder, false)
    }

    data class ActionLabels(
        val complete: String,
        val snooze: String,
        val snoozeOptions: List<String>,
    )

    private suspend fun actionLabels(options: List<SnoozeOption>): ActionLabels {
        val wanted = buildList {
            add(Res.string.rmd_NoA_done)
            add(Res.string.rmd_NoA_snooze)
            options.forEach { add(it.label) }
        }
        val locale = Locale.getDefault()
        cachedActionLabels?.let { (key, labels) -> if (key == locale to wanted) return labels }
        val resolved = wanted.map { getString(it) }
        return ActionLabels(
            complete = resolved[0],
            snooze = resolved[1],
            snoozeOptions = resolved.drop(2),
        ).also { cachedActionLabels = (locale to wanted) to it }
    }

    @Volatile
    private var cachedActionLabels:
            Pair<Pair<Locale, List<StringResource>>, ActionLabels>? = null

    sealed interface TaskNotification {
        data class Ready(
            val builder: NotificationCompat.Builder,
            val level: AlertLevel? = null,
        ) : TaskNotification

        data object Stale : TaskNotification
    }

    suspend fun getTaskNotification(notification: Notification): NotificationCompat.Builder? =
        (buildTaskNotification(notification) as? TaskNotification.Ready)?.builder

    suspend fun buildTaskNotification(notification: Notification): TaskNotification {
        val id = notification.taskId
        val type = notification.type
        val `when` = notification.timestamp
        val task = taskDao.fetch(id)
        if (task == null) {
            Timber.e("Could not find %s", id)
            return TaskNotification.Stale
        }

        // you're done, or not yours - don't sound, do delete
        if (task.isCompleted || task.isDeleted) {
            return TaskNotification.Stale
        }

        val snoozeOptions = snoozeOptions(preferences.quickPickTimes)
        val (completeLabel, snoozeActionLabel, snoozeLabels) = actionLabels(snoozeOptions)

        // read properties
        val localized = localeContext
        val markdown = markdownProvider.markdown()
        val taskTitle = markdown.toMarkdown(task.title)?.toString()
        val features = featureGate.current
        val taskDescription = if (features.notes) markdown.toMarkdown(task.notes)?.toString() else null
        val level = if (type == Alarm.TYPE_RANDOM) {
            AlertLevel.ONCE
        } else {
            alertLevel(task, preferences.enabledLevels())
        }
        val channel = if (level == AlertLevel.SILENT) {
            NOTIFICATION_CHANNEL_SILENT
        } else {
            NOTIFICATION_CHANNEL_DEFAULT
        }
        val builder = NotificationCompat.Builder(context, channel)
                .setCategory(
                    if (level == AlertLevel.SUPER) NotificationCompat.CATEGORY_ALARM
                    else NotificationCompat.CATEGORY_REMINDER
                )
                .setContentTitle(taskTitle)
                .setColor(colorProvider.getPriorityColor(if (features.priority) task.priority else org.tasks.data.entity.Task.Priority.LOW))
                .setSmallIcon(R.drawable.ic_check_white_24dp)
                .setWhen(`when`)
                .setOnlyAlertOnce(false)
                .setShowWhen(true)
                .setTicker(taskTitle)
        val intent = NotificationActivity.newIntent(context, taskTitle.toString(), id, task.readOnly)
        builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    id.toInt(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
        )
        if (level == AlertLevel.SUPER && canUseFullScreenIntent()) {
            val fullScreen = PendingIntent.getActivity(
                context,
                id.toInt(),
                AlarmActivity.newIntent(
                    context,
                    title = taskTitle?.takeIf { it.isNotBlank() } ?: task.title.orEmpty(),
                    id = id,
                    readOnly = task.readOnly,
                ),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setFullScreenIntent(fullScreen, true)
        }
        when {
            type == Alarm.TYPE_GEO_ENTER || type == Alarm.TYPE_GEO_EXIT -> {
                val place = locationDao.getPlace(notification.location!!)
                if (place != null) {
                    builder.setContentText(
                        localized.getString(
                            if (type == Alarm.TYPE_GEO_ENTER) R.string.location_arrived else R.string.location_departed,
                            place.displayName
                        )
                    )
                }
            }
            taskDescription?.isNotBlank() == true -> {
                builder
                    .setContentText(taskDescription)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(taskDescription))
            }
        }
        val completeIntent = Intent(context, CompleteTaskReceiver::class.java)
        completeIntent.putExtra(CompleteTaskReceiver.TASK_ID, id)
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            completeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val completeAction = NotificationCompat.Action.Builder(
                R.drawable.ic_check_white_24dp,
                completeLabel,
                completePendingIntent)
                .build()
        val snoozeIntent = SnoozeActivity.newIntent(context, id)
        val snoozePendingIntent = PendingIntent.getActivity(
            context,
            id.toInt(),
            snoozeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val wearableExtender = NotificationCompat.WearableExtender()
        if (!task.readOnly) {
            wearableExtender.addAction(completeAction)
        }
        for ((snoozeOption, snoozeLabel) in snoozeOptions.zip(snoozeLabels)) {
            val timestamp = snoozeOption.timestamp
            val wearableIntent = SnoozeActivity.newIntent(context, id)
            wearableIntent.action = "snooze-$id-${snoozeOption.label.key}"
            wearableIntent.putExtra(SnoozeActivity.EXTRA_SNOOZE_TIME, timestamp)
            val wearablePendingIntent = PendingIntent.getActivity(
                context,
                id.toInt(),
                wearableIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            wearableExtender.addAction(
                    NotificationCompat.Action.Builder(
                            R.drawable.ic_snooze_white_24dp,
                            snoozeLabel,
                            wearablePendingIntent)
                            .build())
        }
        if (!task.readOnly) {
            builder.addAction(completeAction)
        }
        return TaskNotification.Ready(
                builder = builder
                        .addAction(
                                R.drawable.ic_snooze_white_24dp,
                                snoozeActionLabel,
                                snoozePendingIntent)
                        .extend(wearableExtender),
                level = level,
        )
    }

    private fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return runCatching {
            context.getSystemService(android.app.NotificationManager::class.java)
                .canUseFullScreenIntent()
        }.getOrDefault(false)
    }

    private fun cancelSummaryNotification() {
        Timber.d("Cancelling summary notification")
        notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_DEFAULT = "reminders"
        const val NOTIFICATION_CHANNEL_SILENT = "reminders_silent"
        const val NOTIFICATION_CHANNEL_INSISTENT = "reminders_insistent"
        const val NOTIFICATION_CHANNEL_TASKER = "notifications_tasker"
        const val NOTIFICATION_CHANNEL_MISCELLANEOUS = "notifications_miscellaneous"
        const val NOTIFICATION_CHANNEL_PINNED = "pinned"
        private val PIN_ENDING = setOf(
            CancelReason.COMPLETE,
            CancelReason.REMOTE_COMPLETION,
            CancelReason.REMOTE_DELETION,
        )
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"
        const val SUMMARY_NOTIFICATION_ID = 0
        private const val GROUP_KEY = "tasks"
    }
}
