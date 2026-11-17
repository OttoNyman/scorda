package org.tasks.repeats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.data.createDueDate
import org.tasks.data.entity.Task
import org.tasks.time.DateTime

class SeriesClockTest {
    private fun withTZ(id: String, block: () -> Unit) {
        val default = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(id))
            block()
        } finally {
            java.util.TimeZone.setDefault(default)
        }
    }

    private fun withTime(millis: Long) = createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, millis)

    private fun dateOnly(millis: Long) = createDueDate(Task.URGENCY_SPECIFIC_DAY, millis)

    @Test
    fun dueMomentWithTimeIsItself() {
        val scheduled = withTime(DateTime(2026, 5, 4, 9, 0).millis)
        assertEquals(scheduled, SeriesClock.dueMoment(scheduled))
    }

    @Test
    fun dueMomentDateOnlyIsNextMidnight() {
        val scheduled = dateOnly(DateTime(2026, 5, 4).millis)
        assertEquals(DateTime(2026, 5, 5).millis, SeriesClock.dueMoment(scheduled))
    }

    @Test
    fun nextDaily() {
        val after = withTime(DateTime(2026, 5, 4, 9, 0).millis)
        val next = SeriesClock.next("FREQ=DAILY", after)
        assertNotNull(next)
        assertEquals(withTime(DateTime(2026, 5, 5, 9, 0).millis), next!!.scheduled)
        assertEquals("FREQ=DAILY", next.recurrence)
    }

    @Test
    fun countRunsOut() {
        val after = withTime(DateTime(2026, 5, 4, 9, 0).millis)
        val first = SeriesClock.next("FREQ=DAILY;COUNT=2", after)
        assertNotNull(first)
        assertEquals(1, Recur.parse(first!!.recurrence).count)
        assertNull(SeriesClock.next(first.recurrence, first.scheduled))
    }

    @Test
    fun untilInPastIsNull() {
        val after = withTime(DateTime(2026, 5, 4, 9, 0).millis)
        assertNull(SeriesClock.next("FREQ=DAILY;UNTIL=20200101", after))
    }

    @Test
    fun dateOnlyDailyAcrossDst() = withTZ("Europe/Kyiv") {
        val after = dateOnly(DateTime(2026, 3, 28).millis)
        val next = SeriesClock.next("FREQ=DAILY", after)!!
        assertFalse(Task.hasDueTime(next.scheduled))
        assertEquals(dateOnly(DateTime(2026, 3, 29).millis), next.scheduled)
        assertTrue(next.scheduled > after)
    }

    @Test
    fun rebaseSingleWeekday() {
        // 2026-05-05 is a Tuesday
        val rebased = SeriesClock.rebase(
            "FREQ=WEEKLY;BYDAY=MO",
            from = DateTime(2026, 5, 4).millis,
            to = DateTime(2026, 5, 5).millis,
        )
        assertEquals(listOf(ByDay(Weekday.TU)), Recur.parse(rebased).byDay)
    }

    @Test
    fun rebaseMultiWeekdayUnchanged() {
        val rule = "FREQ=WEEKLY;BYDAY=MO,WE"
        assertEquals(
            rule,
            SeriesClock.rebase(rule, DateTime(2026, 5, 4).millis, DateTime(2026, 5, 5).millis),
        )
    }

    @Test
    fun rebaseSingleMonthDay() {
        val rebased = SeriesClock.rebase(
            "FREQ=MONTHLY;BYMONTHDAY=4",
            from = DateTime(2026, 5, 4).millis,
            to = DateTime(2026, 5, 17).millis,
        )
        assertEquals(listOf(17), Recur.parse(rebased).byMonthDay)
    }
}
