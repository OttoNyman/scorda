package org.tasks.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tasks.DatabaseTest
import org.tasks.data.entity.Task
import org.tasks.time.DateTimeUtils2.currentTimeMillis

/** «Описание» off with «Удалить»: which descriptions go. Memos are their text, not a description. */
class DescriptionsDaoTest : DatabaseTest() {
    private val taskDao = db.taskDao()

    private suspend fun newTask(title: String, notes: String?, memo: Boolean = false, deleted: Boolean = false) {
        taskDao.createNew(
            Task(
                title = title,
                notes = notes,
                memo = memo,
                deletionDate = if (deleted) currentTimeMillis() else 0,
            )
        )
    }

    @Test
    fun onlyLiveTasksWithADescriptionAreListed() = runBlocking {
        newTask("described", notes = "buy milk")
        newTask("done and described", notes = "- [x] eggs")
        newTask("blank", notes = "   ")
        newTask("none", notes = null)
        newTask("memo", notes = "a memo's text", memo = true)
        newTask("trashed", notes = "gone", deleted = true)

        assertEquals(
            setOf("described", "done and described"),
            taskDao.getWithDescriptions().map { it.title }.toSet(),
        )
    }
}
