package org.tasks.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.constructor.Constructor
import org.tasks.ui.TaskEditViewModel.Companion.TAG_DESCRIPTION
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