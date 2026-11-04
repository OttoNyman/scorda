package org.tasks.googleapis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.tasks.data.createDueDate
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

        val packed = GoogleTaskNotes.pack(task.notes, task, 8192)
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

        assertEquals("только текст", GoogleTaskNotes.pack(task.notes, task, 8192))
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
}
