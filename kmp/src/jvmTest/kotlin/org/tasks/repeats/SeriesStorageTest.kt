package org.tasks.repeats

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.tasks.DatabaseTest
import org.tasks.data.dao.DeletionDao
import org.tasks.data.entity.Series
import org.tasks.data.entity.Task
import org.tasks.service.TaskCleanup
import org.tasks.service.TaskDeleter

class SeriesStorageTest : DatabaseTest() {
    private val taskDao = db.taskDao()

    private val taskDeleter = TaskDeleter(
        deletionDao = db.deletionDao(),
        taskDao = taskDao,
        caldavDao = db.caldavDao(),
        refreshBroadcaster = mock(),
        vtodoCache = mock(),
        tasksPreferences = mock(),
        taskCleanup = object : TaskCleanup {},
    )

    private suspend fun newTask(): Long {
        val task = Task(title = "occurrence")
        taskDao.createNew(task)
        return task.id
    }

    @Test
    fun seriesRoundTrip() = runBlocking {
        val series = Series(
            uuid = "s1",
            recurrence = "FREQ=DAILY",
            anchor = 1_000L,
            template = "{}",
            created = 1,
            modified = 2,
        )

        val id = db.seriesDao().insert(series)

        assertEquals(series.copy(id = id), db.seriesDao().getByUuid("s1"))
    }

    @Test
    fun discardedIsHiddenFromTrashAndPurged() = runBlocking {
        val id = newTask()

        taskDeleter.discard(listOf(id))

        assertFalse(db.deletionDao().getTrash().contains(id))
        assertEquals(DeletionDao.DISCARDED, taskDao.fetch(id)!!.deletionDate)

        taskDeleter.purge()

        assertNull(taskDao.fetch(id))
    }

    @Test
    fun discardedCanBeRestored() = runBlocking {
        val id = newTask()

        taskDeleter.discard(listOf(id))
        taskDeleter.restore(listOf(id))

        assertEquals(0L, taskDao.fetch(id)!!.deletionDate)
    }
}
