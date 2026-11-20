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
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_GOOGLE_TASKS
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

    var googleAccount by mutableStateOf<CaldavAccount?>(null)
        private set
    var lastBackupSummary by mutableStateOf("")
        private set
    var driveBackupEnabled by mutableStateOf(false)
        private set
    var tasksSyncEnabled by mutableStateOf(false)
        private set
    var listProblem by mutableStateOf("")
        private set
    var ambiguousLists by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var lastDriveBackupSummary by mutableStateOf("")
        private set
    var lastAndroidBackupSummary by mutableStateOf("")
        private set

    val backupDirectory: Uri?
        get() = preferences.backupDirectory

    fun refreshState(preferencesViewModel: PreferencesViewModel) {
        refreshTasks()
        refreshDriveState(preferencesViewModel)
        refreshLocalBackupSummary(preferencesViewModel.lastBackup.value)
        refreshDriveBackupSummary(preferencesViewModel.lastDriveBackup.value)
        refreshAndroidBackupSummary(preferencesViewModel.lastAndroidBackup.value)
        preferencesViewModel.updateBackups()
    }

    fun disableDriveBackup(preferencesViewModel: PreferencesViewModel) {
        preferences.remove(R.string.p_backups_drive_last)
        preferences.remove(R.string.p_google_drive_backup_account)
        preferencesViewModel.updateDriveBackup()
        refreshDriveState(preferencesViewModel)
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
            googleAccount = account
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

    /** Signs out of Drive. Tasks and the Google Tasks account stay on this phone. */
    fun logout(preferencesViewModel: PreferencesViewModel, onDone: () -> Unit) {
        viewModelScope.launch {
            listOf(
                R.string.p_google_tasks_list_id,
                R.string.p_google_tasks_previous_list_id,
                R.string.p_google_tasks_list_problem,
                R.string.p_google_tasks_ambiguous,
                R.string.p_google_tasks_paused,
                R.string.p_google_tasks_force_create,
                R.string.p_google_tasks_move_local,
                R.string.p_google_tasks_move_target,
                R.string.p_google_drive_backup,
                R.string.p_google_drive_granted,
            ).forEach { preferences.remove(it) }
            disableDriveBackup(preferencesViewModel)
            refreshTasks()
            onDone()
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
        driveBackupEnabled = preferencesViewModel.driveAccount != null
    }

    fun refreshLocalBackupSummary(timestamp: Long?) {
        viewModelScope.launch { lastBackupSummary = formatLastBackup(timestamp) }
    }

    fun refreshDriveBackupSummary(timestamp: Long?) {
        viewModelScope.launch { lastDriveBackupSummary = formatLastBackup(timestamp) }
    }

    fun refreshAndroidBackupSummary(timestamp: Long?) {
        viewModelScope.launch { lastAndroidBackupSummary = formatLastBackup(timestamp) }
    }

    private suspend fun formatLastBackup(timestamp: Long?): String {
        val time = timestamp
            ?.takeIf { it >= 0 }
            ?.let { DateFormatter.create(context.is24HourFormat).fullDateTime(it) }
            ?: context.getString(R.string.last_backup_never)
        return context.getString(R.string.last_backup, time)
    }
}
