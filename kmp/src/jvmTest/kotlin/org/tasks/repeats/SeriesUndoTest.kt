package org.tasks.repeats

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.tasks.DatabaseTest
import org.tasks.constructor.Constructor
import org.tasks.constructor.FeatureGate
import org.tasks.data.TaskSaver
import org.tasks.data.createDueDate
import org.tasks.data.dao.DeletionDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_LOCAL
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Task
import org.tasks.preferences.AppPreferences
import org.tasks.service.TaskCleanup
import org.tasks.service.TaskDeleter
import org.tasks.time.DateTime

class SeriesUndoTest : DatabaseTest() {
    private val taskDao = db.taskDao()
    private val caldavDao = db.caldavDao()
    private val seriesDao = db.seriesDao()

    private val preferences: AppPreferences = mock {
        onBlocking { addTasksToTop() } doReturn true
    }

    private val taskSaver = TaskSaver(
        taskDao = taskDao,
        refreshBroadcaster = mock(),
        notifier = mock(),
        locationService = mock(),
        backgroundWork = mock(),
        caldavDao = caldavDao,
    )

    private val templates = SeriesTemplates(
        taskDao = taskDao,
        taskSaver = taskSaver,
        alarmDao = db.alarmDao(),
        tagDao = db.tagDao(),
        tagDataDao = db.tagDataDao(),
        locationDao = db.locationDao(),
        caldavDao = caldavDao,
        googleTaskDao = db.googleTaskDao(),
        preferences = preferences,
    )

    private val taskDeleter = TaskDeleter(
        deletionDao = db.deletionDao(),
        taskDao = taskDao,
        caldavDao = caldavDao,
        refreshBroadcaster = mock(),
        vtodoCache = mock(),
        tasksPreferences = mock(),
        taskCleanup = object : TaskCleanup {},
    )

    private val scheduler = SeriesScheduler(
        seriesDao = seriesDao,
        taskDao = taskDao,
        taskSaver = taskSaver,
        taskDeleter = taskDeleter,
        templates = templates,
        featureGate = FeatureGate(Constructor(repeat = true)),
        refreshBroadcaster = mock(),
    )

    @Before
    fun setUp() = runBlocking {
        caldavDao.insert(CaldavAccount(uuid = "local", accountType = TYPE_LOCAL))
        caldavDao.insert(CaldavCalendar(uuid = LIST, account = "local"))
    }

    private suspend fun legacy(recurrence: String = "FREQ=DAILY"): Task {
        val task = Task(title = "water", dueDate = MON, recurrence = recurrence)
        taskDao.createNew(task)
        caldavDao.insert(task, CaldavTask(task = task.id, calendar = LIST, remoteId = null), false)
        return task
    }

    private suspend fun fetch(task: Task) = taskDao.fetch(task.id)!!

    private suspend fun complete(task: Task, at: Long) {
        taskDao.update(fetch(task).copy(completionDate = at))
    }

    @Test
    fun undoRestoresDiscardedOccurrence() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        complete(row, SUN)
        scheduler.reconcile(SUN)
        assertEquals(DeletionDao.DISCARDED, fetch(row).deletionDate)

        scheduler.undoCompletion(listOf(row.id), SUN)

        val back = fetch(row)
        assertEquals(0L, back.deletionDate)
        assertFalse(back.isCompleted)
        // the successor emitted by the completion goes again; the series is where it was
        val live = seriesDao.getOccurrences(back.series!!)
        assertEquals(listOf(MON), live.map { it.scheduled })
        assertEquals(MON, seriesDao.getByUuid(back.series!!)!!.anchor)

        // and it carries on as usual
        scheduler.reconcile(day(1))
        assertEquals(listOf(day(1), day(2)), seriesDao.getOccurrences(back.series!!).map { it.scheduled }.sorted())
    }

    @Test
    fun undoReopensASeriesThatEndedWithTheCompletion() = runBlocking {
        val row = legacy("FREQ=DAILY;COUNT=1")
        scheduler.reconcile(SUN)
        complete(row, SUN)
        scheduler.reconcile(SUN)
        val uuid = fetch(row).series!!
        assertEquals(true, seriesDao.getByUuid(uuid)!!.finished > 0)

        scheduler.undoCompletion(listOf(row.id), SUN)

        assertFalse(fetch(row).isCompleted)
        assertEquals(0L, seriesDao.getByUuid(uuid)!!.finished)
    }

    @Test
    fun undoOfAPlainTaskIsLeftAlone() = runBlocking {
        val plain = Task(title = "plain", completionDate = SUN)
        taskDao.createNew(plain)

        scheduler.undoCompletion(listOf(plain.id), SUN)

        assertEquals(SUN, fetch(plain).completionDate)
    }

    companion object {
        private const val LIST = "local-list"

        private fun withTime(millis: Long) = createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, millis)

        /** Monday 2026-05-04 09:00 plus [n] days. */
        private fun day(n: Int) = withTime(DateTime(2026, 5, 4, 9, 0).plusDays(n).millis)

        private val MON = day(0)
        private val SUN = withTime(DateTime(2026, 5, 3, 20, 0).millis)
    }
}
