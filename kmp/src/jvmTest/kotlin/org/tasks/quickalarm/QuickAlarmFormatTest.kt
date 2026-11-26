package org.tasks.quickalarm

import org.junit.Assert.assertEquals
import org.junit.Test
import org.tasks.time.DateTime

class QuickAlarmFormatTest {
    @Test
    fun formatsHoursWithinDayCap() {
        assertEquals("+00:00", formatQuickAlarmOffset(0))
        assertEquals("+00:05", formatQuickAlarmOffset(5))
        assertEquals("+01:30", formatQuickAlarmOffset(90))
        assertEquals("+24:00", formatQuickAlarmOffset(QUICK_ALARM_MAX_MINUTES))
        assertEquals("+24:00", formatQuickAlarmOffset(26 * 60))
        assertEquals("+00:00", formatQuickAlarmOffset(-3))
    }

    @Test
    fun dueDropsSecondsThenAddsMinutes() {
        val now = DateTime(2026, 11, 26, 12, 0, 59, 900).millis
        val expected = DateTime(2026, 11, 26, 12, 1, 0, 0).millis
        assertEquals(expected, quickAlarmDueAtMillis(now, 1))
    }

    @Test
    fun addPresetAccumulatesAndCapsAt24h() {
        assertEquals(16, addQuickAlarmPreset(1, 15))
        assertEquals(61, addQuickAlarmPreset(1, 60))
        assertEquals(QUICK_ALARM_MAX_MINUTES, addQuickAlarmPreset(23 * 60, 60))
        assertEquals(QUICK_ALARM_MAX_MINUTES, addQuickAlarmPreset(QUICK_ALARM_MAX_MINUTES, 1))
    }
}
