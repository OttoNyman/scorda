package org.tasks.repeats

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.tasks.DatabaseTest
import org.tasks.constructor.Constructor
import org.tasks.constructor.FeatureGate
import org.tasks.data.TaskSaver
import org.tasks.data.createDueDate
import org.tasks.data.dao.DeletionDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_GOOGLE_TASKS
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_LOCAL
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Series
import org.tasks.data.entity.Task
import org.tasks.preferences.AppPreferences
import org.tasks.service.TaskCleanup
import org.tasks.service.TaskDeleter
import org.tasks.time.DateTime

class SeriesSchedulerTest : DatabaseTest() {
    private val taskDao = db.taskDao()
    private val caldavDao = db.caldavDao()
    private val seriesDao = db.seriesDao()
    private val deletionDao = db.deletionDao()
    private val dirtyDao = db.dirtyDao()

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

    private val taskDeleter = TaskDeleter(
        deletionDao = deletionDao,
        taskDao = taskDao,
        caldavDao = caldavDao,
        refreshBroadcaster = mock(),
        vtodoCache = mock(),
        tasksPreferences = mock(),
        taskCleanup = object : TaskCleanup {},
    )

    private val gate = FeatureGate(Constructor(repeat = true))

    private val scheduler = SeriesScheduler(
        seriesDao = seriesDao,
        taskDao = taskDao,
        taskSaver = taskSaver,
        taskDeleter = taskDeleter,
        templates = templates,
        featureGate = gate,
        refreshBroadcaster = mock(),
        dirtyDao = dirtyDao,
    )

    @Before
    fun setUp() = runBlocking {
        caldavDao.insert(CaldavAccount(uuid = "local", accountType = TYPE_LOCAL))
        caldavDao.insert(CaldavCalendar(uuid = LIST, account = "local"))
    }

    private suspend fun legacy(
        recurrence: String = "FREQ=DAILY",
        due: Long = MON,
        repeatFrom: Int = Task.RepeatFrom.DUE_DATE,
    ): Task {
        val task = Task(title = "water", dueDate = due, recurrence = recurrence, repeatFrom = repeatFrom)
        taskDao.createNew(task)
        caldavDao.insert(task, CaldavTask(task = task.id, calendar = LIST, remoteId = null), false)
        return task
    }

    private suspend fun fetch(task: Task) = taskDao.fetch(task.id)!!

    private suspend fun uuid(task: Task) = fetch(task).series!!

    private suspend fun series(task: Task): Series = seriesDao.getByUuid(uuid(task))!!

    private suspend fun scheduled(uuid: String) =
        seriesDao.getOccurrences(uuid).map { it.scheduled }.sorted()

    private suspend fun complete(task: Task, at: Long) {
        taskDao.update(fetch(task).copy(completionDate = at))
    }

    @Test
    fun adoptLegacyRowBecomesFirstOccurrence() = runBlocking {
        val row = legacy()

        scheduler.reconcile(SUN)

        val adopted = fetch(row)
        assertNotNull(adopted.series)
        assertNull(adopted.recurrence)
        assertEquals(MON, adopted.scheduled)
        assertEquals(1, seriesDao.getActive().size)
        val series = series(row)
        assertEquals(MON, series.anchor)
        assertEquals("FREQ=DAILY", series.recurrence)
        assertEquals(SUN, series.created)
        assertEquals(listOf(MON), scheduled(series.uuid))
    }

    @Test
    fun adoptDueLessRowSetsTodayFirst() = runBlocking {
        val row = legacy(due = 0)

        scheduler.reconcile(SUN)

        val today = createDueDate(Task.URGENCY_SPECIFIC_DAY, SUN)
        assertEquals(today, fetch(row).dueDate)
        assertEquals(today, fetch(row).scheduled)
        assertEquals(today, series(row).anchor)
    }

    @Test
    fun unparseableRuleIsLeftAlone() = runBlocking {
        val row = legacy(recurrence = "garbage")

        scheduler.reconcile(SUN)

        assertNull(fetch(row).series)
        assertEquals("garbage", fetch(row).recurrence)
        assertTrue(seriesDao.getActive().isEmpty())
    }

    @Test
    fun exhaustedRuleIsAdoptedAndFinishesAtItsMoment() = runBlocking {
        val row = legacy(recurrence = "FREQ=DAILY;COUNT=1")

        scheduler.reconcile(SUN)

        assertNotNull(fetch(row).series)
        assertEquals(0L, series(row).finished)

        scheduler.reconcile(MON)

        assertTrue(series(row).finished > 0)
        assertEquals(0L, fetch(row).deletionDate)
        assertEquals(listOf(MON), scheduled(uuid(row)))
    }

