package com.todoroo.astrid.adapter

import com.todoroo.astrid.core.SortHelper.SORT_IMPORTANCE
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.data.TaskContainer
import org.tasks.data.TaskMover
import org.tasks.data.TaskSaver
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.DirtyDao
import org.tasks.data.dao.GoogleTaskDao
import org.tasks.data.dao.TaskDao
import org.tasks.LocalBroadcastManager
import org.tasks.memos.MemosOrderWriter

class MemosTaskAdapter(
    googleTaskDao: GoogleTaskDao,
    caldavDao: CaldavDao,
    taskDao: TaskDao,
    taskSaver: TaskSaver,
    dirtyDao: DirtyDao,
    localBroadcastManager: LocalBroadcastManager,
    taskMover: TaskMover,
    private val memosOrderWriter: MemosOrderWriter,
    private val refreshBroadcaster: RefreshBroadcaster,
    private val ignorePriority: () -> Boolean = { false },
) : TaskAdapter(
    false,
    googleTaskDao,
    caldavDao,
    taskDao,
    taskSaver,
    dirtyDao,
    localBroadcastManager,
    taskMover,
) {
    override suspend fun moved(from: Int, to: Int, indent: Int) {
        if (indent != 0) return
        val task = getTask(from)
        if (!task.task.memo) return
        val insertIndex = if (from < to) to - 1 else to
        val flat = ignorePriority()
        if (!flat) {
            val destPriority = when (adapterSortMode()) {
                SORT_IMPORTANCE -> adapterNearestHeader(if (insertIndex <= 0) 1 else insertIndex).toInt()
                else -> task.priority
            }
            if (destPriority != task.priority) {
                return
            }
        }
        val band = if (flat) allMemos() else tasksInBand(task.priority)
        val targetId = when {
            insertIndex >= count -> band.lastOrNull()?.id ?: return
            adapterIsHeader(insertIndex) -> return
            else -> getTask(insertIndex).id
        }
        val ordered = memosOrderWriter.reorderBand(band, task.id, targetId) ?: return
        memosOrderWriter.persistOrder(ordered)
        refreshBroadcaster.broadcastRefresh()
    }

    override fun canMove(source: TaskContainer, from: Int, target: TaskContainer, to: Int): Boolean {
        if (!source.task.memo) return super.canMove(source, from, target, to)
        return ignorePriority() || source.priority == target.priority
    }

    private fun allMemos(): List<TaskContainer> = buildList {
        for (i in 0 until count) {
            if (adapterIsHeader(i)) continue
            val t = getTask(i)
            if (t.task.memo) add(t)
        }
    }

    private fun tasksInBand(priority: Int): List<TaskContainer> = buildList {
        for (i in 0 until count) {
            if (adapterIsHeader(i)) continue
            val t = getTask(i)
            if (t.priority == priority) add(t)
        }
    }
}