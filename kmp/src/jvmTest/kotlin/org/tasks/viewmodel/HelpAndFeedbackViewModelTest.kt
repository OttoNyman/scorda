package org.tasks.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.tasks.InMemoryDataStore
import org.tasks.PlatformConfiguration
import org.tasks.preferences.TasksPreferences
import org.tasks.viewmodel.HelpAndFeedbackViewModel.Companion.LOGS_UNLOCK_TAPS

class HelpAndFeedbackViewModelTest {

    private fun viewModel(supportsLogExport: Boolean = true) = HelpAndFeedbackViewModel(
        reporting = mock(),
        tasksPreferences = TasksPreferences(InMemoryDataStore()),
        platformConfiguration = PlatformConfiguration(supportsLogExport = supportsLogExport),
        purchaseState = mock(),
        collectStatistics = false,
    )

    @Test
    fun tenthVersionTapRevealsSendLogsOnce() {
        val viewModel = viewModel()

        val unlocks = (1..LOGS_UNLOCK_TAPS + 5).map { viewModel.onVersionClick() }

        assertEquals(listOf(LOGS_UNLOCK_TAPS), unlocks.withIndex().filter { it.value }.map { it.index + 1 })
        assertTrue(viewModel.showSendLogs)
    }

    @Test
    fun sendLogsStaysHiddenWithoutLogExport() {
        val viewModel = viewModel(supportsLogExport = false)

        repeat(LOGS_UNLOCK_TAPS) { assertFalse(viewModel.onVersionClick()) }

        assertFalse(viewModel.showSendLogs)
    }
}
