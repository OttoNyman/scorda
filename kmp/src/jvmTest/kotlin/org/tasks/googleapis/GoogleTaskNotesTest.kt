package org.tasks.googleapis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.data.createDueDate
import org.tasks.data.entity.Series
import org.tasks.data.entity.Task
import org.tasks.data.entity.Task.Priority
import org.tasks.date.DateTimeUtils.newDateTime
import org.tasks.time.DateTime

class GoogleTaskNotesTest {
    @Test
    fun roundTripKeepsTheHumanNoteAndTheClock() {
        val task = Task(
            notes = "купить молоко",
            dueDate = createDueDate(
                Task.URGENCY_SPECIFIC_DAY_TIME,
                DateTime(2026, 11, 4, 15, 30).millis,
            ),
            priority = Priority.HIGH,
            recurrence = "FREQ=DAILY",
            hideUntil = DateTime(2026, 11, 4, 9, 0).millis,
        )

        val packed = GoogleTaskNotes.pack(task.notes, task, null, 8192)
        val (human, extra) = GoogleTaskNotes.unpack(packed)

        assertEquals("купить молоко", human)
        assertEquals(15 * 60 + 30, extra!!.dueMin)
        assertEquals(Priority.HIGH, extra.priority)
        assertEquals("FREQ=DAILY", extra.recur)
        assertEquals(task.hideUntil, extra.start)
    }

    @Test
    fun aPlainNoteStaysAPlainNote() {
        val task = Task(notes = "только текст", priority = Priority.NONE)

        assertEquals("только текст", GoogleTaskNotes.pack(task.notes, task, null, 8192))
        assertNull(GoogleTaskNotes.unpack("только текст").second)
    }

    @Test
    fun aBrokenTrailerStaysInTheNote() {
        val notes = "текст\n§scorda {not json"

        assertEquals(notes to null, GoogleTaskNotes.unpack(notes))
    }

    @Test
    fun applyRestoresTheClockOntoGooglesDate() {
        val day = createDueDate(
            Task.URGENCY_SPECIFIC_DAY,
            DateTime(2026, 11, 4, 0, 0).millis,
        )
        val task = Task(dueDate = day, priority = Priority.NONE)
        GoogleTaskNotes.apply(
            task,
            GoogleTaskNotes.Extra(dueMin = 15 * 60 + 30, priority = Priority.LOW),
        )

        val due = newDateTime(task.dueDate)
        assertEquals(15, due.hourOfDay)
        assertEquals(30, due.minuteOfHour)
        assertEquals(Priority.LOW, task.priority)
    }

    @Test
    fun packUnpackSeriesFields() {
        val scheduled = createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, DateTime(2026, 11, 4, 9, 0).millis)
        val series = Series(
            uuid = "series-1",
            recurrence = "FREQ=WEEKLY;BYDAY=MO",
            repeatFrom = Task.RepeatFrom.COMPLETION_DATE,
            anchor = scheduled,
            template = "{}",
            created = 1_000L,
            modified = 2_000L,
        )
        val task = Task(notes = "заметка", dueDate = scheduled, series = "series-1", scheduled = scheduled)

        val (human, extra) = GoogleTaskNotes.unpack(GoogleTaskNotes.pack(task.notes, task, series, 8192))

        assertEquals("заметка", human)
        assertEquals("series-1", extra!!.ser)
        assertEquals(scheduled, extra.sch)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", extra.rule)
        assertEquals(Task.RepeatFrom.COMPLETION_DATE, extra.from)
        assertEquals(1_000L, extra.sc)
        assertEquals(2_000L, extra.sm)
        assertNull(extra.recur)

        val incoming = Task(dueDate = createDueDate(Task.URGENCY_SPECIFIC_DAY, scheduled))
        GoogleTaskNotes.apply(incoming, extra)
        assertEquals("series-1", incoming.series)
        assertEquals(scheduled, incoming.scheduled)
        assertNull(incoming.recurrence)
    }

    @Test
    fun legacyRecurStillApplies() {
        val task = Task(priority = Priority.NONE)

        GoogleTaskNotes.apply(task, GoogleTaskNotes.Extra(recur = "FREQ=DAILY"))

        assertEquals("FREQ=DAILY", task.recurrence)
        assertNull(task.series)
        assertEquals(0L, task.scheduled)
    }

    @Test
    fun oldRecurTrailerDoesNotUndoAnOccurrence() {
        val task = Task(priority = Priority.NONE, series = "series-1", scheduled = 42L)

        GoogleTaskNotes.apply(task, GoogleTaskNotes.Extra(recur = "FREQ=DAILY"))

        assertEquals("series-1", task.series)
        assertEquals(42L, task.scheduled)
        assertNull(task.recurrence)
    }

    @Test
    fun seriesOrSlotChangePushesTheTrailer() {
        val original = Task(title = "water", series = "series-1", scheduled = 42L)

        assertTrue(original.copy().googleTaskUpToDate(original))
        assertFalse(original.copy(series = null).googleTaskUpToDate(original))
        assertFalse(original.copy(scheduled = 43L).googleTaskUpToDate(original))
    }
}
