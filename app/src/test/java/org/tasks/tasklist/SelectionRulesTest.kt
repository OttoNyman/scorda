package org.tasks.tasklist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.data.TaskContainer
import org.tasks.data.entity.Task

class SelectionRulesTest {
    private val plain = TaskContainer(task = Task(id = 1))
    private val occurrence = TaskContainer(task = Task(id = 2, series = "s"))
    private val doneOccurrence = TaskContainer(task = Task(id = 3, series = "s", completionDate = 1L))

    @Test
    fun noSeriesAmongTheSelectedHidesFinishSeries() {
        assertFalse(selectionHasOpenSeries(listOf(plain, occurrence), setOf(plain.id)))
    }

    @Test
    fun anOpenOccurrenceAmongTheSelectedShowsFinishSeries() {
        assertTrue(selectionHasOpenSeries(listOf(plain, occurrence), setOf(plain.id, occurrence.id)))
    }

    @Test
    fun aDoneOccurrenceDoesNotCount() {
        assertFalse(selectionHasOpenSeries(listOf(doneOccurrence), setOf(doneOccurrence.id)))
    }

    @Test
    fun swipeOnAFinishedTaskGoesToTrash() {
        assertTrue(swipeTrashes(onCompletedScreen = false, doneOccurrence))
    }

    @Test
    fun swipeOnAnOpenTaskTogglesDone() {
        assertFalse(swipeTrashes(onCompletedScreen = false, plain))
    }

    @Test
    fun swipeOnTheCompletedScreenGoesToTrash() {
        assertTrue(swipeTrashes(onCompletedScreen = true, plain))
    }

    @Test
    fun swipeOnANoteGoesToTrash() {
        val note = TaskContainer(task = Task(id = 4, memo = true))
        assertTrue(swipeTrashes(onCompletedScreen = false, note))
    }
}
