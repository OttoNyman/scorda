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
    private val taskSaver: TaskSaver,
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
    /**
     * Asks before a drop moves a memo into another priority band. The list shows the memo back
     * in place meanwhile; [apply] makes the change.
     */
    var confirmPriorityChange: (apply: suspend () -> Unit) -> Unit = {}

    // memos never nest
    override fun maxIndent(previousPosition: Int, task: TaskContainer) = 0

    override fun canMove(source: TaskContainer, from: Int, target: TaskContainer, to: Int): Boolean =
        source.task.memo || super.canMove(source, from, target, to)

    override suspend fun moved(from: Int, to: Int, indent: Int) {
        val memo = getTask(from)
        if (!memo.task.memo) return
        val insertIndex = if (from < to) to - 1 else to
        val header = if (!ignorePriority() && adapterSortMode() == SORT_IMPORTANCE) {
            adapterNearestHeader(maxOf(insertIndex, 1)).toInt()
        } else {
            -1
        }
        val priority = if (header >= 0) header else memo.priority
        val insertAt = insertIndex - (0..insertIndex).count { adapterIsHeader(it) }
        val ordered = memosOrderWriter.orderAfterDrop(
            all = listed(),
            movedId = memo.id,
            insertAt = insertAt,
            priority = priority.takeIf { header >= 0 },
        )
        if (priority == memo.priority) {
            memosOrderWriter.persistOrder(ordered)
            refreshBroadcaster.broadcastRefresh()
        } else {
            confirmPriorityChange {
                taskSaver.save(memo.task.copy(priority = priority), memo.task)
                memosOrderWriter.persistOrder(ordered)
                refreshBroadcaster.broadcastRefresh()
            }
        }
    }

    /** Rows in list order. [count] holds rows only, headers come on top of it. */
    private fun listed(): List<TaskContainer> = buildList {
        var position = 0
        while (size < count) {
            if (!adapterIsHeader(position)) add(getTask(position))
            position++
        }
    }
}
