package org.tasks.preferences.fragments

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tasks.jobs.WorkManager
import org.tasks.sync.SyncSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.google.api.services.drive.DriveScopes
import com.google.api.services.tasks.TasksScopes
import com.todoroo.astrid.gtasks.auth.GtasksLoginActivity
import kotlinx.coroutines.launch
import org.tasks.compose.settings.BackupsScreen
import org.tasks.dialogs.ExportTasksDialog
import org.tasks.dialogs.ImportTasksDialog
import org.tasks.drive.DriveLoginActivity
import org.tasks.extensions.Context.openUri
import org.tasks.extensions.Context.takePersistableUriPermission
import org.tasks.extensions.Context.toast
import org.tasks.files.FileHelper
import org.tasks.preferences.BasePreferences
import org.tasks.preferences.PreferencesViewModel
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import javax.inject.Inject

private const val FRAG_TAG_EXPORT_TASKS = "frag_tag_export_tasks"
private const val FRAG_TAG_IMPORT_TASKS = "frag_tag_import_tasks"

@AndroidEntryPoint
class Backups : Fragment() {

    @Inject lateinit var theme: Theme
    @Inject lateinit var workManager: WorkManager

    private val preferencesViewModel: PreferencesViewModel by activityViewModels()
    private val viewModel: BackupsViewModel by viewModels()

