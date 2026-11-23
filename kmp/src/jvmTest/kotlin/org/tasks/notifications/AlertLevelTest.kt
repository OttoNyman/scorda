package org.tasks.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.data.entity.Task

class AlertLevelTest {
    @Test
    fun priorityMapsToLevelsAndBack() {
        assertEquals(AlertLevel.SUPER, alertLevelOf(Task.Priority.HIGH))
        assertEquals(AlertLevel.PERSISTENT, alertLevelOf(Task.Priority.MEDIUM))
        assertEquals(AlertLevel.ONCE, alertLevelOf(Task.Priority.LOW))
        assertEquals(AlertLevel.SILENT, alertLevelOf(Task.Priority.NONE))
        assertEquals(Task.Priority.HIGH, priorityOf(AlertLevel.SUPER))
        assertEquals(Task.Priority.MEDIUM, priorityOf(AlertLevel.PERSISTENT))
        assertEquals(Task.Priority.LOW, priorityOf(AlertLevel.ONCE))
        assertEquals(Task.Priority.NONE, priorityOf(AlertLevel.SILENT))
    }

    @Test
    fun aMemoNeverRings() {
        assertNull(alertLevel(Task(memo = true, priority = Task.Priority.HIGH), enabledLevels()))
    }

    @Test
    fun aDayWithoutATimeIsQuietWhateverItsPriority() {
        val day = 86_400_000L * 10
        assertEquals(
            AlertLevel.SILENT,
            alertLevel(Task(dueDate = day, priority = Task.Priority.HIGH), enabledLevels()),
        )
        assertEquals(
            AlertLevel.SUPER,
            alertLevel(Task(dueDate = day + 1_000, priority = Task.Priority.HIGH), enabledLevels()),
        )
    }

    @Test
    fun aHiddenLevelRingsAsOnce() {
        val enabled = enabledLevels(silent = false, persistent = false, alarm = true)
        assertEquals(
            AlertLevel.ONCE,
            alertLevel(Task(priority = Task.Priority.MEDIUM), enabled),
        )
        assertEquals(
            AlertLevel.ONCE,
            alertLevel(Task(priority = Task.Priority.NONE), enabled),
        )
        assertEquals(
            AlertLevel.SUPER,
            alertLevel(Task(priority = Task.Priority.HIGH), enabled),
        )
    }

    @Test
    fun enabledLevelsAlwaysIncludeOnce() {
        assertEquals(setOf(AlertLevel.ONCE), enabledLevels(false, false, false))
        assertEquals(
            setOf(AlertLevel.ONCE, AlertLevel.SILENT, AlertLevel.PERSISTENT, AlertLevel.SUPER),
            enabledLevels(true, true, true),
        )
        assertTrue(AlertLevel.ONCE in enabledLevels(alarm = true))
    }

    @Test
    fun silentNeverMakesASound() {
        assertEquals(Route.NONE, route(AlertLevel.SILENT, state()))
        assertFalse(vibrate(AlertLevel.SILENT, state()))
        assertNull(nextEpisodeDelay(AlertLevel.SILENT, 0))
    }

    @Test
    fun aCallUsesTheVoiceTone() {
        val calling = state(inCall = true, superBypassesSilent = true)
        assertEquals(Route.CALL_TONE, route(AlertLevel.ONCE, calling))
        assertEquals(Route.CALL_TONE, route(AlertLevel.SUPER, calling))
        assertFalse(vibrate(AlertLevel.SUPER, calling))
    }

    @Test
    fun superIsSilentWhenTheRingerIsMutedUnlessAsked() {
        val muted = state(ringerNormal = false)
        assertEquals(Route.NONE, route(AlertLevel.SUPER, muted))
        assertTrue(vibrate(AlertLevel.SUPER, muted))
        assertEquals(Route.ALARM, route(AlertLevel.SUPER, muted.copy(superBypassesSilent = true)))
    }

    @Test
    fun onceAndPersistentUseTheNotificationStream() {
        assertEquals(Route.NOTIFICATION, route(AlertLevel.ONCE, state()))
        assertEquals(Route.NOTIFICATION, route(AlertLevel.PERSISTENT, state()))
        assertTrue(vibrate(AlertLevel.ONCE, state()))
        assertFalse(vibrate(AlertLevel.ONCE, state(vibrateAllowed = false)))
    }

    @Test
    fun persistentBacksOffThenStops() {
        val pauses = (0..5).map { nextEpisodeDelay(AlertLevel.PERSISTENT, it)!! / 60_000L }
        assertEquals(listOf(1L, 2, 4, 8, 15, 30), pauses)
        assertNull(nextEpisodeDelay(AlertLevel.PERSISTENT, 6))
        assertNull(nextEpisodeDelay(AlertLevel.ONCE, 0))
    }

    @Test
    fun alarmStartsEveryFiveMinutesForHalfAnHour() {
        repeat(6) { index ->
            assertEquals(SUPER_INTERVAL_MS, nextEpisodeDelay(AlertLevel.SUPER, index))
        }
        assertNull(nextEpisodeDelay(AlertLevel.SUPER, 6))
    }

    @Test
    fun alarmRingDurationSnapsToFiveSeconds() {
        assertEquals(20_000L, clampAlarmRingMs(19_000L))
        assertEquals(60_000L, clampAlarmRingMs(61_000L))
        assertEquals(45_000L, clampAlarmRingMs(47_000L))
    }

    private fun state(
        inCall: Boolean = false,
        ringerNormal: Boolean = true,
        superBypassesSilent: Boolean = false,
        vibrateAllowed: Boolean = true,
    ) = SoundState(inCall, ringerNormal, superBypassesSilent, vibrateAllowed)
}
