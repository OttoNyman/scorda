/*
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.adapter

import com.todoroo.astrid.core.SortHelper.SORT_DUE
import com.todoroo.astrid.core.SortHelper.SORT_IMPORTANCE
import com.todoroo.astrid.core.SortHelper.SORT_LIST
import com.todoroo.astrid.core.SortHelper.SORT_START
import org.tasks.data.dao.TaskDao
import org.tasks.data.TaskSaver
import org.tasks.data.TaskMover
import org.tasks.BuildConfig
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.data.TaskContainer
import org.tasks.data.createDueDate
import org.tasks.data.createHideUntil
import org.tasks.data.findParentIndex
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.DirtyDao
import org.tasks.data.dao.GoogleTaskDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.Task
import org.tasks.data.entity.Task.Companion.HIDE_UNTIL_SPECIFIC_DAY
import org.tasks.date.DateTimeUtils.toDateTime
import org.tasks.time.millisOfDay
import kotlin.math.min

open class TaskAdapter(
    private val newTasksOnTop: Boolean,
    private val googleTaskDao: GoogleTaskDao,
    private val caldavDao: CaldavDao,
    private val taskDao: TaskDao,
    private val taskSaver: TaskSaver,
    private val dirtyDao: DirtyDao,
    private val refreshBroadcaster: RefreshBroadcaster,
    private val taskMover: TaskMover,
) {
    private val selected = HashSet<Long>()
    private lateinit var dataSource: TaskAdapterDataSource

    val count: Int
        get() = dataSource.getTaskCount()

    fun setDataSource(dataSource: TaskAdapterDataSource) {
        this.dataSource = dataSource
    }

    val numSelected: Int
        get() = selected.size

    fun getSelected(): ArrayList<Long> = ArrayList(selected)

    fun setSelected(ids: Collection<Long>) {
        clearSelections()
        selected.addAll(ids)
    }

    fun clearSelections() = selected.clear()

    open fun getIndent(task: TaskContainer): Int = task.indent

    open fun canMove(source: TaskContainer, from: Int, target: TaskContainer, to: Int): Boolean {
        if (target.isSingleLevelSubtask) {
            return if (!source.hasChildren() || to <= 0 || to >= count - 1) {
                true
            } else if (from < to) {
                when {
                    target.hasChildren() -> false
                    target.hasParent() -> !getTask(to + 1).hasParent()
                    else -> true
                }
            } else {
                when {
                    target.hasChildren() -> true
                    target.hasParent() -> target.parent == source.id && target.secondarySort == 0L
                    else -> true
                }
            }
        } else {
            return !taskIsChild(source, to)
        }
    }

    // subtasks are retired: a sideways drag never makes one
    open fun maxIndent(previousPosition: Int, task: TaskContainer): Int = 0

    fun minIndent(nextPosition: Int, task: TaskContainer): Int {
        (nextPosition until count).forEach {
            if (isHeader(it)) {
                return 0
            }
            val next = getTask(it)
            if (next.isSingleLevelSubtask) {
                return if (task.hasChildren() || !next.hasParent()) 0 else 1
            }
            if (!taskIsChild(task, it)) {
                return next.indent
            }
        }
        return 0
    }

    fun minIndent(nextPosition: Int, task: TaskContainer, maxIndent: Int): Int =
        min(minIndent(nextPosition, task), maxIndent)

    fun isSelected(task: TaskContainer): Boolean = selected.contains(task.id)

    fun toggleSelection(task: TaskContainer) {
        val id = task.id
        if (selected.contains(id)) {
            selected.remove(id)
        } else {
            selected.add(id)
        }
    }

    open fun supportsAstridSorting(): Boolean = false

    // Subtasks are retired: a drag only changes the sort group and never a parent, so a row
    // nested from Google keeps its parent.
    open suspend fun moved(from: Int, to: Int, indent: Int) {
        changeSortGroup(getTask(from), if (from < to) to - 1 else to)
    }

    fun isHeader(position: Int): Boolean = dataSource.isHeader(position)

    protected fun adapterIsHeader(position: Int): Boolean = dataSource.isHeader(position)

    protected fun adapterSortMode(): Int = dataSource.sortMode

    protected fun adapterNearestHeader(position: Int): Long = dataSource.nearestHeader(position)

    fun getTask(position: Int): TaskContainer = dataSource.getItem(position)!!

    fun getItemUuid(position: Int): String = getTask(position).uuid

    open suspend fun onCompletedTask(uuid: String, newState: Boolean) {}

    open suspend fun onTaskCreated(uuid: String) {}

    open suspend fun onTaskDeleted(task: Task) {}

    open fun supportsHiddenTasks(): Boolean = true

    private fun taskIsChild(source: TaskContainer, destinationIndex: Int): Boolean {
        (destinationIndex downTo 0).forEach {
            if (isHeader(it)) {
                return false
            }
            when (getTask(it).parent) {
                0L -> return false
                source.parent -> return false
                source.id -> return true
            }
        }
        return false
    }

    private fun findParent(indent: Int, to: Int): TaskContainer? =
        findParentIndex(indent, to) { getTask(it).indent }?.let { getTask(it) }

    private suspend fun changeSortGroup(task: TaskContainer, pos: Int) {
        val header = dataSource.nearestHeader(if (pos == 0) 1 else pos)
        // negative headers (pinned, overdue, completed) are sections, not a date, priority or list
        if (header < 0) return
        when(dataSource.sortMode) {
            SORT_IMPORTANCE -> {
                val newPriority = header.toInt()
                if (newPriority != task.priority) {
                    taskSaver.save(task.task.copy(priority = newPriority), task.task)
                }
            }
            SORT_LIST -> taskMover.move(task.id, header)
            SORT_DUE -> applyDueDate(task.task, header)
            SORT_START -> applyStartDate(task.task, header)
        }
    }

    private suspend fun applyDueDate(task: Task, date: Long) {
        val original = task.copy()
        task.setDueDateAdjustingHideUntil(when {
            date == 0L -> 0L
            task.hasDueTime() -> date.toDateTime().withMillisOfDay(original.dueDate.millisOfDay).millis
            else -> createDueDate(Task.URGENCY_SPECIFIC_DAY, date)
        })
        if (original.dueDate != task.dueDate) {
            taskSaver.save(task, original)
        }
    }

    private suspend fun applyStartDate(task: Task, date: Long) {
        val original = task.copy()
        task.hideUntil = when {
            date == 0L -> 0L
            task.hasStartDate() -> date.toDateTime().withMillisOfDay(original.hideUntil.millisOfDay).millis
            else -> task.createHideUntil(HIDE_UNTIL_SPECIFIC_DAY, date)
        }
        if (original.hideUntil != task.hideUntil) {
            taskSaver.save(task, original)
        }
    }

    protected suspend fun moveGoogleTask(from: Int, to: Int, indent: Int) {
        val task = getTask(from)
        val googleTask = task.caldavTask ?: return
        val list = googleTask.calendar ?: return
        val previous = if (to > 0) getTask(to - 1) else null
        if (previous == null) {
            googleTaskDao.move(
                task = task.task,
                list = list,
                newParent = 0,
                newPosition = 0,
            )
        } else if (to == count || to <= from) {
            when {
                indent == 0 ->
                    googleTaskDao.move(
                        task = task.task,
                        list = list,
                        newParent = 0,
                        newPosition = previous.primarySort + if (to == count) 0 else 1,
                    )
                previous.hasParent() && previous.parent == task.parent ->
                    googleTaskDao.move(
                        task = task.task,
                        list = list,
                        newParent = previous.parent,
                        newPosition = previous.secondarySort + if (to == count) 0 else 1,
                    )
                previous.hasParent() ->
                    googleTaskDao.move(
                        task = task.task,
                        list = list,
                        newParent = previous.parent,
                        newPosition = previous.secondarySort + 1,
                    )
                else ->
                    googleTaskDao.move(
                        task = task.task,
                        list = list,
                        newParent = previous.id,
                        newPosition = 0,
                    )
            }
        } else {
            when {
                indent == 0 ->
                    googleTaskDao.move(
                        task = task.task,
                        list = list,
                        newParent = 0,
                        newPosition = previous.primarySort + if (task.hasParent()) 1 else 0,
                    )
                previous.hasParent() && previous.parent == task.parent ->
                    googleTaskDao.move(
                        task = task.task,
                        list = list,
                        newParent = previous.parent,
                        newPosition = previous.secondarySort,
                    )
                previous.hasParent() ->
                    googleTaskDao.move(
                        task = task.task,
                        list = list,
                        newParent = previous.parent,
                        newPosition = previous.secondarySort + 1,
                    )
                else ->
                    googleTaskDao.move(
                        task = task.task,
                        list = list,
                        newParent = previous.id,
                        newPosition = 0,
                    )
            }
        }
        refreshBroadcaster.broadcastRefresh()
        if (BuildConfig.DEBUG) {
            googleTaskDao.validateSorting(task.caldav!!)
        }
    }

    protected suspend fun moveCaldavTask(from: Int, to: Int, indent: Int) {
        val task = getTask(from)
        val oldParent = task.parent
        val newParent = changeCaldavParent(task, indent, to)

        if (oldParent == newParent && from == to) {
            return
        }

        val previous = if (to > 0) getTask(to - 1) else null
        val next = if (to < count) getTask(to) else null

        val newPosition = when {
            previous == null -> next!!.caldavSortOrder - 1
            indent > previous.indent && next?.indent == indent -> next.caldavSortOrder - 1
            indent > previous.indent -> null
            indent == previous.indent -> previous.caldavSortOrder + 1
            else -> getTask((to - 1 downTo 0).find { getTask(it).indent == indent }!!).caldavSortOrder + 1
        }
        caldavDao.move(
            task = task,
            previousParent = oldParent,
            newParent = newParent,
            newPosition = newPosition,
        )
        refreshBroadcaster.broadcastRefresh()
    }

    private suspend fun changeCaldavParent(task: TaskContainer, indent: Int, to: Int): Long {
        val newParent = findParent(indent, to)?.id ?: 0
        if (task.parent != newParent) {
            changeCaldavParent(task, newParent)
        }
        return newParent
    }

    private suspend fun changeCaldavParent(task: TaskContainer, newParent: Long) {
        val caldavTask = task.caldavTask ?: return
        val skipRemoteParentUpdate = !CaldavAccount.pushesRemoteParent(task.accountType)
        if (newParent == 0L) {
            if (!skipRemoteParentUpdate) {
                caldavTask.remoteParent = ""
                caldavDao.update(caldavTask.id, caldavTask.remoteParent)
            }
        } else {
            val parentTask = caldavDao.getTask(newParent) ?: return
            if (parentTask.calendar == caldavTask.calendar) {
                if (!skipRemoteParentUpdate) {
                    caldavTask.remoteParent = parentTask.remoteId
                    caldavDao.update(caldavTask.id, caldavTask.remoteParent)
                }
            } else {
                taskMover.move(task = task.id, listUuid = parentTask.calendar!!, newParent = newParent)
                return
            }
        }
        task.parent = newParent
        taskSaver.save(task.task, null)
    }
}