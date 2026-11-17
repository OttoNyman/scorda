package org.tasks.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Alarm.Companion.TYPE_REL_END
import org.tasks.time.ONE_DAY
import org.tasks.time.ONE_MINUTE

class AdvanceTest {
    @Test
    fun replacesOnlyTheAdvanceClosestToTheTime() {
        val due = Alarm(type = TYPE_REL_END, time = 0)
        val near = Alarm(type = TYPE_REL_END, time = -5 * ONE_MINUTE)
        val far = Alarm(type = TYPE_REL_END, time = -ONE_DAY)
        val nag = Alarm(type = TYPE_REL_END, time = -ONE_MINUTE, repeat = 2, interval = ONE_MINUTE)

        val result = listOf(due, near, far, nag).withAdvance(30 * ONE_MINUTE)

        assertEquals(setOf(due, far, nag, Alarm(type = TYPE_REL_END, time = -30 * ONE_MINUTE)), result.toSet())
    }

    @Test
    fun nullRemovesTheAdvance() {
        assertEquals(emptyList<Alarm>(), listOf(Alarm(type = TYPE_REL_END, time = -15 * ONE_MINUTE)).withAdvance(null))
    }

    @Test
    fun turningAdvanceOnOffersNoneByDefault() {
        assertNull(listOf(Alarm(type = TYPE_REL_END, time = 0)).forAdvanceToggle(wasOn = false, isOn = true))
    }

    @Test
    fun turningAdvanceOffDropsTheDefault() {
        val due = Alarm(type = TYPE_REL_END, time = 0)
        assertEquals(
            listOf(due),
            listOf(due, Alarm(type = TYPE_REL_END, time = -15 * ONE_MINUTE)).forAdvanceToggle(wasOn = true, isOn = false),
        )
        assertNull(listOf(due).forAdvanceToggle(wasOn = true, isOn = true))
    }

    @Test
    fun aDefaultAdvanceNeverJoinsAnAdvanceTheTaskHas() {
        val chosen = listOf(Alarm(type = TYPE_REL_END, time = -30 * ONE_MINUTE))
        assertFalse(chosen.acceptsDefault(Alarm(type = TYPE_REL_END, time = -15 * ONE_MINUTE)))
        assertTrue(chosen.acceptsDefault(Alarm(type = TYPE_REL_END, time = 0)))
        assertTrue(emptyList<Alarm>().acceptsDefault(Alarm(type = TYPE_REL_END, time = -15 * ONE_MINUTE)))
    }

    @Test
    fun aRepeatingEarlyAlarmIsNotTheAdvance() {
        assertNull(listOf(Alarm(type = TYPE_REL_END, time = -ONE_MINUTE, repeat = 1, interval = ONE_MINUTE)).advance())
    }
}
