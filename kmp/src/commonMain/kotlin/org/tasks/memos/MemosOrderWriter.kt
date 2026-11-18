package org.tasks.memos

import org.tasks.data.TaskContainer
import org.tasks.data.dao.CaldavDao.Companion.toAppleEpoch
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.Task

class MemosOrderWriter(private val taskDao: TaskDao) {

    suspend fun persistOrder(ordered: List<Long>) {
        if (ordered.isEmpty()) return
        taskDao.inTransaction {
            ordered.forEachIndexed { index, id ->
                taskDao.setOrder(id, index.toLong())
            }
        }
    }

    fun effectiveSortKey(task: Task): Long =
        task.order ?: task.creationDate.toAppleEpoch()

    fun reorderBand(
        band: List<TaskContainer>,
        movedId: Long,
        targetId: Long,
    ): List<Long>? {
        if (band.none { it.id == movedId } || band.none { it.id == targetId }) {
            return null
        }
        val fromIdx = band.indexOfFirst { it.id == movedId }
        val toIdx = band.indexOfFirst { it.id == targetId }
        if (fromIdx < 0 || toIdx < 0 || fromIdx == toIdx) {
            return null
        }
        val reordered = band.map { it.id }.toMutableList()
        val id = reordered.removeAt(fromIdx)
        reordered.add(toIdx, id)
        return reordered
    }
}