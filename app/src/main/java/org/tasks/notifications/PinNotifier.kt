package org.tasks.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tasks.R
import org.tasks.constructor.FeatureGate
import org.tasks.data.dao.NotificationDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.Task
import org.tasks.intents.TaskIntents
import org.tasks.pins.pinCards
import org.tasks.preferences.PermissionChecker
import org.tasks.receivers.CompleteTaskReceiver
import org.tasks.resources.getString
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.pin_channel
import tasks.kmp.generated.resources.rmd_NoA_done
import tasks.kmp.generated.resources.unpin
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one quiet card per pinned task or memo in the shade. Posts straight to the system,
 * outside the reminder queue, so pins never count toward or evict reminders.
 */
@Singleton
class PinNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val notificationDao: NotificationDao,
    private val notificationManager: ThrottledNotificationManager,
    private val permissionChecker: PermissionChecker,
    private val featureGate: FeatureGate,
) {
    /** Idempotent: shows the missing pinned cards and removes the ones no longer wanted. */
    @SuppressLint("MissingPermission")
    suspend fun reconcile() {
        if (!permissionChecker.canNotify()) return
        val pinned = taskDao.getPinned()
        val want = pinCards(pinned, notificationDao.getAll().toSet(), featureGate.current)
        // a pinned card stays while its task is pinned, even next to a pending reminder row
        val keepIds = pinCards(pinned, emptySet(), featureGate.current).map { it.id.toInt() }.toSet()
        val system = ensureChannel(want.isNotEmpty())
        system.activeNotifications
            .filter { it.notification.channelId == NotificationManager.NOTIFICATION_CHANNEL_PINNED }
            .filter { it.id !in keepIds }
            .forEach { notificationManager.cancel(it.id) }
        want.forEach { notificationManager.notify(it.id.toInt(), card(it)) }
        Timber.d("Pinned cards: ${want.map { it.id }}")
    }

    /** A swiped reminder of a pinned task: its quiet card comes back now, the next episode replaces it. */
    @SuppressLint("MissingPermission")
    suspend fun show(taskId: Long) {
        if (!permissionChecker.canNotify()) return
        val task = taskDao.fetch(taskId) ?: return
        if (!task.isPinned || pinCards(listOf(task), emptySet(), featureGate.current).isEmpty()) return
        ensureChannel(true)
        notificationManager.notify(task.id.toInt(), card(task))
    }

    private suspend fun ensureChannel(create: Boolean): android.app.NotificationManager {
        val system = context.getSystemService(android.app.NotificationManager::class.java)
        if (create) {
            // idempotent; here rather than at startup so the first pin never posts to a missing channel
            system.createNotificationChannel(
                NotificationChannel(
                    NotificationManager.NOTIFICATION_CHANNEL_PINNED,
                    getString(Res.string.pin_channel),
                    android.app.NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }
        return system
    }

    private suspend fun card(task: Task): android.app.Notification {
        val id = task.id.toInt()
        val builder = NotificationCompat.Builder(context, NotificationManager.NOTIFICATION_CHANNEL_PINNED)
            .setSmallIcon(R.drawable.ic_push_pin_24px)
            .setContentTitle(task.title)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setGroup(GROUP)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    id,
                    TaskIntents.getEditTaskIntent(context, null, task),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .setDeleteIntent(pinBroadcast(PinReceiver.ACTION_SWIPED, task.id))
        task.notes?.takeIf { it.isNotBlank() }?.let {
            builder.setContentText(it).setStyle(NotificationCompat.BigTextStyle().bigText(it))
        }
        if (!task.memo && !task.readOnly) {
            val complete = Intent(context, CompleteTaskReceiver::class.java)
                .putExtra(CompleteTaskReceiver.TASK_ID, task.id)
            builder.addAction(
                R.drawable.ic_check_white_24dp,
                getString(Res.string.rmd_NoA_done),
                PendingIntent.getBroadcast(
                    context,
                    id,
                    complete,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        builder.addAction(
            R.drawable.ic_push_pin_24px,
            getString(Res.string.unpin),
            pinBroadcast(PinReceiver.ACTION_UNPIN, task.id),
        )
        return builder.build()
    }

    private fun pinBroadcast(action: String, taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            Intent(context, PinReceiver::class.java)
                .setAction(action)
                .putExtra(PinReceiver.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private companion object {
        const val GROUP = "pinned"
    }
}
