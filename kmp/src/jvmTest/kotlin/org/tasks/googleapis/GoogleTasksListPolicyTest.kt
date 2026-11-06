package org.tasks.googleapis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleTasksListPolicyTest {
    @Test
    fun aForeignListIsNotSelected() {
        val choice = chooseScordaList(
            listOf(RemoteTaskList("default", "My Tasks"), RemoteTaskList("s", SCORDA_LIST_NAME)),
            savedId = null,
        )
        assertEquals(ScordaListChoice.Ready("s"), choice)
    }

    @Test
    fun aMissingSavedListIsNotReplaced() {
        val choice = chooseScordaList(
            listOf(RemoteTaskList("other", "My Tasks")),
            savedId = "scorda",
        )
        assertEquals(ScordaListChoice.Missing, choice)
        assertFalse(shouldDeleteLocalList("scorda", emptySet(), protectedId = "scorda"))
    }

    @Test
    fun aForeignLocalListIsDeleted() {
        assertTrue(shouldDeleteLocalList("default", keptRemoteIds = setOf("scorda"), protectedId = "scorda"))
    }

    @Test
    fun renamingKeepsTheSameId() {
        val choice = chooseScordaList(
            listOf(RemoteTaskList("scorda", "Renamed")),
            savedId = "scorda",
        )
        assertEquals(ScordaListChoice.Ready("scorda"), choice)
    }

    @Test
    fun twoListsWithTheSameNameAreNotPickedSilently() {
        val choice = chooseScordaList(
            listOf(RemoteTaskList("a", SCORDA_LIST_NAME), RemoteTaskList("b", SCORDA_LIST_NAME)),
            savedId = null,
        )
        assertTrue(choice is ScordaListChoice.Ambiguous)
    }

    @Test
    fun theDefaultAndAScordaNamedListMoveIntoScorda() {
        val plan = planLocalMove(
            local = listOf(LocalList("d", "Default"), LocalList("s", SCORDA_LIST_NAME)),
            localDefault = "d",
            scordaId = "scorda",
            known = emptyList(),
        )
        assertEquals(listOf("scorda", "scorda"), plan.map { it.second })
    }

    @Test
    fun aListMergesIntoAKnownGoogleListWithItsNameOrGetsANewOne() {
        val plan = planLocalMove(
            local = listOf(LocalList("w", "Work"), LocalList("h", "Home")),
            localDefault = null,
            scordaId = "scorda",
            known = listOf(LocalList("gw", "Work")),
        )
        assertEquals(listOf("gw", null), plan.map { it.second })
    }
}
