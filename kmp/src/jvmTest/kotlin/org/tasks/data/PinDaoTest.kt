package org.tasks.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.DatabaseTest
import org.tasks.data.entity.Task
import org.tasks.time.DateTimeUtils2.currentTimeMillis

class PinDaoTest : DatabaseTest() {
    private val taskDao = db.taskDao()

    private suspend fun newTask(title: String, completed: Boolean = false, deleted: Boolean = false): Task {
        val task = Task(
            title = title,
            completionDate = if (completed) currentTimeMillis() else 0,
            deletionDate = if (deleted) currentTimeMillis() else 0,
        )
        taskDao.createNew(task)
        return task
    }

    @Test
    fun getPinnedReturnsOnlyActivePins() = runBlocking {
        val ids = listOf(
            newTask("active"),
            newTask("done", completed = true),
            newTask("trashed", deleted = true),
        ).map { it.id }
        taskDao.setPinned(ids, 1_000L)

        assertEquals(listOf("active"), taskDao.getPinned().map { it.title })
    }

    @Test
    fun setPinnedZeroUnpins() = runBlocking {
        val id = newTask("active").id
        taskDao.setPinned(listOf(id), 1_000L)
        taskDao.setPinned(listOf(id), 0L)

        assertTrue(taskDao.getPinned().isEmpty())
    }
}
