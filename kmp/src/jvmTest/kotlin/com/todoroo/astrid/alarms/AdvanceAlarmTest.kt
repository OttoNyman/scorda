package com.todoroo.astrid.alarms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.tasks.data.createDueDate
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Alarm.Companion.TYPE_REL_END
import org.tasks.data.entity.Task
import org.tasks.reminders.Random
import org.tasks.time.ONE_MINUTE

class AdvanceAlarmTest {
    private val calculator = AlarmCalculator(Random())
    private val now = 1_800_000_000_000L
    private val early = Alarm(task = 1, type = TYPE_REL_END, time = -15 * ONE_MINUTE)

    private fun task(dueIn: Long) = Task(
        id = 1,
        dueDate = createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, now + dueIn),
        creationDate = now,
        modificationDate = now,
    )

    @Test
    fun anEarlyRingWhoseMomentPassedBeforeTheTaskWasSavedNeverSounds() {
        assertNull(calculator.toAlarmEntry(task(dueIn = 10 * ONE_MINUTE), early, 0))
    }

    @Test
    fun anEarlyRingStillAheadSounds() {
        val task = task(dueIn = 60 * ONE_MINUTE)
        assertEquals(task.dueDate - 15 * ONE_MINUTE, calculator.toAlarmEntry(task, early, 0)?.timestamp)
    }
}
