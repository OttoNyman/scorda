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
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.tasks.R
import org.tasks.themes.TasksIcons

enum class RegularBackupDest {
    DRIVE,
    FILE,
}

@Composable
fun BackupsScreen(
    lastBackupSummary: String,
    lastDriveBackupSummary: String,
    lastAndroidBackupSummary: String,
    regularDest: RegularBackupDest,
    fileFolderSummary: String,
    fileFolderPrivate: Boolean,
    onRegularDest: (RegularBackupDest) -> Unit,
    onPickBackupFolder: () -> Unit,
    onSaveFile: () -> Unit,
    onImportBackup: () -> Unit,
    onBackupHelp: () -> Unit,
    accountEmail: String? = null,
    onLogout: () -> Unit = {},
    expiredMessage: String? = null,
    onSaveDriveNow: () -> Unit = {},
    onRestoreDrive: () -> Unit = {},
    onHelp: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        if (accountEmail != null) {
            Spacer(modifier = Modifier.height(SettingsContentPadding))
            Column(
                modifier = Modifier.padding(horizontal = SettingsContentPadding),
                verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
            ) {
                SettingsItemCard(position = CardPosition.First) {
                    PreferenceRow(
                        title = accountEmail,
                        summary = stringResource(R.string.google_account),
                    )
                }
                SettingsItemCard(position = CardPosition.Last) {
                    PreferenceRow(
                        title = stringResource(R.string.logout),
                        icon = TasksIcons.LOGOUT,
                        onClick = onLogout,
                    )
                }
            }
        }

        SectionHeader(
            R.string.regular_backup,
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            onClick = onHelp,
        )
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                PreferenceRow(
                    title = stringResource(R.string.regular_backup_drive),
                    summary = expiredMessage ?: stringResource(R.string.google_drive_backup_summary),
                    indent = false,
                    leading = {
                        RadioButton(
                            selected = regularDest == RegularBackupDest.DRIVE,
                            onClick = null,
                            modifier = Modifier.padding(start = SettingsContentPadding),
                        )
                    },
                    onClick = { onRegularDest(RegularBackupDest.DRIVE) },
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                PreferenceRow(
                    title = stringResource(R.string.regular_backup_file),
                    summary = if (fileFolderPrivate) {
                        stringResource(R.string.regular_backup_pick_folder)
                    } else {
                        fileFolderSummary
                    },
                    showWarning = fileFolderPrivate,
                    indent = false,
                    leading = {
                        RadioButton(
                            selected = regularDest == RegularBackupDest.FILE,
                            onClick = null,
                            modifier = Modifier.padding(start = SettingsContentPadding),
                        )
                    },
                    onClick = {
                        if (regularDest == RegularBackupDest.FILE) {
                            onPickBackupFolder()
                        } else {
                            onRegularDest(RegularBackupDest.FILE)
                        }
                    },
                )
            }
        }

        SectionHeader(
            R.string.sync_and_backup,
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
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

        SectionHeader(
            R.string.backup_section,
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            onClick = onBackupHelp,
        )
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                PreferenceRow(
                    title = stringResource(R.string.save_to_file),
                    summary = if (fileFolderPrivate) {
                        stringResource(R.string.save_to_file_private_warning)
                    } else {
                        lastBackupSummary
                    },
                    showWarning = fileFolderPrivate,
                    onClick = onSaveFile,
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                PreferenceRow(
                    title = stringResource(R.string.backup_BAc_import),
                    onClick = onImportBackup,
                )
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard {
                SwitchPreferenceRow(
                    title = stringResource(R.string.android_auto_backup),
                    summary = lastAndroidBackupSummary,
                    checked = true,
                    enabled = false,
                    onCheckedChange = {},
                )
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
