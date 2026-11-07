package org.tasks.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.runBlocking
import org.tasks.PlatformConfiguration
import org.tasks.TasksBuildConfig
import org.tasks.analytics.AnalyticsEvents
import org.tasks.analytics.Reporting
import org.tasks.billing.BillingProvider
import org.tasks.billing.PurchaseState
import org.tasks.preferences.TasksPreferences

open class HelpAndFeedbackViewModel(
    private val reporting: Reporting,
    tasksPreferences: TasksPreferences,
    private val platformConfiguration: PlatformConfiguration,
    purchaseState: PurchaseState,
    collectStatistics: Boolean = runBlocking { tasksPreferences.get(TasksPreferences.collectStatistics, true) },
) : ViewModel() {

    val versionName: String = TasksBuildConfig.VERSION_NAME

    val isGooglePlay: Boolean =
        platformConfiguration.billingProvider == BillingProvider.GOOGLE_PLAY

    val showTermsOfService: Boolean =
        !platformConfiguration.isLibre || purchaseState.hasTasksAccount

    /** Hidden until the version row is tapped [LOGS_UNLOCK_TAPS] times; lives as long as this view model. */
    var showSendLogs by mutableStateOf(false)
        private set

    private var versionTaps = 0

    val showCollectStatistics: Boolean = !platformConfiguration.isLibre

    var collectStatistics by mutableStateOf(collectStatistics)
        protected set

    var showRestartDialog by mutableStateOf(false)
        private set

    /** Returns true on the tap that reveals "Send application logs". */
    fun onVersionClick(): Boolean {
        if (showSendLogs || !platformConfiguration.supportsLogExport) return false
        if (++versionTaps < LOGS_UNLOCK_TAPS) return false
        showSendLogs = true
        return true
    }

    fun logEvent(type: String) {
        reporting.logEvent(
            AnalyticsEvents.SETTINGS_CLICK,
            AnalyticsEvents.PARAM_TYPE to type,
        )
    }

    fun updateCollectStatistics(enabled: Boolean) {
        collectStatistics = enabled
        showRestartDialog = true
    }

    fun dismissRestartDialog() {
        showRestartDialog = false
    }

    companion object {
        const val LOGS_UNLOCK_TAPS = 10
    }
}
