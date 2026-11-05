package org.tasks.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.data.entity.Task

class AlertLevelTest {
    @Test
    fun flagsMapToLevelsAndBack() {
        assertEquals(AlertLevel.SILENT, alertLevel(Task.NOTIFY_MODE_SILENT))
        assertEquals(AlertLevel.PERSISTENT, alertLevel(Task.NOTIFY_MODE_FIVE))
        assertEquals(AlertLevel.SUPER, alertLevel(Task.NOTIFY_MODE_NONSTOP))
        assertEquals(AlertLevel.ONCE, alertLevel(0))
        assertEquals(Task.NOTIFY_MODE_SILENT, ringFlags(AlertLevel.SILENT))
        assertEquals(0, ringFlags(AlertLevel.ONCE))
        assertEquals(Task.NOTIFY_MODE_FIVE, ringFlags(AlertLevel.PERSISTENT))
        assertEquals(Task.NOTIFY_MODE_NONSTOP, ringFlags(AlertLevel.SUPER))
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
    fun persistentRepeatsTwiceThenStops() {
        assertEquals(PERSISTENT_INTERVAL_MS, nextEpisodeDelay(AlertLevel.PERSISTENT, 0))
        assertEquals(PERSISTENT_INTERVAL_MS, nextEpisodeDelay(AlertLevel.PERSISTENT, 1))
        assertNull(nextEpisodeDelay(AlertLevel.PERSISTENT, 2))
        assertNull(nextEpisodeDelay(AlertLevel.ONCE, 0))
    }

    @Test
    fun superWaitsOutTheRingAndThePause() {
        assertEquals(SUPER_RING_MS + SUPER_PAUSE_MS, nextEpisodeDelay(AlertLevel.SUPER, 0))
        assertEquals(SUPER_RING_MS + SUPER_PAUSE_MS, nextEpisodeDelay(AlertLevel.SUPER, 1))
        assertNull(nextEpisodeDelay(AlertLevel.SUPER, 2))
    }

    @Test
    fun aBatchFollowsTheLoudestDelay() {
        assertEquals(
            SUPER_RING_MS + SUPER_PAUSE_MS,
            nextBatchDelay(listOf(AlertLevel.PERSISTENT, AlertLevel.SUPER), 0),
        )
        assertEquals(
            PERSISTENT_INTERVAL_MS,
            nextBatchDelay(listOf(AlertLevel.PERSISTENT, AlertLevel.ONCE), 0),
        )
        assertNull(nextBatchDelay(listOf(AlertLevel.ONCE), 0))
    }

    private fun state(
        inCall: Boolean = false,
        ringerNormal: Boolean = true,
        superBypassesSilent: Boolean = false,
        vibrateAllowed: Boolean = true,
    ) = SoundState(inCall, ringerNormal, superBypassesSilent, vibrateAllowed)
}
