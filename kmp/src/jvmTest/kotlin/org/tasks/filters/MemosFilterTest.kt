package org.tasks.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.DatabaseTest
import org.tasks.constructor.Constructor
import org.tasks.constructor.fits

class MemosFilterTest : DatabaseTest() {
    private val codec by lazy {
        FilterPreferenceCodec(
            db.filterDao(),
            db.tagDataDao(),
            db.caldavDao(),
            db.locationDao(),
        )
    }

    @Test
    fun fitsFalseWhenMemosOff() {
        val filter = MemosFilter(title = "Memos")
        assertFalse(filter.fits(Constructor(memos = false)))
        assertTrue(filter.fits(Constructor(memos = true)))
    }

    @Test
    fun codecRoundTripFilterMemos() {
        val original = MemosFilter(title = "Memos")
        val encoded = codec.encode(original)
        assertEquals(
            "${FilterPreferenceCodec.TYPE_FILTER}:${FilterPreferenceCodec.FILTER_MEMOS}",
            encoded,
        )
        assertEquals(encoded, codec.encode(MemosFilter(title = "other")))
        assertEquals("builtin_memos", original.key())
    }
}