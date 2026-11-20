package org.tasks.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.tasks.DatabaseTest
import org.tasks.constructor.Constructor
import org.tasks.data.entity.Task
import org.tasks.filters.CompletedFilter
import org.tasks.filters.Filter
import org.tasks.filters.MemosFilter
import org.tasks.filters.TodayFilter
import org.tasks.preferences.DefaultQueryPreferences
import org.tasks.time.DateTimeUtils2.currentTimeMillis

class PinOrderTest : DatabaseTest() {
    private val taskDao = db.taskDao()

    private val today = TodayFilter(title = "Today")

    private suspend fun newTask(
        title: String,
        due: Long = currentTimeMillis(),
        completed: Long = 0,
        memo: Boolean = false,
    ): Task {
        val task = Task(
            title = title,
            dueDate = if (memo) 0 else due,
            completionDate = completed,
            memo = memo,
        )
        taskDao.createNew(task)
        return task
    }

    private suspend fun rows(filter: Filter = today, pinning: Boolean = true) = taskDao.fetchTasks(
        TaskListQuery.getQuery(
            DefaultQueryPreferences().apply {
                isAstridSort = true
                completedTasksAtBottom = true
            },
            filter,
            features = Constructor(pinning = pinning, memos = true),
        )
    ).map { it.title!! }

    private suspend fun pin(title: String, at: Long = currentTimeMillis()) {
        val id = taskDao.getAll().first { it.title == title }.id
        taskDao.setPinned(listOf(id), at)
    }

    @Test
    fun aPinnedTaskComesFirst() = runBlocking {
        newTask("a", due = currentTimeMillis() - 3_600_000)
        newTask("b", due = currentTimeMillis())
        val baseline = rows()

        pin(baseline.last())

        assertEquals(baseline.last(), rows().first())
    }

    @Test
    fun theLatestPinIsOnTop() = runBlocking {
        newTask("a", due = currentTimeMillis() - 3_600_000)
        newTask("b", due = currentTimeMillis() - 1_800_000)
        newTask("c", due = currentTimeMillis())
        val baseline = rows()

        pin(baseline[1], at = 1_000)
        pin(baseline[2], at = 2_000)

        assertEquals(listOf(baseline[2], baseline[1], baseline[0]), rows())
    }

    @Test
    fun pinsAreIgnoredWhenPinningIsOff() = runBlocking {
        newTask("a", due = currentTimeMillis() - 3_600_000)
        newTask("b", due = currentTimeMillis())
        val baseline = rows(pinning = false)

        pin(baseline.last())

        assertEquals(baseline, rows(pinning = false))
    }

    @Test
    fun aCompletedPinIsNotFirstButComesBackWhenUncompleted() = runBlocking {
        newTask("open")
        val done = newTask("done", completed = currentTimeMillis())
        pin("done")

        assertNotEquals("done", rows().first())

        taskDao.update(taskDao.fetch(done.id)!!.copy(completionDate = 0))

        assertEquals("done", rows().first())
    }

    @Test
    fun completedFilterIgnoresPins() = runBlocking {
        val completed = CompletedFilter(title = "Completed")
        newTask("older", completed = currentTimeMillis() - 60_000)
        newTask("newer", completed = currentTimeMillis())
        val baseline = rows(completed)

        pin(baseline.last())

        assertEquals(baseline, rows(completed))
    }

    @Test
    fun pinnedMemoComesFirst() = runBlocking {
        val memos = MemosFilter(title = "Memos")
        newTask("first", memo = true)
        newTask("second", memo = true)
        val baseline = rows(memos)

        pin(baseline.last())

        assertEquals(baseline.last(), rows(memos).first())
    }
}
