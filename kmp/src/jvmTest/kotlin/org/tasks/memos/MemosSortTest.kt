package org.tasks.memos

import com.todoroo.astrid.core.SortHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.tasks.DatabaseTest
import org.tasks.data.TaskContainer
import org.tasks.data.entity.Task
import org.tasks.preferences.DefaultQueryPreferences

class MemosSortTest : DatabaseTest() {
    private val taskDao = db.taskDao()
    private val writer = MemosOrderWriter(taskDao)

    private suspend fun insertMemo(title: String, priority: Int, order: Long?): Long {
        return taskDao.createNew(Task(title = title, memo = true, priority = priority, order = order))
    }

    private suspend fun container(id: Long): TaskContainer {
        val task = taskDao.fetch(id)!!
        return TaskContainer(task = task, parentComplete = false, sortGroup = task.priority.toLong())
    }

    @Test
    fun samePriorityReorderSwapsOrder() = runBlocking {
        val idA = insertMemo("a", Task.Priority.MEDIUM, 0L)
        val idB = insertMemo("b", Task.Priority.MEDIUM, 1L)
        val a = container(idA)
        val b = container(idB)
        val ordered = writer.reorderBand(listOf(a, b), a.id, b.id)!!
        writer.persistOrder(ordered)
        assertEquals(1L, taskDao.fetch(a.id)!!.order)
        assertEquals(0L, taskDao.fetch(b.id)!!.order)
    }

    @Test
    fun crossBandReorderDoesNotChangePriority() = runBlocking {
        val idHigh = insertMemo("high", Task.Priority.HIGH, 0L)
        val idLow = insertMemo("low", Task.Priority.LOW, 0L)
        val high = container(idHigh)
        val low = container(idLow)
        assertNull(writer.reorderBand(listOf(high), high.id, low.id))
        assertEquals(Task.Priority.HIGH, taskDao.fetch(high.id)!!.priority)
        assertEquals(Task.Priority.LOW, taskDao.fetch(low.id)!!.priority)
    }

    @Test
    fun queryPreferencesUseManualOrderInsideImportance() {
        val prefs = MemosQueryPreferences(DefaultQueryPreferences(), SortHelper.SORT_IMPORTANCE)
        assertEquals(SortHelper.SORT_IMPORTANCE, prefs.groupMode)
        assertEquals(SortHelper.SORT_CALDAV, prefs.sortMode)
    }

    @Test
    fun queryPreferencesSkipPriorityHeadersWhenPriorityIsOff() {
        val prefs = MemosQueryPreferences(
            DefaultQueryPreferences(),
            SortHelper.SORT_IMPORTANCE,
            groupByPriority = false,
        )
        assertEquals(SortHelper.GROUP_NONE, prefs.groupMode)
    }

    @Test
    fun queryPreferencesUseFlatModified() {
        val prefs = MemosQueryPreferences(DefaultQueryPreferences(), SortHelper.SORT_MODIFIED)
        assertEquals(SortHelper.GROUP_NONE, prefs.groupMode)
        assertEquals(SortHelper.SORT_MODIFIED, prefs.sortMode)
    }
}