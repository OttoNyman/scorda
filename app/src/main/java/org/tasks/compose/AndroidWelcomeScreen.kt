package org.tasks.compose

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.api.services.drive.DriveScopes
import com.todoroo.astrid.gtasks.auth.GtasksLoginActivity
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.TasksApplication.Companion.IS_GENERIC
import org.tasks.auth.TasksServerEnvironment
import org.tasks.extensions.Context.toast
import org.tasks.preferences.PreferencesViewModel
import org.jetbrains.compose.resources.stringResource as composeStringResource
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.cancel
import tasks.kmp.generated.resources.last_backup_never

@Composable
fun WelcomeScreen(
    importViewModel: ImportTasksViewModel,
    preferencesViewModel: PreferencesViewModel,
    filePickerIntent: Intent,
    onBack: () -> Unit,
    onSignIn: (() -> Unit)?,
    onContinueWithoutSync: () -> Unit,
    onImportBackup: () -> Unit,
    onRestoreDrive: () -> Unit = {},
    openLegalUrl: (String) -> Unit,
    environments: List<TasksServerEnvironment.Environment>,
    currentEnvironment: String,
    onSelectEnvironment: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importUri by importViewModel.importUri.collectAsStateWithLifecycle()
    var driveFiles by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var afterLogin by remember { mutableStateOf<(() -> Unit)?>(null) }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            importViewModel.setImportUri(result.data?.data)
        }
    }

    val driveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            preferencesViewModel.updateDriveBackup()
            afterLogin?.invoke()
        } else {
            result.data?.getStringExtra(GtasksLoginActivity.EXTRA_ERROR)
                ?.let { context.toast(it) }
        }
        afterLogin = null
    }

    fun openDriveList() {
        scope.launch {
            try {
                val files = preferencesViewModel.listDriveBackups()
                driveFiles = files.mapNotNull { file ->
                    file.id?.let { id -> id to (file.name ?: id) }
                }
            } catch (_: Exception) {
                context.toast(R.string.google_request_failed)
            }
        }
    }

    fun startRestoreDrive() {
        onRestoreDrive()
        val open = { openDriveList() }
        if (preferencesViewModel.driveGranted()) {
            open()
        } else {
            afterLogin = open
            driveLauncher.launch(
                Intent(context, GtasksLoginActivity::class.java)
                    .putStringArrayListExtra(
                        GtasksLoginActivity.EXTRA_SCOPES,
                        arrayListOf(DriveScopes.DRIVE_FILE),
                    )
                    .putExtra(GtasksLoginActivity.EXTRA_DAILY_DRIVE, false)
                    .putExtra(
                        GtasksLoginActivity.EXTRA_ACCOUNT,
                        preferencesViewModel.driveAccountName(),
                    )
            )
        }
    }

    importUri?.let { uri ->
        ImportTasksDialog(
            uri = uri,
            viewModel = importViewModel,
            onFinished = { importViewModel.reset() }
        )
    }

    driveFiles?.let { files ->
        AlertDialog(
            onDismissRequest = { driveFiles = null },
            title = { Text(stringResource(R.string.restore_drive)) },
            text = {
                Column {
                    if (files.isEmpty()) {
                        Text(composeStringResource(Res.string.last_backup_never))
                    }
                    files.forEach { (id, name) ->
                        TextButton(onClick = {
                            driveFiles = null
                            preferencesViewModel.downloadDriveBackup(id, name, { uri ->
                                importViewModel.setImportUri(uri)
                            }, {
                                context.toast(R.string.google_request_failed)
                            })
                        }) { Text(name) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { driveFiles = null }) {
                    Text(composeStringResource(Res.string.cancel))
                }
            },
        )
    }

    BackHandler(onBack = onBack)

    WelcomeScreenLayout(
        showLegalDisclosure = !IS_GENERIC,
        showRestoreDrive = true,
        onSignIn = onSignIn,
        onContinueWithoutSync = onContinueWithoutSync,
        onImportBackup = {
            onImportBackup()
            importBackupLauncher.launch(filePickerIntent)
        },
        onRestoreDrive = { startRestoreDrive() },
        openLegalUrl = openLegalUrl,
        environments = environments,
        currentEnvironment = currentEnvironment,
        onSelectEnvironment = onSelectEnvironment,
    )
}
