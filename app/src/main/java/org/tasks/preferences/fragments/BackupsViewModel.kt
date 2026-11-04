package org.tasks.preferences.fragments

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tasks.R
import org.tasks.analytics.Firebase
import org.tasks.data.dao.CaldavDao
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_GOOGLE_TASKS
import org.tasks.files.FileHelper
import org.tasks.extensions.Context.is24HourFormat
import org.tasks.kmp.org.tasks.time.DateFormatter
import org.tasks.preferences.Preferences
import org.tasks.preferences.PreferencesViewModel
import javax.inject.Inject

@HiltViewModel
class BackupsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: Preferences,
    private val firebase: Firebase,
    private val caldavDao: CaldavDao,
) : ViewModel() {

    var backupDirSummary by mutableStateOf("")
        private set
    var showBackupDirWarning by mutableStateOf(false)
        private set
    var lastBackupSummary by mutableStateOf("")
        private set
    var showLocalBackupWarning by mutableStateOf(false)
        private set
    var backupsEnabled by mutableStateOf(preferences.getBoolean(R.string.p_backups_enabled, true))
        private set
    var driveBackupEnabled by mutableStateOf(false)
        private set
    var tasksSyncEnabled by mutableStateOf(false)
        private set
    var listProblem by mutableStateOf("")
        private set
    var ambiguousLists by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var driveAccountSummary by mutableStateOf("")
        private set
    var driveAccountEnabled by mutableStateOf(false)
        private set
    var lastDriveBackupSummary by mutableStateOf("")
        private set
    var showDriveBackupWarning by mutableStateOf(false)
        private set
    var androidBackupEnabled by mutableStateOf(
        preferences.getBoolean(R.string.p_backups_android_backup_enabled, true)
    )
        private set
    var lastAndroidBackupSummary by mutableStateOf("")
        private set
    var showAndroidBackupWarning by mutableStateOf(false)
        private set
    var ignoreWarnings by mutableStateOf(
        preferences.getBoolean(R.string.p_backups_ignore_warnings, false)
    )
        private set

    val backupDirectory: Uri?
        get() = preferences.backupDirectory

    fun refreshState(preferencesViewModel: PreferencesViewModel) {
        backupsEnabled = preferences.getBoolean(R.string.p_backups_enabled, true)
        androidBackupEnabled = preferences.getBoolean(
            R.string.p_backups_android_backup_enabled, true,
        )
        ignoreWarnings = preferences.getBoolean(R.string.p_backups_ignore_warnings, false)
        refreshTasks()
        refreshDriveState(preferencesViewModel)
        refreshWarnings(preferencesViewModel)
        preferencesViewModel.updateBackups()
    }

    fun updateBackupsEnabled(enabled: Boolean) {
        preferences.setBoolean(R.string.p_backups_enabled, enabled)
        backupsEnabled = enabled
    }

    fun disableDriveBackup(preferencesViewModel: PreferencesViewModel) {
        preferences.remove(R.string.p_backups_drive_last)
        preferences.remove(R.string.p_google_drive_backup_account)
        preferencesViewModel.updateDriveBackup()
        refreshDriveState(preferencesViewModel)
    }

    fun updateAndroidBackup(enabled: Boolean, preferencesViewModel: PreferencesViewModel) {
        preferences.setBoolean(R.string.p_backups_android_backup_enabled, enabled)
        androidBackupEnabled = enabled
        refreshAndroidBackupSummary(preferencesViewModel.lastAndroidBackup.value, preferencesViewModel)
    }

    fun updateIgnoreWarnings(enabled: Boolean, preferencesViewModel: PreferencesViewModel) {
        preferences.setBoolean(R.string.p_backups_ignore_warnings, enabled)
        ignoreWarnings = enabled
        refreshWarnings(preferencesViewModel)
    }

    fun handleBackupDirResult(uri: Uri, preferencesViewModel: PreferencesViewModel) {
        preferences.setUri(R.string.p_backup_dir, uri)
        refreshBackupDirectory(preferencesViewModel)
        preferencesViewModel.updateLocalBackup()
    }

    fun logEvent(type: String) {
        firebase.logEvent(
            R.string.event_settings_click,
            R.string.param_type to type,
        )
    }

    fun refreshTasks() {
        viewModelScope.launch {
            val account = caldavDao.getAccounts(TYPE_GOOGLE_TASKS).firstOrNull()
            tasksSyncEnabled = account != null &&
                    !preferences.getBoolean(R.string.p_google_tasks_paused, false)
            listProblem = preferences.getStringValue(R.string.p_google_tasks_list_problem).orEmpty()
            ambiguousLists = preferences.getStringValue(R.string.p_google_tasks_ambiguous)
                .orEmpty()
                .lineSequence()
                .mapNotNull { line ->
                    val parts = line.split('\t', limit = 2)
                    val id = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    id to parts.getOrNull(1).orEmpty().ifBlank { id }
                }
                .toList()
        }
    }

    fun recreateScordaList() {
        val current = preferences.getStringValue(R.string.p_google_tasks_list_id)
        if (!current.isNullOrBlank()) {
            preferences.setString(R.string.p_google_tasks_previous_list_id, current)
        }
        preferences.setString(R.string.p_google_tasks_list_id, null)
        preferences.setBoolean(R.string.p_google_tasks_force_create, true)
        preferences.setString(R.string.p_google_tasks_list_problem, null)
        preferences.setString(R.string.p_google_tasks_ambiguous, null)
        listProblem = ""
        ambiguousLists = emptyList()
        viewModelScope.launch {
            caldavDao.getAccounts(TYPE_GOOGLE_TASKS).forEach {
                caldavDao.update(it.copy(error = ""))
            }
        }
    }

    fun pickScordaList(id: String) {
        preferences.setString(R.string.p_google_tasks_list_id, id)
        preferences.setBoolean(R.string.p_google_tasks_force_create, false)
        preferences.setString(R.string.p_google_tasks_list_problem, null)
        preferences.setString(R.string.p_google_tasks_ambiguous, null)
        listProblem = ""
        ambiguousLists = emptyList()
        viewModelScope.launch {
            caldavDao.getAccounts(TYPE_GOOGLE_TASKS).forEach {
                caldavDao.update(it.copy(error = ""))
            }
        }
    }

    fun pauseTasks() {
        preferences.setBoolean(R.string.p_google_tasks_paused, true)
        tasksSyncEnabled = false
    }

    fun setDriveDaily(enabled: Boolean) {
        preferences.setBoolean(R.string.p_google_drive_backup, enabled)
        driveBackupEnabled = enabled
    }

    fun driveGranted(): Boolean =
        preferences.getBoolean(R.string.p_google_drive_granted, false) ||
                preferences.getBoolean(R.string.p_google_drive_backup, false)

    fun refreshDriveState(preferencesViewModel: PreferencesViewModel) {
        val account = preferencesViewModel.driveAccount
        driveBackupEnabled = account != null
        driveAccountEnabled = account != null
        driveAccountSummary = account?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.none)
    }

    fun refreshWarnings(preferencesViewModel: PreferencesViewModel) {
        refreshBackupDirectory(preferencesViewModel)
        refreshLocalBackupSummary(preferencesViewModel.lastBackup.value, preferencesViewModel)
        refreshDriveBackupSummary(preferencesViewModel.lastDriveBackup.value, preferencesViewModel)
        refreshAndroidBackupSummary(preferencesViewModel.lastAndroidBackup.value, preferencesViewModel)
    }

    fun refreshLocalBackupSummary(timestamp: Long?, preferencesViewModel: PreferencesViewModel) {
        viewModelScope.launch { lastBackupSummary = formatLastBackup(timestamp) }
        showLocalBackupWarning = preferences.showBackupWarnings() && preferencesViewModel.staleLocalBackup
    }

    fun refreshDriveBackupSummary(timestamp: Long?, preferencesViewModel: PreferencesViewModel) {
        viewModelScope.launch { lastDriveBackupSummary = formatLastBackup(timestamp) }
        showDriveBackupWarning = preferences.showBackupWarnings() && preferencesViewModel.staleRemoteBackup
    }

    fun refreshAndroidBackupSummary(timestamp: Long? = null, preferencesViewModel: PreferencesViewModel) {
        viewModelScope.launch { lastAndroidBackupSummary = formatLastBackup(timestamp) }
        showAndroidBackupWarning = preferences.showBackupWarnings() && preferencesViewModel.staleRemoteBackup
    }

    private fun refreshBackupDirectory(preferencesViewModel: PreferencesViewModel) {
        val location = FileHelper.uri2String(preferences.backupDirectory) ?: ""
        if (preferences.showBackupWarnings() && preferencesViewModel.usingPrivateStorage) {
            showBackupDirWarning = true
            val warning = context.getString(
                R.string.backup_location_warning,
                FileHelper.uri2String(preferences.appPrivateStorage),
            )
            backupDirSummary = "$location\n\n$warning"
        } else {
            showBackupDirWarning = false
            backupDirSummary = location
        }
    }

    private suspend fun formatLastBackup(timestamp: Long?): String {
        val time = timestamp
            ?.takeIf { it >= 0 }
            ?.let { DateFormatter.create(context.is24HourFormat).fullDateTime(it) }
            ?: context.getString(R.string.last_backup_never)
        return context.getString(R.string.last_backup, time)
    }
}
