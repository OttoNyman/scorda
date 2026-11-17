package org.tasks.googleapis

import com.google.api.client.util.DateTime
import com.google.api.services.tasks.model.TaskList
import com.google.api.services.tasks.model.TaskLists
import com.google.api.services.tasks.model.Tasks
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.tasks.DatabaseTest
import org.tasks.data.TaskSaver
import org.tasks.data.createDueDate
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_GOOGLE_TASKS
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Task
import org.tasks.filters.CaldavFilter
import org.tasks.service.TaskCleanup
import org.tasks.service.TaskDeleter
import com.google.api.services.tasks.model.Task as GoogleTask

/** A series occurrence completed in Google's own apps comes back hidden. */
class GoogleOccurrenceCompletedTest : DatabaseTest() {
    private val taskDao = db.taskDao()
    private val caldavDao = db.caldavDao()

    private val taskSaver = TaskSaver(
        taskDao = taskDao,
        refreshBroadcaster = mock(),
        notifier = mock(),
        locationService = mock(),
        backgroundWork = mock(),
        caldavDao = caldavDao,
    )

    private val synchronizer = GoogleTaskSynchronizer(
        caldavDao = caldavDao,
        gtasksListService = mock(),
        taskDao = taskDao,
        dirtyDao = db.dirtyDao(),
        taskSaver = taskSaver,
        reporting = mock(),
        googleTaskDao = db.googleTaskDao(),
        defaultListProvider = mock {
            onBlocking { getDefaultList() } doReturn CaldavFilter(calendar = CaldavCalendar(), account = CaldavAccount())
        },
        refreshBroadcaster = mock(),
        taskDeleter = TaskDeleter(
            deletionDao = db.deletionDao(),
            taskDao = taskDao,
            caldavDao = caldavDao,
            refreshBroadcaster = mock(),
            vtodoCache = mock(),
            tasksPreferences = mock(),
            taskCleanup = object : TaskCleanup {},
        ),
        alarmDao = db.alarmDao(),
        appPreferences = mock(),
        repeatTaskHelper = mock {
            onBlocking { handleRepeat(any()) } doReturn false
        },
        taskCompleter = mock(),
        createTask = { Task() },
    )

    @Test
    fun completedOccurrenceIsKeptCompletedForTheScheduler() = runBlocking {
        caldavDao.insert(CaldavAccount(uuid = ACCOUNT, accountType = TYPE_GOOGLE_TASKS))
        caldavDao.insert(CaldavCalendar(uuid = LIST, account = ACCOUNT, name = SCORDA_LIST_NAME))
        val due = createDueDate(Task.URGENCY_SPECIFIC_DAY, DateTime.parseRfc3339("2026-05-04T00:00:00Z").value)
        val occurrence = Task(title = "water", dueDate = due, series = "series", scheduled = due)
        taskDao.createNew(occurrence)
        val link = caldavDao.insert(CaldavTask(task = occurrence.id, calendar = LIST, remoteId = REMOTE))
        db.dirtyDao().setDirtyState(link, dirtyVersion = 1, syncedVersion = 1)
        val invoker = mock<GtasksInvoker> {
            onBlocking { allGtaskLists(anyOrNull()) } doReturn
                TaskLists().setItems(listOf(TaskList().setId(LIST).setTitle(SCORDA_LIST_NAME)))
            onBlocking { getAllGtasksFromListId(anyOrNull(), any(), anyOrNull()) } doReturn
                Tasks().setItems(
                    listOf(
                        GoogleTask()
                            .setId(REMOTE)
                            .setTitle("water")
                            .setStatus("completed")
                            .setCompleted("2026-05-04T10:00:00.000Z")
                            .setHidden(true)
                    )
                )
        }
        val account = caldavDao.getAccountByUuid(ACCOUNT)!!

        synchronizer.sync(account, invoker, savedListId = LIST)

        assertEquals("", account.error.orEmpty())
        val row = taskDao.fetch(occurrence.id)!!
        assertEquals(0L, row.deletionDate)
        assertTrue(row.isCompleted)
        assertEquals("series", row.series)
    }

    companion object {
        private const val ACCOUNT = "someone@example.org"
        private const val LIST = "scorda-list"
        private const val REMOTE = "remote-1"
    }
}
