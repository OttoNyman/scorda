package org.tasks.memos

import com.todoroo.astrid.core.SortHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tasks.DatabaseTest
import org.tasks.constructor.Constructor
import org.tasks.data.TaskContainer
import org.tasks.data.TaskListQuery
import org.tasks.data.entity.Task
import org.tasks.filters.MemosFilter
import org.tasks.preferences.DefaultQueryPreferences

class MemosSortTest : DatabaseTest() {
    private val taskDao = db.taskDao()
    private val writer = MemosOrderWriter(taskDao)

    private suspend fun insertMemo(
        title: String,
        priority: Int,
        order: Long?,
        created: Long = 0L,
    ): Long {
        return taskDao.createNew(
            Task(title = title, memo = true, priority = priority, order = order, creationDate = created)
        )
    }

    private fun memo(id: Long, priority: Int) =
        TaskContainer(task = Task(id = id, memo = true, priority = priority))

    private suspend fun titles(sortMode: Int, features: Constructor): List<String?> =
        taskDao.fetchTasks(
            TaskListQuery.getQuery(
                MemosQueryPreferences(DefaultQueryPreferences(), sortMode),
                MemosFilter(title = "Memos"),
                features = features,
            )
        ).map { it.title }

    @Test
    fun persistOrderWritesPositions() = runBlocking {
        val idA = insertMemo("a", Task.Priority.MEDIUM, 0L)
        val idB = insertMemo("b", Task.Priority.MEDIUM, 1L)
        writer.persistOrder(listOf(idB, idA))
        assertEquals(1L, taskDao.fetch(idA)!!.order)
        assertEquals(0L, taskDao.fetch(idB)!!.order)
    }

    @Test
    fun manualOrderHoldsInFlatLists() = runBlocking {
        insertMemo("a", Task.Priority.HIGH, 1L)
        insertMemo("b", Task.Priority.HIGH, 0L)
        insertMemo("c", Task.Priority.MEDIUM, 0L)
        val expected = listOf("b", "a", "c")
        assertEquals(expected, titles(SortHelper.SORT_IMPORTANCE, Constructor()))
    }

    @Test
    fun createdSortShowsNewestFirst() = runBlocking {
        insertMemo("old", Task.Priority.HIGH, 0L, created = 1_000L)
        insertMemo("new", Task.Priority.LOW, 0L, created = 2_000L)
        assertEquals(listOf("new", "old"), titles(SortHelper.SORT_CREATED, Constructor()))
    }

    @Test
    fun dropInsideBandReordersThatBand() {
        val all = listOf(
            memo(1, Task.Priority.HIGH),
            memo(2, Task.Priority.HIGH),
            memo(3, Task.Priority.MEDIUM),
            memo(4, Task.Priority.MEDIUM),
        )
        assertEquals(listOf(4L, 3L), writer.orderAfterDrop(all, movedId = 4, insertAt = 2, priority = Task.Priority.MEDIUM))
    }

    @Test
    fun dropIntoOtherBandPlacesMemoAtDropPoint() {
        val all = listOf(
            memo(1, Task.Priority.HIGH),
            memo(2, Task.Priority.HIGH),
            memo(3, Task.Priority.MEDIUM),
            memo(4, Task.Priority.MEDIUM),
        )
        assertEquals(listOf(3L, 1L, 4L), writer.orderAfterDrop(all, movedId = 1, insertAt = 2, priority = Task.Priority.MEDIUM))
        assertEquals(listOf(1L, 2L, 4L), writer.orderAfterDrop(all, movedId = 4, insertAt = 2, priority = Task.Priority.HIGH))
        assertEquals(listOf(4L, 1L, 2L), writer.orderAfterDrop(all, movedId = 4, insertAt = 0, priority = Task.Priority.HIGH))
    }

    @Test
    fun dropWithoutBandsOrdersEverything() {
        val all = listOf(
            memo(1, Task.Priority.HIGH),
            memo(2, Task.Priority.MEDIUM),
            memo(3, Task.Priority.LOW),
        )
        assertEquals(listOf(2L, 3L, 1L), writer.orderAfterDrop(all, movedId = 1, insertAt = 9, priority = null))
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

    @Test
    fun queryPreferencesUseFlatCreated() {
        val prefs = MemosQueryPreferences(DefaultQueryPreferences(), SortHelper.SORT_CREATED)
        assertEquals(SortHelper.GROUP_NONE, prefs.groupMode)
        assertEquals(SortHelper.SORT_CREATED, prefs.sortMode)
    }
}
