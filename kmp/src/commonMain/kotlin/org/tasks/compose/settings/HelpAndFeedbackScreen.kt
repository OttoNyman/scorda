package org.tasks.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.tasks.themes.TasksIcons
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.contact_developer
import tasks.kmp.generated.resources.documentation
import tasks.kmp.generated.resources.ic_octocat
import tasks.kmp.generated.resources.legal
import tasks.kmp.generated.resources.license_summary
import tasks.kmp.generated.resources.open_source
import tasks.kmp.generated.resources.privacy_policy
import tasks.kmp.generated.resources.rate_tasks
import tasks.kmp.generated.resources.send_anonymous_statistics
import tasks.kmp.generated.resources.send_anonymous_statistics_summary
import tasks.kmp.generated.resources.send_application_logs
import tasks.kmp.generated.resources.source_code
import tasks.kmp.generated.resources.support
import tasks.kmp.generated.resources.terms_of_service
import tasks.kmp.generated.resources.third_party_licenses
import tasks.kmp.generated.resources.version

@Composable
fun HelpAndFeedbackScreen(
    versionName: String,
    isGooglePlay: Boolean,
    showTermsOfService: Boolean,
    showSendLogs: Boolean,
    showCollectStatistics: Boolean,
    collectStatistics: Boolean,
    onVersionClick: () -> Unit,
    onRateTasks: () -> Unit,
    onDocumentation: () -> Unit,
    onContactDeveloper: () -> Unit,
    onSendLogs: () -> Unit,
    onSourceCode: () -> Unit,
    onThirdPartyLicenses: () -> Unit,
    onTermsOfService: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onCollectStatisticsChanged: (Boolean) -> Unit,
    bottomInsets: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))

        // Top items
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(
                position = if (isGooglePlay) CardPosition.First else CardPosition.Only,
            ) {
                PreferenceRow(
                    title = stringResource(Res.string.version),
                    icon = TasksIcons.INFO,
                    summary = versionName,
                    onClick = onVersionClick,
                )
            }
            if (isGooglePlay) {
                SettingsItemCard(position = CardPosition.Last) {
                    PreferenceRow(
                        title = stringResource(Res.string.rate_tasks),
                        icon = TasksIcons.STAR_BORDER,
                        onClick = onRateTasks,
                    )
                }
            }
        }

        // Support section
        SectionHeader(
            stringResource(Res.string.support),
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                PreferenceRow(
                    title = stringResource(Res.string.documentation),
                    icon = TasksIcons.HELP,
                    onClick = onDocumentation,
                )
            }
            SettingsItemCard(
                position = if (showSendLogs) CardPosition.Middle else CardPosition.Last,
            ) {
                PreferenceRow(
                    title = stringResource(Res.string.contact_developer),
                    icon = TasksIcons.EMAIL,
                    onClick = onContactDeveloper,
                )
            }
            if (showSendLogs) {
                SettingsItemCard(position = CardPosition.Last) {
                    PreferenceRow(
                        title = stringResource(Res.string.send_application_logs),
                        icon = TasksIcons.ATTACHMENT,
                        onClick = onSendLogs,
                    )
                }
            }
        }

        // Open Source section
        SectionHeader(
            stringResource(Res.string.open_source),
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(
                position = if (isGooglePlay) CardPosition.First else CardPosition.Only,
            ) {
                PreferenceRow(
                    title = stringResource(Res.string.source_code),
                    iconDrawable = Res.drawable.ic_octocat,
                    summary = stringResource(Res.string.license_summary),
                    onClick = onSourceCode,
                )
            }
            if (isGooglePlay) {
                SettingsItemCard(position = CardPosition.Last) {
                    PreferenceRow(
                        title = stringResource(Res.string.third_party_licenses),
                        icon = TasksIcons.GAVEL,
                        onClick = onThirdPartyLicenses,
                    )
                }
            }
        }

        // Legal section
        SectionHeader(
            stringResource(Res.string.legal),
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            if (showTermsOfService) {
                SettingsItemCard(position = CardPosition.First) {
                    PreferenceRow(
                        title = stringResource(Res.string.terms_of_service),
                        icon = TasksIcons.GAVEL,
                        onClick = onTermsOfService,
                    )
                }
                SettingsItemCard(position = CardPosition.Last) {
                    PreferenceRow(
                        title = stringResource(Res.string.privacy_policy),
                        icon = TasksIcons.PERM_IDENTITY,
                        onClick = onPrivacyPolicy,
                    )
                }
            } else {
                SettingsItemCard {
                    PreferenceRow(
                        title = stringResource(Res.string.privacy_policy),
                        icon = TasksIcons.PERM_IDENTITY,
                        onClick = onPrivacyPolicy,
                    )
                }
            }
        }

        // Statistics toggle
        if (showCollectStatistics) {
            Spacer(modifier = Modifier.height(SettingsContentPadding))
            SettingsItemCard(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
                SwitchPreferenceRow(
                    title = stringResource(Res.string.send_anonymous_statistics),
                    icon = TasksIcons.BUG_REPORT,
                    summary = stringResource(Res.string.send_anonymous_statistics_summary),
                    checked = collectStatistics,
                    onCheckedChange = onCollectStatisticsChanged,
                )
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        bottomInsets()
    }
}
