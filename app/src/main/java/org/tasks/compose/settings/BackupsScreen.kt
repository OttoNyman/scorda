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
import androidx.compose.ui.res.stringResource
import org.tasks.R
import org.tasks.themes.TasksIcons

/** Account, Drive backup and every kind of local backup on one screen. */
@Composable
fun BackupsScreen(
    lastBackupSummary: String,
    lastDriveBackupSummary: String,
    lastAndroidBackupSummary: String,
    onSaveFile: () -> Unit,
    onImportBackup: () -> Unit,
    onBackupHelp: () -> Unit,
    accountEmail: String? = null,
    onLogout: () -> Unit = {},
    driveDailyEnabled: Boolean = false,
    expiredMessage: String? = null,
    onDriveDaily: (Boolean) -> Unit = {},
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

        SectionHeader(
            R.string.backup_section,
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            onClick = onBackupHelp,
        )
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            // Always on: shown so people know it exists, explained under the header.
            SettingsItemCard(position = CardPosition.First) {
                SwitchPreferenceRow(
                    title = stringResource(R.string.android_auto_backup),
                    summary = lastAndroidBackupSummary,
                    checked = true,
                    enabled = false,
                    onCheckedChange = {},
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.save_to_file),
                    summary = lastBackupSummary,
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
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
