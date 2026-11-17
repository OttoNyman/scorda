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
import org.jetbrains.compose.resources.stringResource as kmpStringResource
import org.tasks.R
import org.tasks.themes.TasksIcons
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.reset_trusted_certificates
import tasks.kmp.generated.resources.reset_trusted_certificates_summary

@Composable
fun AdvancedScreen(
    attachmentDirSummary: String,
    badgesEnabled: Boolean,
    badgeFilterName: String,
    onAttachmentDir: () -> Unit,
    onBadges: (Boolean) -> Unit,
    onBadgeList: () -> Unit,
    onResetPreferences: () -> Unit,
    onResetTrustedCertificates: () -> Unit,
    onDeleteTaskData: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))

        SettingsItemCard(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
            PreferenceRow(
                title = stringResource(R.string.attachment_directory),
                icon = TasksIcons.ATTACHMENT,
                summary = attachmentDirSummary,
                onClick = onAttachmentDir,
            )
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))

        // Badges section
        SectionHeader(
            R.string.badges,
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                SwitchPreferenceRow(
                    title = stringResource(R.string.enabled),
                    summary = stringResource(R.string.badges_description),
                    checked = badgesEnabled,
                    onCheckedChange = onBadges,
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                PreferenceRow(
                    title = stringResource(R.string.list),
                    summary = badgeFilterName,
                    enabled = badgesEnabled,
                    onClick = onBadgeList,
                )
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))

        // Danger zone island
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                PreferenceRow(
                    title = stringResource(R.string.EPr_reset_preferences),
                    onClick = onResetPreferences,
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = kmpStringResource(Res.string.reset_trusted_certificates),
                    summary = kmpStringResource(Res.string.reset_trusted_certificates_summary),
                    onClick = onResetTrustedCertificates,
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                PreferenceRow(
                    title = stringResource(R.string.EPr_delete_task_data),
                    icon = TasksIcons.DELETE,
                    onClick = onDeleteTaskData,
                )
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