    @Test
    fun overdueLegacyRowSurvivesAdoption() = runBlocking {
        val row = legacy()

        scheduler.reconcile(day(3))

        val kept = fetch(row)
        assertEquals(0L, kept.deletionDate)
        assertEquals(MON, kept.dueDate)
        assertEquals(day(3), kept.scheduled)
        assertEquals(listOf(day(3), day(4)), scheduled(uuid(row)))
        assertEquals(day(4), series(row).anchor)

        // postponed: never absorbed
        scheduler.reconcile(day(6))
        assertEquals(0L, fetch(row).deletionDate)
    }

    @Test
    fun adoptionRollsBackWithoutItsRow() = runBlocking {
        val failing: TaskSaver = mock {
            onBlocking { save(any(), anyOrNull(), any(), any()) } doThrow IllegalStateException("crash")
        }
        val crashing = SeriesScheduler(seriesDao, taskDao, failing, taskDeleter, templates, gate, mock())
        val row = legacy()

        // the failure is logged and skipped, but must not leave a series behind
        crashing.reconcile(SUN)

        assertTrue(seriesDao.getActive().isEmpty())
        assertNull(fetch(row).series)
    }

    @Test
    fun oneBrokenLegacyRowDoesNotBlockTheOthers() = runBlocking {
        val bad = legacy()
        val good = legacy()
        val brokenCapture: SeriesTemplates = mock {
            onBlocking { capture(bad.id) } doThrow IllegalStateException("broken")
            onBlocking { capture(good.id) } doReturn SeriesTemplate(title = "water")
            on { encode(any()) } doReturn "{}"
        }
        val partial = SeriesScheduler(seriesDao, taskDao, taskSaver, taskDeleter, brokenCapture, gate, mock())

        partial.reconcile(SUN)

        assertNull(fetch(bad).series)
        assertEquals("FREQ=DAILY", fetch(bad).recurrence)
        assertNotNull(fetch(good).series)
    }

    @Test
    fun oneBrokenSeriesDoesNotStopTheOthers() = runBlocking {
        // inserted first, so it is reconciled first; its template cannot be decoded
        seriesDao.insert(
            Series(uuid = "broken", recurrence = "FREQ=DAILY", anchor = MON, template = "not json", created = 1, modified = 1)
        )
        val row = legacy()
        scheduler.reconcile(SUN)

        scheduler.reconcile(MON)

        assertEquals(listOf(MON, day(1)), scheduled(uuid(row)))
        assertEquals(MON, seriesDao.getByUuid("broken")!!.anchor)
    }

    @Test
    fun dateOnlySeriesEmitsAtMidnight() = runBlocking {
        val monday = createDueDate(Task.URGENCY_SPECIFIC_DAY, MON)
        val row = legacy(due = monday)
        scheduler.reconcile(SUN)

        scheduler.reconcile(DateTime(2026, 5, 4, 23, 59).millis)
        assertEquals(listOf(monday), scheduled(uuid(row)))

        scheduler.reconcile(DateTime(2026, 5, 5).millis)
        val tuesday = createDueDate(Task.URGENCY_SPECIFIC_DAY, DateTime(2026, 5, 5).millis)
        assertEquals(listOf(monday, tuesday), scheduled(uuid(row)))
    }

