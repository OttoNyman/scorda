package org.tasks.preferences.fragments

import android.app.Activity.RESULT_OK
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.os.BundleCompat
import com.todoroo.astrid.utility.Constants
import java.io.File
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
import org.tasks.compose.settings.MoveListsDialog
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.jetbrains.compose.resources.stringResource as composeStringResource
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.logout_warning
import org.tasks.dialogs.ExportTasksDialog
import org.tasks.dialogs.ImportTasksDialog
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
    @Inject lateinit var constructorRepository: org.tasks.constructor.ConstructorRepository

    private val preferencesViewModel: PreferencesViewModel by activityViewModels()
    private val viewModel: BackupsViewModel by viewModels()

    // A manual export just finished: offer to send the file.
    private var shareFile by mutableStateOf<Uri?>(null)

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
        constructorRepository.refreshSync()
        viewModel.refreshTasks()
        viewModel.refreshDriveState(preferencesViewModel)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesViewModel.lastBackup.observe(this) {
            viewModel.refreshLocalBackupSummary(it)
        }
        preferencesViewModel.lastDriveBackup.observe(this) {
            viewModel.refreshDriveBackupSummary(it)
        }
        preferencesViewModel.lastAndroidBackup.observe(this) {
            viewModel.refreshAndroidBackupSummary(it)
        }
        parentFragmentManager.setFragmentResultListener(
            ExportTasksDialog.REQUEST_KEY, this
        ) { _, bundle ->
            preferencesViewModel.updateLocalBackup()
            shareFile = BundleCompat.getParcelable(bundle, ExportTasksDialog.EXTRA_FILE, Uri::class.java)
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
        var moveLists by remember { mutableStateOf(0) }
        var confirmLogout by remember { mutableStateOf(false) }
        var showBackupHelp by remember { mutableStateOf(false) }
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            BackupsScreen(
                lastBackupSummary = viewModel.lastBackupSummary,
                lastDriveBackupSummary = viewModel.lastDriveBackupSummary,
                lastAndroidBackupSummary = viewModel.lastAndroidBackupSummary,
                onSaveFile = {
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
                onBackupHelp = { showBackupHelp = true },
                accountEmail = viewModel.googleAccount?.name ?: preferencesViewModel.driveAccount?.takeIf { it.isNotBlank() },
                accountError = viewModel.googleAccount?.error?.takeIf { it.isNotBlank() },
                onLogout = { confirmLogout = true },
                tasksEnabled = viewModel.tasksSyncEnabled,
                driveDailyEnabled = viewModel.driveBackupEnabled,
                onTasksEnabled = { enabled ->
                    if (enabled) {
                        lifecycleScope.launch {
                            val lists = constructorRepository.counts(currentTimeMillis()).localLists
                            val raw = constructorRepository.raw.value
                            when {
                                raw.lists && raw.localLists ->
                                    signIn(arrayListOf(TasksScopes.TASKS), daily = false)
                                raw.lists && lists > 0 -> moveLists = lists
                                // Nothing to choose: whatever lands locally before the first sync moves too.
                                else -> signIn(arrayListOf(TasksScopes.TASKS), daily = false) {
                                    constructorRepository.moveLocalListsToGoogle()
                                }
                            }
                        }
                    } else {
                        viewModel.pauseTasks()
                        constructorRepository.refreshSync()
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
            if (moveLists > 0) {
                MoveListsDialog(
                    lists = moveLists,
                    onMove = {
                        moveLists = 0
                        signIn(arrayListOf(TasksScopes.TASKS), daily = false) {
                            constructorRepository.moveLocalListsToGoogle()
                        }
                    },
                    onKeep = {
                        moveLists = 0
                        signIn(arrayListOf(TasksScopes.TASKS), daily = false) {
                            constructorRepository.write(constructorRepository.raw.value.copy(localLists = true))
                        }
                    },
                    onDismiss = { moveLists = 0 },
                    dismissText = stringResource(android.R.string.cancel),
                )
            }
            if (showBackupHelp) {
                AlertDialog(
                    onDismissRequest = { showBackupHelp = false },
                    title = { Text(stringResource(R.string.backup_help_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.backup_help_android))
                            Text(
                                stringResource(R.string.backup_help_file),
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showBackupHelp = false }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showBackupHelp = false
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }) { Text(stringResource(R.string.device_settings)) }
                    },
                )
            }
            shareFile?.let { file ->
                AlertDialog(
                    onDismissRequest = { shareFile = null },
                    title = { Text(stringResource(R.string.share_backup_title)) },
                    text = { Text(stringResource(R.string.share_backup_text)) },
                    confirmButton = {
                        TextButton(onClick = {
                            shareFile = null
                            share(file)
                        }) { Text(stringResource(R.string.share_backup_send)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { shareFile = null }) {
                            Text(stringResource(R.string.share_backup_skip))
                        }
                    },
                )
            }
            if (confirmLogout) {
                AlertDialog(
                    onDismissRequest = { confirmLogout = false },
                    title = { Text(stringResource(R.string.logout)) },
                    text = { Text(composeStringResource(Res.string.logout_warning)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmLogout = false
                            viewModel.logout(preferencesViewModel) {
                                constructorRepository.refreshSync()
                            }
                        }) { Text(stringResource(R.string.logout)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmLogout = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                )
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

    private fun share(file: Uri) {
        val content = if (file.scheme == ContentResolver.SCHEME_FILE) {
            FileProvider.getUriForFile(requireContext(), Constants.FILE_PROVIDER_AUTHORITY, File(file.path!!))
        } else {
            file
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, content)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
    }

    private fun signIn(scopes: ArrayList<String>, daily: Boolean, after: (() -> Unit)? = null) {
        afterLogin = after
        driveLauncher.launch(
            Intent(requireContext(), GtasksLoginActivity::class.java)
                .putStringArrayListExtra(GtasksLoginActivity.EXTRA_SCOPES, scopes)
                .putExtra(GtasksLoginActivity.EXTRA_DAILY_DRIVE, daily)
                .putExtra(
                    GtasksLoginActivity.EXTRA_ACCOUNT,
                    viewModel.googleAccount?.name ?: preferencesViewModel.driveAccount,
                )
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
}
