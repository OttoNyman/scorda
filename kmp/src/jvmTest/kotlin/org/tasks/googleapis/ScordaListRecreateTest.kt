package org.tasks.googleapis

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.DatabaseTest
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_GOOGLE_TASKS
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Task

class ScordaListRecreateTest : DatabaseTest() {
    @Test
    fun recreateKeepsTasksAndMarksThemDirty() = runBlocking {
        val dao = db.caldavDao()
        dao.insert(CaldavAccount(accountType = TYPE_GOOGLE_TASKS, uuid = "acct", username = "a@b.c"))
        dao.insert(CaldavCalendar(account = "acct", uuid = "old-list", name = "Scorda"))
        dao.insert(CaldavCalendar(account = "acct", uuid = "new-list", name = "Scorda"))
        val task = Task(title = "keep me")
        db.taskDao().createNew(task)
        val linkId = dao.insert(
            CaldavTask(task = task.id, calendar = "old-list", remoteId = "remote-1"),
        )
        db.dirtyDao().setDirtyState(linkId, dirtyVersion = 1, syncedVersion = 1)

        moveLocalTasksToList(dao, "old-list", "new-list")

        val moved = dao.getTask(task.id)!!
        assertEquals("new-list", moved.calendar)
        assertEquals("", moved.remoteId)
        assertEquals(null, moved.remoteParent)
        assertTrue(db.dirtyDao().isDirty(linkId) == true)
        assertTrue(shouldDeleteLocalList("old-list", keptRemoteIds = setOf("new-list"), protectedId = "new-list"))
        assertEquals(false, shouldDeleteLocalList("new-list", keptRemoteIds = setOf("new-list"), protectedId = "new-list"))
    }
}
