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
        remember(context, taskIds)
    }

    fun forget(context: Context, taskIds: List<Long>) {
        if (taskIds.isEmpty()) return
        editBatches(context) { it.remove(key(taskIds)) }
    }

    fun hasPending(context: Context, taskId: Long): Boolean {
        val batches = batches(context).toMutableSet()
        val stale = mutableListOf<String>()
        var pending = false
        for (batch in batches) {
            val ids = parse(batch)
            if (taskId !in ids) continue
            if (pendingExists(context, ids)) pending = true else stale.add(batch)
        }
        if (stale.isNotEmpty()) {
            editBatches(context) { it.removeAll(stale.toSet()) }
        }
        return pending
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

    private fun remember(context: Context, taskIds: List<Long>) {
        editBatches(context) { it.add(key(taskIds)) }
    }

    private fun key(taskIds: List<Long>) = taskIds.sorted().joinToString(",")

    private fun parse(batch: String) = batch.split(',').mapNotNull { it.toLongOrNull() }

    private fun pendingExists(context: Context, taskIds: List<Long>): Boolean =
        PendingIntent.getBroadcast(
            context,
            requestCode(taskIds),
            Intent(context, RingReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) != null

    private fun batches(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(BATCHES, emptySet())
            .orEmpty()

    private fun editBatches(context: Context, block: (MutableSet<String>) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val batches = prefs.getStringSet(BATCHES, emptySet()).orEmpty().toMutableSet()
        block(batches)
        prefs.edit().putStringSet(BATCHES, batches).apply()
    }

    private const val PREFS = "ring_alarms"
    private const val BATCHES = "batches"
}