    @Test
    fun nextAppearsWhenPreviousComesDue() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)

        val wake = scheduler.reconcile(MON)

        assertEquals(listOf(MON, day(1)), scheduled(uuid(row)))
        assertEquals(day(1), wake)
    }

    @Test
    fun missedAbsorbedWhenSuccessorComesDue() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        scheduler.reconcile(MON)

        scheduler.reconcile(day(1))

        assertEquals(DeletionDao.DISCARDED, fetch(row).deletionDate)
        assertEquals(listOf(day(1), day(2)), scheduled(uuid(row)))
    }

    @Test
    fun postponedSurvives() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        taskDao.update(fetch(row).copy(dueDate = day(4)))

        scheduler.reconcile(MON)
        scheduler.reconcile(day(1))
        scheduler.reconcile(day(2))

        assertEquals(0L, fetch(row).deletionDate)
        assertEquals(listOf(MON, day(2), day(3)), scheduled(uuid(row)))
    }

    @Test
    fun completingEarlyEmitsNext() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        complete(row, SUN)

        scheduler.reconcile(SUN)

        assertEquals(DeletionDao.DISCARDED, fetch(row).deletionDate)
        assertEquals(listOf(day(1)), scheduled(uuid(row)))
    }

    @Test
    fun completedOccurrenceNeverInTrash() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        complete(row, SUN)

        scheduler.reconcile(SUN)

        assertTrue(deletionDao.getTrash().isEmpty())
    }

    @Test
    fun catchUpAfterTenDaysLeavesTwo() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)

        scheduler.reconcile(day(10))

        assertEquals(listOf(day(10), day(11)), scheduled(uuid(row)))
        // skipped occurrences are never created
        assertEquals(3, taskDao.getAll().size)
    }

    @Test
    fun emitIsIdempotentPerScheduledTime() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)

        scheduler.reconcile(MON)
        scheduler.reconcile(MON)
        // a stale anchor (another writer, a crash before the update) must not emit Tue twice
        seriesDao.update(series(row).copy(anchor = MON))
        scheduler.reconcile(MON)

        val all = taskDao.getAll().filter { it.series == uuid(row) }.map { it.scheduled }
        assertEquals(all.distinct(), all)
        assertEquals(listOf(MON, day(1)), scheduled(uuid(row)))
    }

    @Test
    fun exhaustedSeriesFinishes() = runBlocking {
        val row = legacy(recurrence = "FREQ=DAILY;COUNT=2")
        scheduler.reconcile(SUN)
        scheduler.reconcile(MON)
        assertEquals(listOf(MON, day(1)), scheduled(uuid(row)))

        assertEquals(0L, scheduler.reconcile(day(1)))

        assertTrue(series(row).finished > 0)
        assertEquals(listOf(day(1)), scheduled(uuid(row)))
        assertEquals(0L, scheduler.reconcile(day(5)))
        assertEquals(listOf(day(1)), scheduled(uuid(row)))
    }

    @Test
    fun completionDateModeWaitsForCompletion() = runBlocking {
        val row = legacy(repeatFrom = Task.RepeatFrom.COMPLETION_DATE)
        scheduler.reconcile(SUN)

        assertEquals(0L, scheduler.reconcile(day(3)))
        assertEquals(listOf(MON), scheduled(uuid(row)))

        val completedAt = DateTime(2026, 5, 7, 14, 0).millis
        complete(row, completedAt)
        scheduler.reconcile(completedAt)

        val expected = withTime(DateTime(2026, 5, 8, 9, 0).millis)
        assertEquals(expected, SeriesClock.nextAfterCompletion("FREQ=DAILY", MON, completedAt)!!.scheduled)
        assertEquals(listOf(expected), scheduled(uuid(row)))
        assertEquals(expected, series(row).anchor)
    }

    @Test
    fun repeatCapabilityOffDoesNothing() = runBlocking {
        gate.current = Constructor(repeat = false)
        val row = legacy()

        assertEquals(0L, scheduler.reconcile(day(3)))

        assertNull(fetch(row).series)
        assertEquals("FREQ=DAILY", fetch(row).recurrence)
        assertTrue(seriesDao.getActive().isEmpty())
    }

    @Test
    fun finishKeepsOneCompletedMarker() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        scheduler.reconcile(MON)
        val uuid = uuid(row)
        val now = MON + HOUR

        scheduler.finish(uuid, now)

        val visible = seriesDao.getOccurrences(uuid)
        assertEquals(listOf(day(1)), visible.map { it.scheduled })
        assertEquals(now, visible.single().completionDate)
        assertEquals(DeletionDao.DISCARDED, fetch(row).deletionDate)
        assertEquals(now, seriesDao.getByUuid(uuid)!!.finished)
        assertEquals(now, seriesDao.getByUuid(uuid)!!.modified)
        assertTrue(deletionDao.getTrash().isEmpty())
        assertEquals(0L, scheduler.reconcile(day(5)))
        assertEquals(listOf(day(1)), scheduled(uuid))
    }

    @Test
    fun reviveRestartsFromNow() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        scheduler.reconcile(MON)
        val uuid = uuid(row)
        scheduler.finish(uuid, MON + HOUR)
        val marker = seriesDao.getOccurrences(uuid).single()

        assertTrue(scheduler.revive(marker.id, day(3) + 3 * HOUR))

        val revived = taskDao.fetch(marker.id)!!
        assertEquals(0L, revived.completionDate)
        assertEquals(day(4), revived.scheduled)
        assertEquals(day(4), revived.dueDate)
        val series = seriesDao.getByUuid(uuid)!!
        assertEquals(0L, series.finished)
        assertEquals(day(4), series.anchor)
    }

    @Test
    fun editScheduleWithNewDateRebases() = runBlocking {
        val row = legacy(recurrence = "FREQ=WEEKLY;BYDAY=MO")
        scheduler.reconcile(SUN)
        val tue10 = withTime(DateTime(2026, 5, 5, 10, 0).millis)
        taskDao.update(fetch(row).copy(dueDate = tue10))

        scheduler.editSchedule(row.id, null, MON, SUN)

        val series = series(row)
        assertEquals(listOf(ByDay(Weekday.TU)), Recur.parse(series.recurrence).byDay)
        assertEquals(tue10, series.anchor)
        assertEquals(tue10, fetch(row).scheduled)
        assertEquals(SUN, series.modified)
    }

    @Test
    fun editScheduleReEmitsNotYetDueOccurrence() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        scheduler.reconcile(MON)
        val uuid = uuid(row)
        val oldTuesday = seriesDao.findOccurrence(uuid, day(1))!!
        taskDao.update(fetch(row).copy(title = "feed"))

        scheduler.editSchedule(row.id, null, MON, MON + HOUR)

        assertEquals(DeletionDao.DISCARDED, taskDao.fetch(oldTuesday.id)!!.deletionDate)
        val tuesday = seriesDao.findOccurrence(uuid, day(1))!!
        assertEquals("feed", tuesday.title)
        assertEquals(listOf(MON, day(1)), scheduled(uuid))
        assertEquals(day(1), seriesDao.getByUuid(uuid)!!.anchor)
    }

    @Test
    fun plainEditOfAdoptedOverdueRowKeepsIt() = runBlocking {
        val row = legacy(recurrence = "FREQ=DAILY;COUNT=10")
        scheduler.reconcile(day(3))
        val before = series(row)
        taskDao.update(fetch(row).copy(title = "feed"))

        scheduler.editSchedule(row.id, null, MON, day(3) + HOUR)

        val kept = fetch(row)
        assertEquals(0L, kept.deletionDate)
        assertEquals(MON, kept.dueDate)
        assertEquals(day(3), kept.scheduled)
        val after = series(row)
        assertEquals(before.recurrence, after.recurrence)
        assertEquals(day(4), after.anchor)
        assertEquals(listOf(day(3), day(4)), scheduled(uuid(row)))
        assertEquals("feed", seriesDao.findOccurrence(uuid(row), day(4))!!.title)
    }

    @Test
    fun editScheduleKeepsTheFullCount() = runBlocking {
        val row = legacy(recurrence = "FREQ=DAILY;COUNT=3")
        scheduler.reconcile(SUN)
        scheduler.reconcile(MON)
        taskDao.update(fetch(row).copy(title = "feed"))

        scheduler.editSchedule(row.id, null, MON, MON + HOUR)
        scheduler.reconcile(day(1))

        assertEquals(0L, series(row).finished)
        assertEquals(listOf(day(1), day(2)), scheduled(uuid(row)))

        scheduler.reconcile(day(2))

        assertTrue(series(row).finished > 0)
        assertEquals(listOf(day(2)), scheduled(uuid(row)))
    }

    @Test
    fun editScheduleTakesAGivenRuleAsIs() = runBlocking {
        val row = legacy(recurrence = "FREQ=WEEKLY;BYDAY=MO")
        scheduler.reconcile(SUN)
        val tue10 = withTime(DateTime(2026, 5, 5, 10, 0).millis)
        taskDao.update(fetch(row).copy(dueDate = tue10))

        scheduler.editSchedule(row.id, "FREQ=WEEKLY;BYDAY=WE", MON, SUN)

        val series = series(row)
        assertEquals(listOf(ByDay(Weekday.WE)), Recur.parse(series.recurrence).byDay)
        assertEquals(tue10, series.anchor)
    }

    @Test
    fun ruleChangeReachesTheNotYetDueOccurrence() = runBlocking {
        val row = legacy(recurrence = "FREQ=WEEKLY;BYDAY=MO")
        scheduler.reconcile(SUN)
        scheduler.reconcile(MON)
        val uuid = uuid(row)
        val nextMonday = seriesDao.findOccurrence(uuid, day(7))!!

        scheduler.editSchedule(row.id, "FREQ=WEEKLY;BYDAY=WE", MON, DateTime(2026, 5, 5, 12, 0).millis)

        assertEquals(DeletionDao.DISCARDED, taskDao.fetch(nextMonday.id)!!.deletionDate)
        assertEquals(0L, fetch(row).deletionDate)
        assertEquals(MON, fetch(row).scheduled)
        assertEquals(listOf(MON, day(2)), scheduled(uuid))
        val series = series(row)
        assertEquals("FREQ=WEEKLY;BYDAY=WE", series.recurrence)
        assertEquals(day(2), series.anchor)
    }

    @Test
    fun ruleChangeOnTheNotYetDueRowItself() = runBlocking {
        val row = legacy(recurrence = "FREQ=WEEKLY;BYDAY=MO")
        scheduler.reconcile(SUN)
        scheduler.reconcile(MON)
        val uuid = uuid(row)
        val nextMonday = seriesDao.findOccurrence(uuid, day(7))!!
        taskDao.update(nextMonday.copy(title = "feed"))

        scheduler.editSchedule(nextMonday.id, "FREQ=WEEKLY;BYDAY=WE", day(7), DateTime(2026, 5, 5, 12, 0).millis)

        assertEquals(DeletionDao.DISCARDED, taskDao.fetch(nextMonday.id)!!.deletionDate)
        assertEquals(listOf(MON, day(2)), scheduled(uuid))
        assertEquals("feed", seriesDao.findOccurrence(uuid, day(2))!!.title)
    }

    /**
     * Once a slot of the new rule has passed, the series looks as if it always had that rule:
     * that slot is the current (missed) occurrence and the old one is absorbed by it.
     */
    @Test
    fun ruleChangeAfterANewSlotHasPassed() = runBlocking {
        val row = legacy(recurrence = "FREQ=WEEKLY;BYDAY=MO")
        scheduler.reconcile(SUN)
        scheduler.reconcile(MON)
        val uuid = uuid(row)

        scheduler.editSchedule(row.id, "FREQ=WEEKLY;BYDAY=WE", MON, DateTime(2026, 5, 9, 12, 0).millis)

        assertEquals(listOf(day(2), day(9)), scheduled(uuid))
        assertEquals(DeletionDao.DISCARDED, fetch(row).deletionDate)
    }

    /** Completed occurrences are gone, so usually no visible occurrence has come yet. */
    @Test
    fun ruleChangeWithNoOccurrenceThatHasCome() = runBlocking {
        val row = legacy(recurrence = "FREQ=WEEKLY;BYDAY=MO")
        scheduler.reconcile(SUN)
        complete(row, SUN)
        scheduler.reconcile(SUN)
        val uuid = uuid(row)
        assertEquals(listOf(day(7)), scheduled(uuid))
        val nextMonday = seriesDao.findOccurrence(uuid, day(7))!!

        scheduler.editSchedule(nextMonday.id, "FREQ=WEEKLY;BYDAY=WE", day(7), DateTime(2026, 5, 5, 12, 0).millis)

        assertEquals(DeletionDao.DISCARDED, taskDao.fetch(nextMonday.id)!!.deletionDate)
        // first Wednesday after the edit, at the schedule's time of day
        assertEquals(listOf(day(2)), scheduled(uuid))
        val series = seriesDao.getByUuid(uuid)!!
        assertEquals("FREQ=WEEKLY;BYDAY=WE", series.recurrence)
        assertEquals(day(2), series.anchor)
    }

    @Test
    fun ruleChangeOnAFreshSeriesWithOnlyAFutureRow() = runBlocking {
        val row = legacy(recurrence = "FREQ=WEEKLY;BYDAY=MO;COUNT=3", due = day(7))
        scheduler.reconcile(SUN)
        val uuid = uuid(row)

        scheduler.editSchedule(row.id, "FREQ=WEEKLY;BYDAY=WE;COUNT=3", day(7), SUN)

        assertEquals(DeletionDao.DISCARDED, fetch(row).deletionDate)
        assertEquals(listOf(day(2)), scheduled(uuid))
        val series = seriesDao.getByUuid(uuid)!!
        assertEquals(day(2), series.anchor)
        // the discarded Monday is refunded, the Wednesday that replaces it is charged
        assertEquals(3, Recur.parse(series.recurrence).count)
    }

    @Test
    fun ruleChangeKeepsTheFullCount() = runBlocking {
        val row = legacy(recurrence = "FREQ=WEEKLY;BYDAY=MO;COUNT=3")
        scheduler.reconcile(SUN)
        scheduler.reconcile(MON)
        val uuid = uuid(row)
        assertEquals(2, Recur.parse(series(row).recurrence).count)

        // the stored COUNT comes back from the editor: Mon 4, Wed 6 and Wed 13 make three
        scheduler.editSchedule(row.id, "FREQ=WEEKLY;BYDAY=WE;COUNT=2", MON, DateTime(2026, 5, 5, 12, 0).millis)
        assertEquals(listOf(MON, day(2)), scheduled(uuid))

        scheduler.reconcile(day(2))
        assertEquals(0L, seriesDao.getByUuid(uuid)!!.finished)
        assertEquals(listOf(day(2), day(9)), scheduled(uuid))

        scheduler.reconcile(day(9))
        assertTrue(seriesDao.getByUuid(uuid)!!.finished > 0)
        assertEquals(listOf(day(9)), scheduled(uuid))
    }

    @Test
    fun sameRuleIsNotARuleChange() = runBlocking {
        val row = legacy(recurrence = "FREQ=WEEKLY;BYDAY=MO;COUNT=5")
        scheduler.reconcile(SUN)
        scheduler.reconcile(MON)
        val uuid = uuid(row)
        val before = series(row)
        val nextMonday = seriesDao.findOccurrence(uuid, day(7))!!

        scheduler.editSchedule(nextMonday.id, before.recurrence, day(7), DateTime(2026, 5, 5, 12, 0).millis)

        assertEquals(0L, taskDao.fetch(nextMonday.id)!!.deletionDate)
        assertEquals(listOf(row.id, nextMonday.id), seriesDao.getOccurrences(uuid).map { it.id }.sorted())
        val after = series(row)
        assertEquals(before.recurrence, after.recurrence)
        assertEquals(day(7), after.anchor)
    }

    @Test
    fun occurrenceMovedIntoThePastIsNotAbsorbed() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        scheduler.reconcile(day(3))
        val uuid = uuid(row)
        assertEquals(listOf(day(3), day(4)), scheduled(uuid))
        val friday = seriesDao.findOccurrence(uuid, day(4))!!
        val monday10 = MON + HOUR
        taskDao.update(friday.copy(dueDate = monday10))

        scheduler.editSchedule(friday.id, null, day(4), day(3) + 3 * HOUR)

        // takes the latest slot of the moved schedule that has come, keeps the date the user set
        val kept = taskDao.fetch(friday.id)!!
        assertEquals(0L, kept.deletionDate)
        assertEquals(monday10, kept.dueDate)
        assertEquals(day(3) + HOUR, kept.scheduled)
        assertEquals(day(4) + HOUR, seriesDao.getByUuid(uuid)!!.anchor)
        assertEquals(listOf(day(3), day(3) + HOUR, day(4) + HOUR), scheduled(uuid))

        scheduler.reconcile(day(6))
        assertEquals(0L, taskDao.fetch(friday.id)!!.deletionDate)
    }

    @Test
    fun occurrenceScopeDateChangeIsPostpone() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        val original = fetch(row)
        // "Only this occurrence": a plain save, no editSchedule
        taskSaver.save(original.copy(dueDate = day(4)), original)

        scheduler.reconcile(day(1))

        val kept = fetch(row)
        assertEquals(0L, kept.deletionDate)
        assertEquals(MON, kept.scheduled)
        assertEquals(day(4), kept.dueDate)
    }

    @Test
    fun editScheduleTakesANewRepeatFrom() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)

        scheduler.editSchedule(row.id, null, MON, SUN, Task.RepeatFrom.COMPLETION_DATE)

        assertEquals(Task.RepeatFrom.COMPLETION_DATE, series(row).repeatFrom)
    }

    @Test
    fun detachLeavesAPlainTask() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        scheduler.reconcile(MON)
        val uuid = uuid(row)

        scheduler.detach(row.id, MON + HOUR)

        assertNull(fetch(row).series)
        assertEquals(0L, fetch(row).scheduled)
        assertEquals(0L, fetch(row).deletionDate)
        assertTrue(seriesDao.getOccurrences(uuid).isEmpty())
        assertTrue(seriesDao.getByUuid(uuid)!!.finished > 0)
    }

    /** An editor opened before adoption saves its whole-row snapshot afterwards. */
    @Test
    fun staleSaveFromBeforeAdoptionKeepsTheSeries() = runBlocking {
        val stale = legacy()
        scheduler.reconcile(SUN)
        val adopted = fetch(stale)

        taskDao.update(stale.copy(title = "feed"), original = stale.copy())

        val saved = fetch(stale)
        assertEquals(adopted.series, saved.series)
        assertEquals(adopted.scheduled, saved.scheduled)
        assertNull(saved.recurrence)
        assertEquals("feed", saved.title)
        scheduler.reconcile(SUN)
        assertEquals(1, seriesDao.getAll().size)
    }

    @Test
    fun goneListTakesTheListOfTheNewestLiveOccurrence() = runBlocking {
        caldavDao.insert(CaldavCalendar(uuid = OTHER_LIST, account = "local"))
        val row = legacy()
        scheduler.reconcile(SUN)
        // its tasks moved to another list, then the list went
        caldavDao.reassignCalendar(LIST, OTHER_LIST)
        taskDeleter.delete(caldavDao.getCalendarByUuid(LIST)!!)

        scheduler.reconcile(MON)

        val next = seriesDao.findOccurrence(uuid(row), day(1))!!
        assertEquals(OTHER_LIST, caldavDao.getTask(next.id)!!.calendar)
        assertEquals(OTHER_LIST, templates.decode(series(row).template).list)
    }

    @Test
    fun goneListWithNoLiveOccurrenceFinishesTheSeries() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        val uuid = uuid(row)
        taskDeleter.delete(caldavDao.getCalendarByUuid(LIST)!!)

        scheduler.reconcile(MON)

        assertTrue(seriesDao.getByUuid(uuid)!!.finished > 0)
        assertTrue(scheduled(uuid).isEmpty())
    }

    @Test
    fun ruleChangeBeforeTodaysOccurrenceKeepsIt() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        val uuid = uuid(row)

        scheduler.editSchedule(row.id, "FREQ=DAILY;INTERVAL=2", MON, MON - HOUR)

        assertEquals(listOf(MON), scheduled(uuid))
        assertEquals(MON, seriesDao.getByUuid(uuid)!!.anchor)
    }

    @Test
    fun snoozedOccurrenceIsPostponedAndOutlivesItsSuccessor() = runBlocking {
        val row = legacy()
        scheduler.reconcile(MON)

        scheduler.postpone(listOf(row.id), day(1) + HOUR)
        scheduler.reconcile(day(1))

        val snoozed = fetch(row)
        assertEquals(0L, snoozed.deletionDate)
        assertEquals(day(1) + HOUR, snoozed.dueDate)
        assertEquals(MON, snoozed.scheduled)
    }

    @Test
    fun snoozeNeverPullsAnOccurrenceEarlier() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)

        scheduler.postpone(listOf(row.id), MON - HOUR)

        assertEquals(MON, fetch(row).dueDate)
    }

    @Test
    fun subDayRuleWithoutTimeStartsNowInsteadOfLookingPostponed() = runBlocking {
        val row = legacy(recurrence = "FREQ=MINUTELY;INTERVAL=15", due = 0)

        scheduler.reconcile(MON - HOUR)

        val adopted = fetch(row)
        assertTrue(Task.hasDueTime(adopted.dueDate))
        assertEquals(adopted.dueDate, adopted.scheduled)
    }

    @Test
    fun ruleChangeAfterEarlyCompletionDoesNotBringTodayBack() = runBlocking {
        val row = legacy()
        scheduler.reconcile(SUN)
        val uuid = uuid(row)
        complete(row, MON - 2 * HOUR)
        scheduler.reconcile(MON - 2 * HOUR)
        val tue = seriesDao.findOccurrence(uuid, day(1))!!

        scheduler.editSchedule(tue.id, "FREQ=DAILY;INTERVAL=2", day(1), MON - HOUR)

        assertEquals(listOf(day(2)), scheduled(uuid))
    }

    /** An occurrence as the Google fetch writes it: series and slot from the `§scorda` trailer. */
    private suspend fun incoming(uuid: String, scheduled: Long, title: String = "water"): Task {
        val task = Task(title = title, dueDate = scheduled, series = uuid, scheduled = scheduled)
        taskDao.createNew(task)
        caldavDao.insert(task, CaldavTask(task = task.id, calendar = LIST, remoteId = null), false)
        return task
    }

    @Test
    fun restoreCreatesSeriesOnce() = runBlocking {
        val tue = incoming("remote", day(1))
        val mon = incoming("remote", MON)

        scheduler.restore(tue, "FREQ=DAILY", Task.RepeatFrom.DUE_DATE, created = 100L, modified = 200L)
        scheduler.restore(mon, "FREQ=DAILY", Task.RepeatFrom.DUE_DATE, created = 100L, modified = 200L)

        val restored = seriesDao.getByUuid("remote")!!
        assertEquals(listOf(restored), seriesDao.getActive())
        assertEquals("FREQ=DAILY", restored.recurrence)
        assertEquals(day(1), restored.anchor)
        assertEquals(100L, restored.created)
        assertEquals(200L, restored.modified)
        assertEquals("water", templates.decode(restored.template).title)

        // the restored series goes on from the rows that came in, without duplicating them
        scheduler.reconcile(day(1))
        assertEquals(listOf(day(1), day(2)), scheduled("remote"))
    }

    @Test
    fun newerScheduleWins() = runBlocking {
        val first = incoming("remote", MON)
        scheduler.restore(first, "FREQ=DAILY", Task.RepeatFrom.DUE_DATE, created = 100L, modified = 200L)
        val later = incoming("remote", day(2))

        // older schedule: ignored, but the slot that came in is not emitted again
        scheduler.restore(later, "FREQ=WEEKLY", Task.RepeatFrom.COMPLETION_DATE, created = 100L, modified = 150L)
        assertEquals("FREQ=DAILY", seriesDao.getByUuid("remote")!!.recurrence)
        assertEquals(day(2), seriesDao.getByUuid("remote")!!.anchor)

        scheduler.restore(later, "FREQ=WEEKLY", Task.RepeatFrom.COMPLETION_DATE, created = 100L, modified = 300L)
        val newer = seriesDao.getByUuid("remote")!!
        assertEquals("FREQ=WEEKLY", newer.recurrence)
        assertEquals(Task.RepeatFrom.COMPLETION_DATE, newer.repeatFrom)
        assertEquals(300L, newer.modified)
        assertEquals(day(2), newer.anchor)

        // the anchor never goes back
        scheduler.restore(first, "FREQ=DAILY", Task.RepeatFrom.DUE_DATE, created = 100L, modified = 400L)
        assertEquals(day(2), seriesDao.getByUuid("remote")!!.anchor)
        assertEquals("FREQ=DAILY", seriesDao.getByUuid("remote")!!.recurrence)
    }

    @Test
    fun completedRowDoesNotRestoreItsSeries() = runBlocking {
        val marker = incoming("remote", MON)
        taskDao.update(fetch(marker).copy(completionDate = MON))

        scheduler.restore(fetch(marker), "FREQ=DAILY", Task.RepeatFrom.DUE_DATE, created = 100L, modified = 200L)

        assertNull(seriesDao.getByUuid("remote"))
    }

    @Test
    fun googleOccurrencePushesAdoptionAndAScheduleOnlyEdit() = runBlocking {
        caldavDao.insert(CaldavAccount(uuid = "google", accountType = TYPE_GOOGLE_TASKS))
        caldavDao.insert(CaldavCalendar(uuid = GOOGLE_LIST, account = "google"))
        val task = Task(title = "water", dueDate = MON, recurrence = "FREQ=DAILY")
        taskDao.createNew(task)
        caldavDao.insert(task, CaldavTask(task = task.id, calendar = GOOGLE_LIST, remoteId = "r1"), false)
        suspend fun pending() = dirtyDao.getTasksToPush(GOOGLE_LIST)
        suspend fun pushAll() = pending().forEach { dirtyDao.markPushed(it.caldavTaskId, it.dirtyVersion) }
        pushAll()

        // adoption changes only series and scheduled: the trailer must still go
        scheduler.reconcile(SUN)
        assertEquals(listOf(task.id), pending().map { it.task.id })
        pushAll()

        // repeatFrom-only edit: no row changes, the new `from`/`sm` must still go
        scheduler.editSchedule(task.id, null, MON, SUN, Task.RepeatFrom.COMPLETION_DATE)
        assertEquals(listOf(task.id), pending().map { it.task.id })
    }

    companion object {
        private const val LIST = "local-list"
        private const val OTHER_LIST = "other-list"
        private const val GOOGLE_LIST = "google-list"
        private const val HOUR = 60L * 60 * 1000

        private fun withTime(millis: Long) = createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, millis)

        /** Monday 2026-05-04 09:00 plus [n] days. */
        private fun day(n: Int) = withTime(DateTime(2026, 5, 4, 9, 0).plusDays(n).millis)

        private val MON = day(0)
        private val SUN = withTime(DateTime(2026, 5, 3, 20, 0).millis)
    }
}
