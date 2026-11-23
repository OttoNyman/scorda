package com.todoroo.astrid.alarms

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.tasks.DatabaseTest
import org.tasks.constructor.Constructor
import org.tasks.constructor.FeatureGate
import org.tasks.data.createDueDate
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Task
import org.tasks.preferences.AppPreferences
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.reminders.Random

/** A task's time is its reminder: it rings then even with no reminder stored for it. */
class ImplicitDueReminderTest : DatabaseTest() {
    private val alarmDao = db.alarmDao()
    private val taskDao = db.taskDao()

    private var allDayReminders = true

    private val preferences: AppPreferences = mock {
        onBlocking { defaultDueTime() } doReturn 9 * 60 * 60 * 1000
        onBlocking { isDefaultDueTimeEnabled() } doAnswer { allDayReminders }
    }

    // «О просрочке» off, as on the owner's phone
    private val alarmService = AlarmService(
        alarmDao = alarmDao,
        taskDao = taskDao,
        dirtyDao = db.dirtyDao(),
        refreshBroadcaster = mock(),
        notifier = mock(),
        alarmCalculator = AlarmCalculator(Random(), FeatureGate(Constructor())),
        preferences = preferences,
    )

    private val due = createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, currentTimeMillis() + ONE_HOUR)

    private suspend fun newTask(
        dueDate: Long = due,
        completed: Boolean = false,
        memo: Boolean = false,
    ): Task {
        val task = Task(
            title = "t",
            dueDate = dueDate,
            completionDate = if (completed) currentTimeMillis() else 0,
            memo = memo,
        )
        taskDao.createNew(task)
        return task
    }

    private suspend fun future() = alarmService.alarmsDueBefore(currentTimeMillis()).second

    @Test
    fun aTaskWithATimeRingsAtItWithNoStoredReminder() = runBlocking {
        val task = newTask()

        assertEquals(listOf(task.id to due), future().map { it.taskId to it.timestamp })
    }

    @Test
    fun anOverdueOnlyReminderSwitchedOffStillLeavesTheDueRing() = runBlocking {
        val task = newTask()
        alarmDao.insert(Alarm.whenOverdue(task.id))

        assertEquals(listOf(task.id to due), future().map { it.taskId to it.timestamp })
    }

    @Test
    fun aStoredDueReminderIsNotDoubled() = runBlocking {
        val task = newTask()
        alarmDao.insert(Alarm.whenDue(task.id))

        assertEquals(listOf(task.id to due), future().map { it.taskId to it.timestamp })
    }

    @Test
    fun noTimeNoDoneNoMemoNoRing() = runBlocking {
        newTask(dueDate = 0)
        newTask(completed = true)
        newTask(memo = true)

        assertTrue(future().isEmpty())
    }

    @Test
    fun anAllDayTaskRingsAtTheDefaultTimeWhenAllDayRemindersAreOn() = runBlocking {
        val tomorrow = createDueDate(Task.URGENCY_SPECIFIC_DAY, currentTimeMillis() + ONE_DAY)
        val task = newTask(dueDate = tomorrow)

        assertEquals(listOf(task.id), future().map { it.taskId })
    }

    @Test
    fun anAllDayTaskStaysQuietWhenAllDayRemindersAreOff() = runBlocking {
        allDayReminders = false
        newTask(dueDate = createDueDate(Task.URGENCY_SPECIFIC_DAY, currentTimeMillis() + ONE_DAY))

        assertTrue(future().isEmpty())
    }

    companion object {
        private const val ONE_HOUR = 60 * 60 * 1000L
        private const val ONE_DAY = 24 * ONE_HOUR
    }
}
