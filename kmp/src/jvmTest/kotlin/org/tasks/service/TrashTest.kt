package org.tasks.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.tasks.DatabaseTest
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_GOOGLE_TASKS
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_LOCAL
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Task
import org.tasks.time.DateTimeUtils2.currentTimeMillis

class TrashTest : DatabaseTest() {
    private val taskDao = db.taskDao()
    private val caldavDao = db.caldavDao()

    private val taskDeleter = TaskDeleter(
        deletionDao = db.deletionDao(),
        taskDao = taskDao,
        caldavDao = caldavDao,
        refreshBroadcaster = mock(),
        vtodoCache = mock(),
        tasksPreferences = mock(),
        taskCleanup = object : TaskCleanup {},
    )

    @Before
    fun setUp() = runBlocking {
        caldavDao.insert(CaldavAccount(uuid = "local", accountType = TYPE_LOCAL))
        caldavDao.insert(CaldavCalendar(uuid = LOCAL_LIST, account = "local"))
        caldavDao.insert(CaldavAccount(uuid = "google", accountType = TYPE_GOOGLE_TASKS))
        caldavDao.insert(CaldavCalendar(uuid = GOOGLE_LIST, account = "google"))
    }

    private suspend fun newTask(
        title: String,
        list: String = LOCAL_LIST,
        parent: Long = 0,
        deleted: Long = 0,
        remoteId: String? = null,
    ): Task {
        val task = Task(title = title, parent = parent, deletionDate = deleted)
        taskDao.createNew(task)
        caldavDao.insert(
            task = task,
            caldavTask = CaldavTask(task = task.id, calendar = list, remoteId = remoteId),
            addToTop = false,
        )
        return task
    }

    private suspend fun fetch(task: Task) = taskDao.fetch(task.id)

    @Test
    fun deletedTaskStaysForTheRetentionPeriod() = runBlocking {
        val task = newTask("a")

        taskDeleter.markDeleted(task)
        taskDeleter.purge()

        assertTrue(fetch(task)!!.isDeleted)

        taskDeleter.purge(now = currentTimeMillis() + DAY * (TaskDeleter.TRASH_RETENTION_DAYS + 1))

        assertNull(fetch(task))
    }

    @Test
    fun restoreBringsBackSubtasksTrashedTogether() = runBlocking {
        val parent = newTask("parent")
        val together = newTask("together", parent = parent.id)
        val earlier = newTask("earlier", parent = parent.id, deleted = 5)

        taskDeleter.markDeleted(parent)
        val restored = taskDeleter.restore(listOf(parent.id))

        assertEquals(setOf(parent.id, together.id), restored.map { it.id }.toSet())
        assertEquals(0L, fetch(parent)!!.deletionDate)
        assertEquals(0L, fetch(together)!!.deletionDate)
        assertTrue(fetch(earlier)!!.isDeleted)
    }

    @Test
    fun restoredSubtaskLeavesItsTrashedParent() = runBlocking {
        val parent = newTask("parent")
        val child = newTask("child", parent = parent.id)

        taskDeleter.markDeleted(parent)
        taskDeleter.restore(listOf(child.id))

        assertTrue(fetch(parent)!!.isDeleted)
        assertEquals(0L, fetch(child)!!.parent)
        assertEquals(0L, fetch(child)!!.deletionDate)
    }

    @Test
    fun deleteForeverSkipsTheRetentionPeriod() = runBlocking {
        val gone = newTask("gone")
        val kept = newTask("kept")
        taskDeleter.markDeleted(listOf(gone.id, kept.id))

        taskDeleter.deleteForever(listOf(gone.id))

        assertNull(fetch(gone))
        assertNotNull(fetch(kept))
    }

    @Test
    fun emptyTrashLeavesLiveTasks() = runBlocking {
        val live = newTask("live")
        val trashed = newTask("trashed")
        taskDeleter.markDeleted(trashed)

        taskDeleter.deleteForever()

        assertNotNull(fetch(live))
        assertNull(fetch(trashed))
    }

    @Test
    fun taskStillOnServerWaitsForItsDeleteToBePushed() = runBlocking {
        val task = newTask("synced", list = GOOGLE_LIST, remoteId = "remote")
        taskDeleter.markDeleted(task)

        taskDeleter.deleteForever(listOf(task.id))

        assertNotNull(fetch(task))
        assertTrue(db.deletionDao().getTrash().isEmpty())

        val caldavTask = caldavDao.getTask(task.id)!!
        caldavDao.update(caldavTask.copy(remoteId = ""))
        taskDeleter.purge()

        assertNull(fetch(task))
    }

    companion object {
        private const val LOCAL_LIST = "local-list"
        private const val GOOGLE_LIST = "google-list"
        private const val DAY = 24L * 60 * 60 * 1000
    }
}