    private val backupDirLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                requireContext().takePersistableUriPermission(uri)
                viewModel.handleBackupDirResult(uri, preferencesViewModel)
            }
        }
    }

    private val importPickerLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val extension = FileHelper.getExtension(requireContext(), uri)
                if (!"json".equals(extension, ignoreCase = true)) {
                    context?.toast(R.string.invalid_backup_file)
                } else {
                    ImportTasksDialog.newImportTasksDialog(uri)
                        .show(parentFragmentManager, FRAG_TAG_IMPORT_TASKS)
                }
            }
        }
    }

    private var afterLogin: (() -> Unit)? = null

    private val driveLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            preferencesViewModel.updateDriveBackup()
            afterLogin?.invoke()
        } else {
            result.data?.getStringExtra(GtasksLoginActivity.EXTRA_ERROR)
                ?.let { context?.toast(it) }
        }
        afterLogin = null
        viewModel.refreshTasks()
        viewModel.refreshDriveState(preferencesViewModel)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesViewModel.lastBackup.observe(this) {
            viewModel.refreshLocalBackupSummary(it, preferencesViewModel)
        }
        preferencesViewModel.lastDriveBackup.observe(this) {
            viewModel.refreshDriveBackupSummary(it, preferencesViewModel)
        }
        preferencesViewModel.lastAndroidBackup.observe(this) {
            viewModel.refreshAndroidBackupSummary(it, preferencesViewModel)
        }
        parentFragmentManager.setFragmentResultListener(
            ExportTasksDialog.REQUEST_KEY, this
        ) { _, _ ->
            preferencesViewModel.updateLocalBackup()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?
    ) = content {
        var showHelp by remember { mutableStateOf(false) }
        var driveFiles by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            BackupsScreen(
                backupDirSummary = viewModel.backupDirSummary,
                showBackupDirWarning = viewModel.showBackupDirWarning,
                lastBackupSummary = viewModel.lastBackupSummary,
                showLocalBackupWarning = viewModel.showLocalBackupWarning,
                backupsEnabled = viewModel.backupsEnabled,
                driveBackupEnabled = viewModel.driveBackupEnabled,
                driveAccountSummary = viewModel.driveAccountSummary,
                driveAccountEnabled = viewModel.driveAccountEnabled,
                lastDriveBackupSummary = viewModel.lastDriveBackupSummary,
                showDriveBackupWarning = viewModel.showDriveBackupWarning,
                androidBackupEnabled = viewModel.androidBackupEnabled,
                lastAndroidBackupSummary = viewModel.lastAndroidBackupSummary,
                showAndroidBackupWarning = viewModel.showAndroidBackupWarning,
                ignoreWarnings = viewModel.ignoreWarnings,
                onDocumentation = {
                    requireContext().openUri(R.string.url_backups)
                },
                onBackupDir = {
                    backupDirLauncher.launch(
                        FileHelper.newDirectoryPickerIntent(
                            context,
                            viewModel.backupDirectory,
                        )
                    )
                },
                onBackupNow = {
                    viewModel.logEvent("backup_now")
                    ExportTasksDialog.newExportTasksDialog()
                        .show(parentFragmentManager, FRAG_TAG_EXPORT_TASKS)
                },
                onImportBackup = {
                    viewModel.logEvent("import_backup")
                    importPickerLauncher.launch(
                        FileHelper.newFilePickerIntent(activity, viewModel.backupDirectory),
                    )
                },
                onBackupsEnabled = { viewModel.updateBackupsEnabled(it) },
                onDriveBackup = { enabled ->
                    if (enabled) {
                        requestGoogleDriveLogin()
                    } else {
                        viewModel.disableDriveBackup(preferencesViewModel)
                    }
                },
                onDriveAccount = {
                    requestGoogleDriveLogin()
                },
                onAndroidBackup = { enabled ->
                    viewModel.updateAndroidBackup(enabled, preferencesViewModel)
                },
                onDeviceSettings = {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                },
                onIgnoreWarnings = { enabled ->
                    viewModel.updateIgnoreWarnings(enabled, preferencesViewModel)
                },
                tasksEnabled = viewModel.tasksSyncEnabled,
                driveDailyEnabled = viewModel.driveBackupEnabled,
                onTasksEnabled = { enabled ->
                    if (enabled) {
                        signIn(arrayListOf(TasksScopes.TASKS), daily = false)
                    } else {
                        viewModel.pauseTasks()
                    }
                },
                onDriveDaily = { enabled ->
                    if (enabled) {
                        signIn(arrayListOf(DriveScopes.DRIVE_FILE), daily = true)
                    } else {
                        viewModel.setDriveDaily(false)
                    }
                },
                onSaveDriveNow = {
                    if (viewModel.driveGranted()) {
                        ExportTasksDialog.newExportTasksDialog(uploadToDrive = true)
                            .show(parentFragmentManager, FRAG_TAG_EXPORT_TASKS)
                    } else {
                        signIn(arrayListOf(DriveScopes.DRIVE_FILE), daily = false) {
                            ExportTasksDialog.newExportTasksDialog(uploadToDrive = true)
                                .show(parentFragmentManager, FRAG_TAG_EXPORT_TASKS)
                        }
                    }
                },
                onRestoreDrive = {
                    val open = {
                        lifecycleScope.launch {
                            try {
                                val files = preferencesViewModel.listDriveBackups()
                                driveFiles = files.mapNotNull { file ->
                                    file.id?.let { id -> id to (file.name ?: id) }
                                }
                            } catch (_: Exception) {
                                context?.toast(R.string.google_request_failed)
                            }
                        }
                    }
                    if (viewModel.driveGranted()) open() else {
                        signIn(arrayListOf(DriveScopes.DRIVE_FILE), daily = false) { open() }
                    }
                },
                onHelp = { showHelp = true },
                listProblem = viewModel.listProblem,
                ambiguousLists = viewModel.ambiguousLists,
                onRecreateList = {
                    viewModel.recreateScordaList()
                    lifecycleScope.launch { workManager.sync(SyncSource.USER_INITIATED) }
                },
                onPickList = { id ->
                    viewModel.pickScordaList(id)
                    lifecycleScope.launch { workManager.sync(SyncSource.USER_INITIATED) }
                },
            )
            if (showHelp) {
                ModalBottomSheet(onDismissRequest = { showHelp = false }) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(text = stringResource(R.string.sync_help_title))
                        Text(text = stringResource(R.string.sync_help_drive), modifier = Modifier.padding(top = 12.dp))
                        Text(text = stringResource(R.string.sync_help_file), modifier = Modifier.padding(top = 12.dp))
                        Text(text = stringResource(R.string.sync_help_tasks), modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            driveFiles?.let { files ->
                AlertDialog(
                    onDismissRequest = { driveFiles = null },
                    title = { Text(stringResource(R.string.restore_drive)) },
                    text = {
                        Column {
                            if (files.isEmpty()) {
                                Text(stringResource(R.string.last_backup_never))
                            }
                            files.forEach { (id, name) ->
                                TextButton(onClick = {
                                    driveFiles = null
                                    preferencesViewModel.downloadDriveBackup(id, name, { uri ->
                                        ImportTasksDialog.newImportTasksDialog(uri)
                                            .show(parentFragmentManager, FRAG_TAG_IMPORT_TASKS)
                                    }, {
                                        context?.toast(R.string.google_request_failed)
                                    })
                                }) { Text(name) }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { driveFiles = null }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                )
            }
        }
    }

    private fun signIn(scopes: ArrayList<String>, daily: Boolean, after: (() -> Unit)? = null) {
        afterLogin = after
        driveLauncher.launch(
            Intent(requireContext(), GtasksLoginActivity::class.java)
                .putStringArrayListExtra(GtasksLoginActivity.EXTRA_SCOPES, scopes)
                .putExtra(GtasksLoginActivity.EXTRA_DAILY_DRIVE, daily)
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshState(preferencesViewModel)
        val surfaceColor = theme.themeBase.getSettingsSurfaceColor(requireActivity())
        (activity as? BasePreferences)?.toolbar?.let { toolbar ->
            toolbar.setBackgroundColor(surfaceColor)
            (toolbar.parent as? View)?.setBackgroundColor(surfaceColor)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val defaultColor = ContextCompat.getColor(requireContext(), R.color.content_background)
        (activity as? BasePreferences)?.toolbar?.let { toolbar ->
            toolbar.setBackgroundColor(defaultColor)
            (toolbar.parent as? View)?.setBackgroundColor(defaultColor)
        }
    }

    private fun requestGoogleDriveLogin() {
        driveLauncher.launch(
            Intent(context, DriveLoginActivity::class.java),
        )
    }
}
