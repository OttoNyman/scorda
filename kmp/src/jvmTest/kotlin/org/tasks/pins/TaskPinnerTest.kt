package org.tasks.pins

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.tasks.DatabaseTest
import org.tasks.constructor.Constructor
import org.tasks.constructor.FeatureGate
import org.tasks.data.TaskSaver
import org.tasks.data.createDueDate
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_LOCAL
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Task
import org.tasks.notifications.CancelReason
import org.tasks.notifications.Notifier
import org.tasks.preferences.AppPreferences
import org.tasks.repeats.SeriesScheduler
import org.tasks.repeats.SeriesTemplates
import org.tasks.service.TaskCleanup
import org.tasks.service.TaskDeleter
import org.tasks.time.DateTime

class TaskPinnerTest : DatabaseTest() {
    private val taskDao = db.taskDao()
    private val caldavDao = db.caldavDao()
    private val seriesDao = db.seriesDao()

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
        locationDao = db.locationDao(),
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

    private val scheduler = SeriesScheduler(
        seriesDao = seriesDao,
        taskDao = taskDao,
        taskSaver = taskSaver,
        taskDeleter = TaskDeleter(
            deletionDao = db.deletionDao(),
            taskDao = taskDao,
            caldavDao = caldavDao,
            refreshBroadcaster = mock(),
            vtodoCache = mock(),
            tasksPreferences = mock(),
            taskCleanup = object : TaskCleanup {},
        ),
        templates = templates,
        featureGate = FeatureGate(Constructor(repeat = true)),
        refreshBroadcaster = mock(),
        dirtyDao = db.dirtyDao(),
    )

    private val pinner = TaskPinner(
        taskDao = taskDao,
        seriesDao = seriesDao,
        templates = templates,
        refreshBroadcaster = mock(),
        notifier = notifier,
    )

    @Before
    fun setUp() = runBlocking {
        caldavDao.insert(CaldavAccount(uuid = "local", accountType = TYPE_LOCAL))
        caldavDao.insert(CaldavCalendar(uuid = LIST, account = "local"))
    }

    private suspend fun plain(): Task {
        val task = Task(title = "milk")
        taskDao.createNew(task)
        return task
    }

    /** A repeating task already adopted into a series. */
    private suspend fun repeating(recurrence: String = "FREQ=DAILY"): Task {
        val task = Task(title = "pill", dueDate = MON, recurrence = recurrence)
        taskDao.createNew(task)
        caldavDao.insert(task, CaldavTask(task = task.id, calendar = LIST, remoteId = null), false)
        scheduler.reconcile(SUN)
        return taskDao.fetch(task.id)!!
    }

    @Test
    fun pinMarksRowsAndRefreshesShade() = runBlocking {
        val task = plain()

        pinner.pin(listOf(task.id))

        assertEquals(listOf(task.id), taskDao.getPinned().map { it.id })
        assertEquals(1, refreshes)
    }

    @Test
    fun pinWholeSeriesFlagsTheTemplate() = runBlocking {
        val task = repeating()

        pinner.pin(listOf(task.id), wholeSeries = true)

        assertTrue(pinner.isSeriesPinned(task.series!!))
        assertTrue(taskDao.fetch(task.id)!!.isPinned)
    }

    @Test
    fun pinWholeSeriesPinsEveryOpenOccurrence() = runBlocking {
        val task = repeating()
        val series = seriesDao.getByUuid(task.series!!)!!
        val other = templates.emit(series, day(1), templates.decode(series.template))

        pinner.pin(listOf(task.id), wholeSeries = true)

        assertTrue(taskDao.fetch(other.id)!!.isPinned)
    }

    @Test
    fun unpinningAPinnedSeriesUnpinsEveryOpenOccurrence() = runBlocking {
        val task = repeating()
        val series = seriesDao.getByUuid(task.series!!)!!
        val other = templates.emit(series, day(1), templates.decode(series.template))
        pinner.pin(listOf(task.id), wholeSeries = true)

        pinner.unpin(listOf(task.id))

        assertFalse(taskDao.fetch(other.id)!!.isPinned)
    }

    @Test
    fun unpinningOneOfALooseSeriesLeavesItsSiblingsPinned() = runBlocking {
        val task = repeating()
        val series = seriesDao.getByUuid(task.series!!)!!
        val other = templates.emit(series, day(1), templates.decode(series.template))
        pinner.pin(listOf(task.id, other.id))

        pinner.unpin(listOf(task.id))

        assertTrue(taskDao.fetch(other.id)!!.isPinned)
    }

    @Test
    fun unpinClearsRowAndSeries() = runBlocking {
        val task = repeating()
        pinner.pin(listOf(task.id), wholeSeries = true)

        pinner.unpin(listOf(task.id))

        assertFalse(taskDao.fetch(task.id)!!.isPinned)
        assertFalse(pinner.isSeriesPinned(task.series!!))
        assertEquals(2, refreshes)
    }

    @Test
    fun aPinnedSeriesEmitsPinnedOccurrences() = runBlocking {
        val task = repeating()
        pinner.pin(listOf(task.id), wholeSeries = true)
        val series = seriesDao.getByUuid(task.series!!)!!

        val next = templates.emit(series, day(1), templates.decode(series.template))

        assertTrue(next.pinned > 0)
    }

    @Test
    fun anUnpinnedSeriesEmitsPlainOccurrences() = runBlocking {
        val task = repeating()
        pinner.pin(listOf(task.id))
        val series = seriesDao.getByUuid(task.series!!)!!

        val next = templates.emit(series, day(1), templates.decode(series.template))

        assertEquals(0L, next.pinned)
    }

    @Test
    fun editingTheScheduleKeepsTheSeriesPin() = runBlocking {
        val task = repeating(recurrence = "FREQ=WEEKLY;BYDAY=MO")
        pinner.pin(listOf(task.id), wholeSeries = true)

        scheduler.editSchedule(task.id, null, MON, SUN)

        assertTrue(pinner.isSeriesPinned(task.series!!))
    }

    companion object {
        private const val LIST = "local-list"

        private fun withTime(millis: Long) = createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, millis)

        private fun day(n: Int) = withTime(DateTime(2026, 5, 4, 9, 0).plusDays(n).millis)

        private val MON = day(0)
        private val SUN = withTime(DateTime(2026, 5, 3, 20, 0).millis)
    }
}
