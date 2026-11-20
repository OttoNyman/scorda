package org.tasks.notifications

import android.app.Notification
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.InterruptionFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors.newSingleThreadExecutor
import javax.inject.Inject
import javax.inject.Singleton

/** One queue and one rate limit for every poster: a cancel and a later notify of the same id never swap. */
@Singleton
class ThrottledNotificationManager @Inject constructor(
        @ApplicationContext val context: Context
) {
    private val notificationManagerCompat = NotificationManagerCompat.from(context)
    private val executor = newSingleThreadExecutor()
    private val throttle = Throttle(NOTIFICATIONS_PER_SECOND, executor = executor, tag = "NOTIFY")

    @InterruptionFilter
    val currentInterruptionFilter: Int
        get() = notificationManagerCompat.currentInterruptionFilter

    fun cancel(id: Int) {
        executor.execute {
            notificationManagerCompat.cancel(id)
        }
    }

    @RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    fun notify(id: Int, notification: Notification) {
        throttle.run {
            notificationManagerCompat.notify(id, notification)
        }
    }

    fun pause(millis: Long) = throttle.pause(millis)

    companion object {
        private const val NOTIFICATIONS_PER_SECOND = 4
    }
}