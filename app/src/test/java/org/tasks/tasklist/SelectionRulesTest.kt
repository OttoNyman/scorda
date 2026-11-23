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
}
