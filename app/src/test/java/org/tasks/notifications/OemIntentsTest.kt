package org.tasks.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OemIntentsTest {
    @Test
    fun xiaomiAutostartIsFirst() {
        val first = oemCandidates(ReminderCheckId.AUTOSTART, "com.scorda.app", "Scorda").first()
        assertEquals("com.miui.securitycenter", first.component?.packageName)
        assertEquals(
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
            first.component?.className,
        )
    }

    @Test
    fun xiaomiRowsHaveCandidates() {
        assertTrue(oemCandidates(ReminderCheckId.AUTOSTART, PKG, LABEL).isNotEmpty())
        assertTrue(oemCandidates(ReminderCheckId.BACKGROUND, PKG, LABEL).isNotEmpty())
        assertTrue(oemCandidates(ReminderCheckId.POPUPS, PKG, LABEL).isNotEmpty())
    }

    @Test
    fun stockIdsAndRecentsHaveNoIntent() {
        assertTrue(oemCandidates(ReminderCheckId.NOTIFICATIONS, PKG, LABEL).isEmpty())
        assertTrue(oemCandidates(ReminderCheckId.EXACT_ALARMS, PKG, LABEL).isEmpty())
        assertTrue(oemCandidates(ReminderCheckId.BATTERY, PKG, LABEL).isEmpty())
        assertTrue(oemCandidates(ReminderCheckId.RECENTS_LOCK, PKG, LABEL).isEmpty())
    }

    private companion object {
        const val PKG = "com.scorda.app"
        const val LABEL = "Scorda"
    }
}
