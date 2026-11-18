package org.tasks.memos

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.DatabaseTest
import org.tasks.data.TaskListQuery
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.Task
import org.tasks.data.sql.Criterion
import org.tasks.data.sql.QueryTemplate
import org.tasks.filters.CompletedFilter
import org.tasks.filters.Filter
import org.tasks.filters.FilterImpl
import org.tasks.filters.MyTasksFilter
import org.tasks.filters.RecentlyModifiedFilter
import org.tasks.filters.SearchFilter
import org.tasks.filters.TodayFilter
import org.tasks.preferences.DefaultQueryPreferences
import org.tasks.time.DateTimeUtils2

class MemosGuardTest : DatabaseTest() {
    private val taskDao = db.taskDao()
    private val preferences = DefaultQueryPreferences()

    private suspend fun titles(filter: Filter): List<String?> =
        taskDao.fetchTasks(TaskListQuery.getQuery(preferences, filter)).map { it.title }

    @Test
    fun memosAreExcludedFromBuiltInTaskUniverses() = runBlocking {
        val marker = "guardx"
        val now = DateTimeUtils2.currentTimeMillis()
        val task = Task(title = "task $marker", memo = false, modificationDate = now, dueDate = now)
        val memo = Task(
            title = "memo $marker",
            memo = true,
            modificationDate = now,
            dueDate = now,
        )
        taskDao.createNew(task)
        taskDao.createNew(memo)

        val taskOnlyFilters = listOf(
            MyTasksFilter(title = "my"),
            TodayFilter(title = "today"),
            RecentlyModifiedFilter(title = "recent"),
            SearchFilter(title = "search", query = marker),
            CompletedFilter(title = "done"),
            FilterImpl(
                sql = QueryTemplate()
                    .where(TaskDao.TaskCriteria.activeAndVisible())
                    .toString(),
            ),
        )
        for (filter in taskOnlyFilters) {
            assertFalse(
                "memo leaked into ${filter::class.simpleName}",
                titles(filter).any { it == memo.title },
            )
        }
        assertTrue(titles(MyTasksFilter(title = "my")).any { it == task.title })
        assertFalse(taskDao.getLocalTasks().contains(memo.id))
        assertTrue(taskDao.getLocalTasks().contains(task.id))

        val memoRows = taskDao.fetchTasks(
            TaskListQuery.getQuery(
                preferences,
                FilterImpl(
                    sql = QueryTemplate()
                        .where(Criterion.and(Task.MEMO.eq(1), Task.DELETION_DATE.lte(0)))
                        .toString(),
                ),
            ),
        )
        assertEquals(listOf(memo.title), memoRows.map { it.title })
    }
}