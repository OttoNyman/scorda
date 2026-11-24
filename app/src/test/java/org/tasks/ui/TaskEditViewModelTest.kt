package org.tasks.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.constructor.Constructor
import org.tasks.ui.TaskEditViewModel.Companion.TAG_DESCRIPTION
import org.tasks.ui.TaskEditViewModel.Companion.reopenOnEdit
import org.tasks.ui.TaskEditViewModel.Reopen
import org.tasks.ui.TaskEditViewModel.Companion.TAG_DUE_DATE
import org.tasks.ui.TaskEditViewModel.Companion.TAG_LIST
import org.tasks.ui.TaskEditViewModel.Companion.stripCarriageReturns

class TaskEditViewModelTest {
    @Test
    fun replaceCRLF() {
        assertEquals("aaa\nbbb", "aaa\r\nbbb".stripCarriageReturns())
    }

    @Test
    fun replaceCR() {
        assertEquals("aaa\nbbb", "aaa\rbbb".stripCarriageReturns())
    }

    @Test
    fun dontReplaceLF() {
        assertEquals("aaa\nbbb", "aaa\nbbb".stripCarriageReturns())
    }

    @Test
    fun stripCarriageReturnOnNull() {
        assertNull((null as String?).stripCarriageReturns())
    }

    @Test
    fun placeTasksHideDueDateAndShowLocationOnlyWhenUnscheduled() {
        val order = TaskEditViewModel.displayOrder(
            Constructor(geo = true, advance = true, repeat = true),
            memo = false,
            hasLocation = true,
        )
        assertFalse(order.contains(TaskEditViewModel.TAG_DUE_DATE))
        assertTrue(order.contains(org.tasks.ui.LocationControlSet.TAG))
        val timed = TaskEditViewModel.displayOrder(Constructor(geo = true), memo = false, hasSchedule = true)
        assertFalse(timed.contains(org.tasks.ui.LocationControlSet.TAG))
        assertTrue(timed.contains(TaskEditViewModel.TAG_DUE_DATE))
    }

    @Test
    fun whenBothLocationAndSchedulePlaceWinsInDisplayOrder() {
        val order = TaskEditViewModel.displayOrder(
            Constructor(geo = true, advance = true, repeat = true),
            memo = false,
            hasLocation = true,
            hasSchedule = true,
        )
        assertTrue(order.contains(org.tasks.ui.LocationControlSet.TAG))
        assertFalse(order.contains(TaskEditViewModel.TAG_DUE_DATE))
        assertFalse(order.contains(TaskEditViewModel.TAG_ADVANCE))
        assertFalse(order.contains(com.todoroo.astrid.repeats.RepeatControlSet.TAG))
    }

    @Test
    fun aTaskKeepsItsExistingDescriptionWhenNotesAreOff() {
        val order = TaskEditViewModel.displayOrder(Constructor(notes = false), memo = false, hasNotes = true)

        assertTrue(order.contains(TAG_DESCRIPTION))
    }

    @Test
    fun aTaskWithoutADescriptionGetsNoFieldWhenNotesAreOff() {
        val order = TaskEditViewModel.displayOrder(Constructor(notes = false), memo = false, hasNotes = false)

        assertFalse(order.contains(TAG_DESCRIPTION))
    }

    @Test
    fun editingACompletedTaskClearsIt() {
        assertEquals(Reopen.Clear, reopenOnEdit(completed = true, series = false, declined = false))
    }

    @Test
    fun editingACompletedSeriesAsks() {
        assertEquals(Reopen.Ask, reopenOnEdit(completed = true, series = true, declined = false))
    }

    @Test
    fun aDeclinedSeriesStaysCompleted() {
        assertEquals(Reopen.None, reopenOnEdit(completed = true, series = true, declined = true))
    }

    @Test
    fun anOpenTaskAndAMemoStayAsTheyAre() {
        assertEquals(Reopen.None, reopenOnEdit(completed = false, series = false, declined = false))
        assertEquals(Reopen.None, reopenOnEdit(completed = true, series = false, declined = false, memo = true))
        assertEquals(Reopen.None, reopenOnEdit(completed = true, series = true, declined = false, readOnly = true))
    }

    @Test
    fun memoDisplayOrderShowsDescriptionWhenConstructorNotesOff() {
        val order = TaskEditViewModel.displayOrder(
            Constructor(notes = false, tags = true, priority = true, files = true),
            memo = true,
        )
        assertTrue(order.contains(TAG_DESCRIPTION))
        assertFalse(order.contains(TAG_DUE_DATE))
        assertFalse(order.contains(TAG_LIST))
    }
}