package org.tasks.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import org.tasks.R
import org.tasks.themes.TasksIcons

@Composable
fun BackupsScreen(
    backupDirSummary: String,
    showBackupDirWarning: Boolean,
    lastBackupSummary: String,
    showLocalBackupWarning: Boolean,
    backupsEnabled: Boolean,
    driveBackupEnabled: Boolean,
    driveAccountSummary: String,
    driveAccountEnabled: Boolean,
    lastDriveBackupSummary: String,
    showDriveBackupWarning: Boolean,
    androidBackupEnabled: Boolean,
    lastAndroidBackupSummary: String,
    showAndroidBackupWarning: Boolean,
    ignoreWarnings: Boolean,
    onDocumentation: () -> Unit,
    onBackupDir: () -> Unit,
    onBackupNow: () -> Unit,
    onImportBackup: () -> Unit,
    onBackupsEnabled: (Boolean) -> Unit,
    onDriveBackup: (Boolean) -> Unit,
    onDriveAccount: () -> Unit,
    onAndroidBackup: (Boolean) -> Unit,
    onDeviceSettings: () -> Unit,
    onIgnoreWarnings: (Boolean) -> Unit,
    tasksEnabled: Boolean = false,
    driveDailyEnabled: Boolean = driveBackupEnabled,
    expiredMessage: String? = null,
    onTasksEnabled: (Boolean) -> Unit = {},
    onDriveDaily: (Boolean) -> Unit = {},
    onSaveDriveNow: () -> Unit = {},
    onRestoreDrive: () -> Unit = {},
    onHelp: () -> Unit = {},
    listProblem: String = "",
    ambiguousLists: List<Pair<String, String>> = emptyList(),
    onRecreateList: () -> Unit = {},
    onPickList: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))

        // Documentation
        SettingsItemCard(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
            PreferenceRow(
                title = stringResource(R.string.documentation),
                icon = TasksIcons.OPEN_IN_NEW,
                onClick = onDocumentation,
            )
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))

        // Backup directory, backup now, import
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                PreferenceRow(
                    title = stringResource(R.string.backup_directory),
                    summary = backupDirSummary,
                    summaryMaxLines = Int.MAX_VALUE,
                    showError = showBackupDirWarning,
                    onClick = onBackupDir,
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.backup_BAc_export),
                    summary = lastBackupSummary,
                    showError = showLocalBackupWarning,
                    onClick = onBackupNow,
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.backup_BAc_import),
                    onClick = onImportBackup,
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                SwitchPreferenceRow(
                    title = stringResource(R.string.automatic_backups),
                    checked = backupsEnabled,
                    onCheckedChange = onBackupsEnabled,
                )
            }
        }

        SectionHeader(
            R.string.sync_and_backup,
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            onClick = onHelp,
        )
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                SwitchPreferenceRow(
                    title = stringResource(R.string.google_tasks_sync),
                    summary = stringResource(R.string.google_tasks_sync_summary),
                    checked = tasksEnabled,
                    onCheckedChange = onTasksEnabled,
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                SwitchPreferenceRow(
                    title = stringResource(R.string.google_drive_backup),
                    summary = expiredMessage ?: stringResource(R.string.google_drive_backup_summary),
                    checked = driveDailyEnabled,
                    onCheckedChange = onDriveDaily,
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.save_drive_now),
                    onClick = onSaveDriveNow,
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                PreferenceRow(
                    title = stringResource(R.string.restore_drive),
                    summary = lastDriveBackupSummary,
                    onClick = onRestoreDrive,
                )
            }
        }
        if (listProblem == "missing" || ambiguousLists.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = SettingsContentPadding),
                verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
            ) {
                if (listProblem == "missing") {
                    SettingsItemCard {
                        PreferenceRow(
                            title = stringResource(R.string.google_list_recreate),
                            summary = stringResource(R.string.google_list_missing),
                            onClick = onRecreateList,
                        )
                    }
                }
                ambiguousLists.forEach { (id, title) ->
                    SettingsItemCard {
                        PreferenceRow(
                            title = title.ifBlank { id },
                            summary = stringResource(R.string.google_list_pick),
                            onClick = { onPickList(id) },
                        )
                    }
                }
            }
        }

        // Android Backup Service section
        SectionHeader(
            R.string.android_auto_backup,
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                val errorColor = colorResource(R.color.overdue)
                SwitchPreferenceRow(
                    title = stringResource(R.string.enabled),
                    summary = lastAndroidBackupSummary,
                    checked = androidBackupEnabled,
                    onCheckedChange = onAndroidBackup,
                    icon = if (showAndroidBackupWarning)
                        TasksIcons.ERROR_OUTLINE else null,
                    iconTint = if (showAndroidBackupWarning) errorColor else null,
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                PreferenceRow(
                    title = stringResource(R.string.device_settings),
                    summary = stringResource(R.string.android_auto_backup_device_summary),
                    summaryMaxLines = 4,
                    icon = TasksIcons.OPEN_IN_NEW,
                    onClick = onDeviceSettings,
                )
            }
        }

        // Advanced section
        SectionHeader(
            R.string.preferences_advanced,
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        SettingsItemCard(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
            SwitchPreferenceRow(
                title = stringResource(R.string.backups_ignore_warnings),
                summary = stringResource(R.string.backups_ignore_warnings_summary),
                checked = ignoreWarnings,
                onCheckedChange = onIgnoreWarnings,
            )
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
