package org.tasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.data.entity.Geofence

class NormalizedPlaceTriggerTest {
    @Test
    fun bothOrNeitherBecomeArrival() {
        val both = Geofence(isArrival = true, isDeparture = true).normalizedPlaceTrigger()
        assertTrue(both.isArrival)
        assertFalse(both.isDeparture)
        val neither = Geofence(isArrival = false, isDeparture = false).normalizedPlaceTrigger()
        assertTrue(neither.isArrival)
        assertFalse(neither.isDeparture)
    }

    @Test
    fun departureAloneStays() {
        val g = Geofence(isArrival = false, isDeparture = true).normalizedPlaceTrigger()
        assertFalse(g.isArrival)
        assertTrue(g.isDeparture)
    }
}
