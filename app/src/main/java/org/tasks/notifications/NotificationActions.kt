package org.tasks.notifications

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

object NotificationActions {
    private const val WINDOW_MS = 10_000L
    private val completing = ConcurrentHashMap<Long, Long>()

    fun markCompleting(taskId: Long) {
        completing[taskId] = SystemClock.elapsedRealtime()
    }

    fun clearCompleting(taskId: Long) {
        completing.remove(taskId)
    }

    fun isCompleting(taskId: Long): Boolean {
        val started = completing[taskId] ?: return false
        if (SystemClock.elapsedRealtime() - started > WINDOW_MS) {
            completing.remove(taskId)
            return false
        }
        return true
    }
}
