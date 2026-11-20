package org.tasks.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.tasks.DatabaseTest
import org.tasks.data.entity.Task
import org.tasks.notifications.CancelReason
import org.tasks.notifications.Notifier
import org.tasks.time.DateTimeUtils2.currentTimeMillis

class TaskSaverPinTest : DatabaseTest() {
    private val taskDao = db.taskDao()

    private var refreshes = 0

    private val notifier = object : Notifier {
        override suspend fun cancel(id: Long, reason: CancelReason) = Unit
        override suspend fun cancel(ids: List<Long>, reason: CancelReason) = Unit
        override suspend fun cancelAll(reason: CancelReason) = Unit
        override fun triggerNotifications() = Unit
        override suspend fun refreshPins() {
            refreshes++
        }
    }

    private val taskSaver = TaskSaver(
        taskDao = taskDao,
        refreshBroadcaster = mock(),
        notifier = notifier,
        locationService = mock(),
        backgroundWork = mock(),
        caldavDao = db.caldavDao(),
    )

    @Test
    fun savingANewPinnedTaskShowsItsCardAtOnce() = runBlocking {
        val task = Task(title = "new", pinned = currentTimeMillis())
        taskDao.createNew(task)

        taskSaver.afterSave(task, null)

        assertEquals(1, refreshes)
    }

    @Test
    fun restoringAPinnedTaskFromTheTrashShowsItsCardAtOnce() = runBlocking {
        val task = Task(title = "restored", pinned = currentTimeMillis())
        taskDao.createNew(task)

        taskSaver.afterSave(task, task.copy(deletionDate = 1L))

        assertEquals(1, refreshes)
    }

    @Test
    fun anOrdinaryEditOfALooseTaskLeavesTheShadeAlone() = runBlocking {
        val task = Task(title = "loose")
        taskDao.createNew(task)

        taskSaver.afterSave(task.copy(title = "renamed"), task)

        assertEquals(0, refreshes)
    }
}
