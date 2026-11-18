package org.tasks.memos

import org.tasks.data.TaskContainer
import org.tasks.data.dao.TaskDao

class MemosOrderWriter(private val taskDao: TaskDao) {

    suspend fun persistOrder(ordered: List<Long>) {
        if (ordered.isEmpty()) return
        taskDao.inTransaction {
            ordered.forEachIndexed { index, id ->
                taskDao.setOrder(id, index.toLong())
            }
        }
    }

    /**
     * Ids of the band the memo lands in, in their new order. [all] is the list as shown before
     * the drop, [insertAt] is the memo's index in it once the memo is taken out. A null
     * [priority] means the list has no priority bands, so every memo is one band.
     */
    fun orderAfterDrop(
        all: List<TaskContainer>,
        movedId: Long,
        insertAt: Int,
        priority: Int?,
    ): List<Long> {
        val rest = all.filter { it.id != movedId }
        val moved = all.firstOrNull { it.id == movedId } ?: return emptyList()
        val dropped = rest.toMutableList().apply { add(insertAt.coerceIn(0, rest.size), moved) }
        return dropped
            .filter { priority == null || it.id == movedId || it.priority == priority }
            .map { it.id }
    }
}