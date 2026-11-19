package org.tasks.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderHealthTest {
    @Test
    fun stockReadyShowsExactWithoutASafetyPoll() {
        val facts = facts(sdk = 34, canExact = true)

        val checks = visibleChecks(facts)

        assertEquals(CheckState.OK, checks.state(ReminderCheckId.NOTIFICATIONS))
        assertEquals(CheckState.OK, checks.state(ReminderCheckId.EXACT_ALARMS))
        assertEquals(CheckState.OK, checks.state(ReminderCheckId.BATTERY))
        assertFalse(checks.any { it.id == ReminderCheckId.POWER_SAVE })
        assertEquals(SchedulePlan(ScheduleMode.EXACT, safetyPoll = false), schedulePlan(facts))
        assertEquals(ModeLine.ON_TIME, modeLine(facts))
    }

    @Test
    fun android11HasNoExactRowAndStillUsesExactAlarms() {
        val facts = facts(sdk = 30, canExact = false)

        assertFalse(visibleChecks(facts).any { it.id == ReminderCheckId.EXACT_ALARMS })
        assertEquals(SchedulePlan(ScheduleMode.EXACT, safetyPoll = false), schedulePlan(facts))
    }

    @Test
    fun stockWithoutExactAlarmsPollsAndWarns() {
        val facts = facts(
            sdk = 34,
            canExact = false,
            batteryUnrestricted = false,
            powerSaveOn = true,
        )
        val checks = visibleChecks(facts)

        assertEquals(CheckState.PROBLEM, checks.state(ReminderCheckId.EXACT_ALARMS))
        assertEquals(CheckState.PROBLEM, checks.state(ReminderCheckId.BATTERY))
        assertEquals(CheckState.PROBLEM, checks.state(ReminderCheckId.POWER_SAVE))
        assertEquals(SchedulePlan(ScheduleMode.INEXACT, safetyPoll = true), schedulePlan(facts))
        assertEquals(ModeLine.INEXACT, modeLine(facts))
    }

    @Test
    fun blockedChannelIsANotificationProblem() {
        val checks = visibleChecks(facts(notificationsEnabled = true, channelBlocked = true))

        assertEquals(CheckState.PROBLEM, checks.state(ReminderCheckId.NOTIFICATIONS))
    }

    @Test
    fun xiaomiKeepsTheSafetyPollEvenWithExactAlarms() {
        assertEquals(OemFamily.XIAOMI, oemFamily("Xiaomi"))
        val facts = facts(oem = OemFamily.XIAOMI, canExact = true)
        val ids = visibleChecks(facts).map { it.id }

        assertTrue(ReminderCheckId.AUTOSTART in ids)
        assertTrue(ReminderCheckId.BACKGROUND in ids)
        assertTrue(ReminderCheckId.POPUPS in ids)
        assertTrue(ReminderCheckId.RECENTS_LOCK in ids)
        assertTrue(schedulePlan(facts).safetyPoll)
        assertEquals(ModeLine.OEM_SAFETY, modeLine(facts))
    }

    @Test
    fun aggressiveNamesShareAFamily() {
        assertEquals(OemFamily.HUAWEI, oemFamily("Honor"))
        assertEquals(OemFamily.OPPO, oemFamily("realme"))
        assertEquals(OemFamily.OPPO, oemFamily("OnePlus"))
        assertEquals(OemFamily.VIVO, oemFamily("iQOO"))
    }

    @Test
    fun samsungIsStock() {
        assertEquals(OemFamily.STOCK, oemFamily("samsung"))
        val ids = visibleChecks(facts(oem = OemFamily.STOCK)).map { it.id }
        assertFalse(ReminderCheckId.AUTOSTART in ids)
        assertFalse(ReminderCheckId.POPUPS in ids)
    }

    @Test
    fun xiaomiWithoutExactAlarmsCombinesBothWarnings() {
        val facts = facts(oem = OemFamily.XIAOMI, canExact = false)

        assertEquals(ModeLine.OEM_AND_INEXACT, modeLine(facts))
    }

    @Test
    fun testIdIsNotTheSummary() {
        assertTrue(TEST_NOTIFICATION_ID < 0)
    }

    @Test
    fun fullScreenAppearsWhenAlarmIsOn() {
        val ok = visibleChecks(facts(alarmEnabled = true, canFullScreen = true))
        assertEquals(CheckState.OK, ok.state(ReminderCheckId.FULL_SCREEN))
        val problem = visibleChecks(facts(alarmEnabled = true, canFullScreen = false))
        assertEquals(CheckState.PROBLEM, problem.state(ReminderCheckId.FULL_SCREEN))
        assertFalse(visibleChecks(facts(alarmEnabled = false)).any { it.id == ReminderCheckId.FULL_SCREEN })
        assertFalse(visibleChecks(facts(sdk = 33, alarmEnabled = true)).any { it.id == ReminderCheckId.FULL_SCREEN })
    }

    @Test
    fun xiaomiAlarmAddsLockScreen() {
        val ids = visibleChecks(facts(oem = OemFamily.XIAOMI, alarmEnabled = true)).map { it.id }
        assertTrue(ReminderCheckId.LOCK_SCREEN in ids)
        assertFalse(
            visibleChecks(facts(oem = OemFamily.XIAOMI, alarmEnabled = false))
                .any { it.id == ReminderCheckId.LOCK_SCREEN }
        )
    }

    private fun facts(
        sdk: Int = 34,
        oem: OemFamily = OemFamily.STOCK,
        notificationsEnabled: Boolean = true,
        channelBlocked: Boolean = false,
        canExact: Boolean = true,
        batteryUnrestricted: Boolean = true,
        powerSaveOn: Boolean = false,
        alarmEnabled: Boolean = true,
        canFullScreen: Boolean = true,
    ) = ReminderFacts(
        sdk = sdk,
        oem = oem,
        notificationsEnabled = notificationsEnabled,
        channelBlocked = channelBlocked,
        canExact = canExact,
        batteryUnrestricted = batteryUnrestricted,
        powerSaveOn = powerSaveOn,
        alarmEnabled = alarmEnabled,
        canFullScreen = canFullScreen,
    )

    private fun List<ReminderCheck>.state(id: ReminderCheckId) = first { it.id == id }.state
}
