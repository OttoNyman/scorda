package org.tasks.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.tasks.extensions.Context.canScheduleExactAlarms
import org.tasks.time.DateTimeUtils2.currentTimeMillis

object RingAlarm {
    const val EXTRA_TASK_IDS = "ring_tasks"
    const val EXTRA_TASK_ID = "ring_task"
    const val EXTRA_INDEX = "ring_index"
    const val INSISTENT_NOTIFICATION_ID = 0x5C0DC

    fun schedule(context: Context, taskIds: List<Long>, index: Int, delayMs: Long) {
        if (taskIds.isEmpty()) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = currentTimeMillis() + delayMs
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(taskIds),
            intent(context, taskIds, index),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (context.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun requestCode(taskIds: List<Long>): Int {
        var hash = 1
        taskIds.sorted().forEach { id -> hash = 31 * hash + id.hashCode() }
        return hash
    }

    private fun intent(context: Context, taskIds: List<Long>, index: Int) =
        Intent(context, RingReceiver::class.java).apply {
            putExtra(EXTRA_TASK_IDS, taskIds.toLongArray())
            putExtra(EXTRA_INDEX, index)
        }
}
