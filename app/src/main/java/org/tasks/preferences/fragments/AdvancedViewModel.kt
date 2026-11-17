package org.tasks.preferences.fragments

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.analytics.Firebase
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.caldav.VtodoCache
import org.tasks.data.db.Database
import org.tasks.etebase.EtebaseLocalCache
import org.tasks.files.FileHelper
import org.tasks.filters.Filter
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.preferences.Preferences
import org.tasks.receivers.ShortcutBadger
import javax.inject.Inject

@HiltViewModel
class AdvancedViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: Preferences,
    private val database: Database,
    private val vtodoCache: VtodoCache,
    private val firebase: Firebase,
    private val defaultFilterProvider: DefaultFilterProvider,
    private val refreshBroadcaster: RefreshBroadcaster,
) : ViewModel() {

    var attachmentDirSummary by mutableStateOf("")
        private set
    var showResetDialog by mutableStateOf(false)
        private set
    var showResetCertificatesDialog by mutableStateOf(false)
        private set
    var showDeleteDataDialog by mutableStateOf(false)
        private set
    var badgesEnabled by mutableStateOf(false)
        private set
    var badgeFilterName by mutableStateOf("")
        private set
    var showRestartDialog by mutableStateOf(false)
        private set

    init {
        refreshState()
    }

    fun refreshState() {
        refreshAttachmentDirectory()
        badgesEnabled = preferences.getBoolean(R.string.p_badges_enabled, false)
        viewModelScope.launch {
            val filter = defaultFilterProvider.getBadgeFilter()
            badgeFilterName = filter.title ?: ""
        }
    }

    val attachmentsDirectory: Uri?
        get() = preferences.attachmentsDirectory

    fun updateBadges(enabled: Boolean) {
        preferences.setBoolean(R.string.p_badges_enabled, enabled)
        badgesEnabled = enabled
        if (enabled) {
            showRestartDialog = true
        } else {
            ShortcutBadger.removeCount(context)
        }
    }

    fun setBadgeFilter(filter: Filter) {
        defaultFilterProvider.setBadgeFilter(filter)
        badgeFilterName = filter.title ?: ""
        refreshBroadcaster.broadcastRefresh()
    }

    suspend fun getBadgeFilter() = defaultFilterProvider.getBadgeFilter()

    fun dismissRestartDialog() { showRestartDialog = false }

    fun openResetDialog() { showResetDialog = true }
    fun dismissResetDialog() { showResetDialog = false }
    fun openResetCertificatesDialog() { showResetCertificatesDialog = true }
    fun dismissResetCertificatesDialog() { showResetCertificatesDialog = false }
    fun openDeleteDataDialog() { showDeleteDataDialog = true }
    fun dismissDeleteDataDialog() { showDeleteDataDialog = false }

    fun handleFilesDirResult(uri: Uri) {
        preferences.setUri(R.string.p_attachment_dir, uri)
        refreshAttachmentDirectory()
    }

    fun resetPreferences() {
        firebase.logEvent(
            R.string.event_settings_click,
            R.string.param_type to "reset_preferences",
        )
        firebase.unregisterPrefChangeListener()
        preferences.reset()
        kotlin.system.exitProcess(0)
    }

    fun deleteTaskData() {
        firebase.logEvent(
            R.string.event_settings_click,
            R.string.param_type to "delete_task_data",
        )
        viewModelScope.launch(NonCancellable) {
            context.deleteDatabase(database.name)
            vtodoCache.clear()
            EtebaseLocalCache.clear(context.filesDir.absolutePath)
            kotlin.system.exitProcess(0)
        }
    }

    private fun refreshAttachmentDirectory() {
        attachmentDirSummary =
            FileHelper.uri2String(preferences.attachmentsDirectory) ?: ""
    }
}
