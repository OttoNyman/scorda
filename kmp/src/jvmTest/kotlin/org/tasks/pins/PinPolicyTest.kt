package org.tasks.pins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.constructor.Constructor
import org.tasks.data.entity.Task

class PinPolicyTest {
    private val task = Task(id = 1, title = "task", pinned = 1_000)
    private val memo = Task(id = 2, title = "memo", pinned = 2_000, memo = true)

    @Test
    fun nothingWhenPinningIsOff() {
        assertTrue(
            pinCards(listOf(task, memo), emptySet(), Constructor(pinning = false, pinShade = true, memos = true))
                .isEmpty()
        )
    }

    @Test
    fun theShadeIsOptional() {
        assertTrue(
            pinCards(listOf(task, memo), emptySet(), Constructor(pinning = true, pinShade = false, memos = true))
                .isEmpty()
        )
    }

    @Test
    fun pinCardsRespectFeatures() {
        assertEquals(
            listOf(task),
            pinCards(listOf(task, memo), emptySet(), Constructor(pinning = true, pinShade = true, memos = false)),
        )
        assertEquals(
            listOf(task, memo),
            pinCards(listOf(task, memo), emptySet(), Constructor(pinning = true, pinShade = true, memos = true)),
        )
    }

    @Test
    fun aRingingReminderOwnsTheCard() {
        assertEquals(
            listOf(memo),
            pinCards(listOf(task, memo), setOf(task.id), Constructor(pinning = true, pinShade = true, memos = true)),
        )
    }
}
