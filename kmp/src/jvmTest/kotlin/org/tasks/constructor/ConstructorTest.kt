package org.tasks.constructor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.data.entity.Alarm
import org.tasks.time.ONE_DAY

class ConstructorTest {
    @Test
    fun presetsMatchTheirFlags() {
        assertEquals(Preset.Simple, matchingPreset(Preset.Simple.flags, false, true, true))
        assertEquals(Preset.Reminders, matchingPreset(Preset.Reminders.flags, false, true, true))
        assertEquals(Preset.Tasks, matchingPreset(Preset.Tasks.flags, false, true, true))
        assertEquals(Preset.Full, matchingPreset(Preset.Full.flags, false, true, true))
    }

    @Test
    fun repeatAloneMatchesNothing() {
        assertNull(matchingPreset(Constructor(repeat = true), false, true, true))
    }

    @Test
    fun fullStillMatchesWhenThePlatformHasNoGeofences() {
        assertEquals(Preset.Full, matchingPreset(Preset.Full.flags, false, geofences = false, calendarEvents = true))
    }

    @Test
    fun anAccountTurnsListsOnWithoutWritingTheRawFlag() {
        val raw = Constructor()
        val effective = raw.effective(hasSyncAccount = true, geofences = true, calendarEvents = true)
        assertFalse(raw.lists)
        assertTrue(effective.lists)
        assertNull(matchingPreset(raw, hasSyncAccount = true, geofences = true, calendarEvents = true))
    }

    @Test
    fun snoozeAlwaysSchedules() {
        val simple = Constructor()
        assertTrue(simple.schedules(Alarm(type = Alarm.TYPE_SNOOZE, time = 1)))
    }

    @Test
    fun geoRequiresTheToggle() {
        val alarm = Alarm(type = Alarm.TYPE_GEO_ENTER)
        assertFalse(Constructor().schedules(alarm))
        assertTrue(Constructor(geo = true).schedules(alarm))
    }

    @Test
    fun aDueAlarmWithNoRepeatIsTheCore() {
        val alarm = Alarm(type = Alarm.TYPE_REL_END, time = 0, repeat = 0)
        assertTrue(Constructor().schedules(alarm))
    }

    @Test
    fun overdueNeedsItsToggle() {
        val alarm = Alarm(type = Alarm.TYPE_REL_END, time = ONE_DAY, repeat = 6, interval = ONE_DAY)
        assertFalse(Constructor().schedules(alarm))
        assertTrue(Constructor(overdue = true).schedules(alarm))
        assertFalse(Constructor(extraReminders = true).schedules(alarm))
    }

    @Test
    fun anEarlyAlarmIsAnExtraReminder() {
        val alarm = Alarm(type = Alarm.TYPE_REL_END, time = -60_000)
        assertFalse(Constructor(overdue = true).schedules(alarm))
        assertTrue(Constructor(extraReminders = true).schedules(alarm))
    }

    @Test
    fun repeatingAtTheDueTimeIsAnExtraReminder() {
        val alarm = Alarm(type = Alarm.TYPE_REL_END, time = 0, repeat = 3, interval = 60_000)
        assertFalse(Constructor(overdue = true).schedules(alarm))
        assertTrue(Constructor(extraReminders = true).schedules(alarm))
    }

    @Test
    fun startRelativeAlarmsNeedTheStartDate() {
        val alarm = Alarm(type = Alarm.TYPE_REL_START)
        assertFalse(Constructor(extraReminders = true).schedules(alarm))
        assertTrue(Constructor(startDate = true).schedules(alarm))
    }

    @Test
    fun randomAndAbsoluteAlarmsNeedExtraReminders() {
        assertFalse(Constructor().schedules(Alarm(type = Alarm.TYPE_RANDOM, time = 1)))
        assertTrue(Constructor(extraReminders = true).schedules(Alarm(type = Alarm.TYPE_RANDOM, time = 1)))
        assertFalse(Constructor().schedules(Alarm(type = Alarm.TYPE_DATE_TIME, time = 1)))
        assertTrue(Constructor(extraReminders = true).schedules(Alarm(type = Alarm.TYPE_DATE_TIME, time = 1)))
    }
}
