package org.tasks.constructor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.data.entity.Alarm
import org.tasks.time.ONE_DAY

class ConstructorTest {
    @Test
    fun allOffClearsCapabilityFlagsAndKeepsTheLocalListsToggle() {
        val next = Constructor(repeat = true, lists = true, localLists = true).withPreset(Preset.Simple)
        assertFalse(next.repeat)
        assertFalse(next.lists)
        assertTrue(next.localLists)
    }

    @Test
    fun allOnSetsCapabilityFlagsAndKeepsTheLocalListsToggle() {
        val next = Constructor(localLists = true).withPreset(Preset.Full)
        assertTrue(next.repeat)
        assertTrue(next.lists)
        assertTrue(next.geo)
        assertTrue(next.localLists)
    }

    @Test
    fun syncDoesNotTurnListsOn() {
        val raw = Constructor(localLists = true)
        assertFalse(raw.effective(sync = true, geofences = true).lists)
    }

    @Test
    fun theLocalBlockNeedsListsAndEitherNoSyncOrTheToggle() {
        fun shown(lists: Boolean, local: Boolean, sync: Boolean) =
            Constructor(lists = lists, localLists = local).effective(sync, true).localLists
        assertFalse(shown(lists = false, local = true, sync = false))
        assertTrue(shown(lists = true, local = false, sync = false))
        assertFalse(shown(lists = true, local = false, sync = true))
        assertTrue(shown(lists = true, local = true, sync = true))
    }

    @Test
    fun withoutTheLocalBlockASyncedStateKeepsEverythingInGoogle() {
        assertFalse(Constructor().keepsEverythingInGoogle(sync = false))
        assertTrue(Constructor().keepsEverythingInGoogle(sync = true))
        assertTrue(Constructor(lists = true).keepsEverythingInGoogle(sync = true))
        assertFalse(Constructor(lists = true, localLists = true).keepsEverythingInGoogle(sync = true))
        assertTrue(Constructor(localLists = true).keepsEverythingInGoogle(sync = true))
    }

    @Test
    fun aSetupKeepsTheLocalListsToggle() {
        assertTrue(Constructor(localLists = true).withPreset(Preset.Full).localLists)
        assertFalse(Constructor().withPreset(Preset.Full).localLists)
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
    fun anEarlyAlarmRingsWithAdvanceOrExtraReminders() {
        val alarm = Alarm(type = Alarm.TYPE_REL_END, time = -15 * 60_000L)
        assertFalse(Constructor().schedules(alarm))
        assertTrue(Constructor(advance = true).schedules(alarm))
        assertTrue(Constructor(extraReminders = true).schedules(alarm))
    }

    @Test
    fun aRepeatingEarlyAlarmStaysAnExtraReminder() {
        val alarm = Alarm(type = Alarm.TYPE_REL_END, time = -60_000, repeat = 2, interval = 60_000)
        assertFalse(Constructor(advance = true).schedules(alarm))
        assertTrue(Constructor(extraReminders = true).schedules(alarm))
    }

    @Test
    fun allOnTurnsAdvanceOnAndLeavesStartDateOff() {
        val full = Constructor().withPreset(Preset.Full)
        assertTrue(full.advance)
        assertFalse(full.startDate)
        assertFalse(full.extraReminders)
    }

    @Test
    fun allOnTurnsMemosOn() {
        assertFalse(Constructor().memos)
        assertTrue(Constructor().withPreset(Preset.Full).memos)
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
