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

@Composable
fun TaskEditScreen(
    showLinks: Boolean,
    backButtonSaves: Boolean,
    showComments: Boolean,
    showCommentsRow: Boolean,
    morningSummary: String,
    eveningSummary: String,
    onShowLinks: (Boolean) -> Unit,
    onBackButtonSaves: (Boolean) -> Unit,
    onShowComments: (Boolean) -> Unit,
    onMorning: () -> Unit,
    onEvening: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))

        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            val total = 2 + if (showCommentsRow) 1 else 0
            var i = 0

            SettingsItemCard(position = CardPosition.forIndex(i++, total)) {
                SwitchPreferenceRow(
                    title = stringResource(R.string.linkify),
                    summary = stringResource(R.string.linkify_description),
                    checked = showLinks,
                    onCheckedChange = onShowLinks,
                )
            }
            SettingsItemCard(position = CardPosition.forIndex(i++, total)) {
                SwitchPreferenceRow(
                    title = stringResource(R.string.back_button_saves_task),
                    checked = backButtonSaves,
                    onCheckedChange = onBackButtonSaves,
                )
            }
            if (showCommentsRow) {
                SettingsItemCard(position = CardPosition.forIndex(i, total)) {
                    SwitchPreferenceRow(
                        title = stringResource(R.string.EPr_show_task_edit_comments),
                        checked = showComments,
                        onCheckedChange = onShowComments,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))

        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                PreferenceRow(
                    title = stringResource(R.string.date_shortcut_morning),
                    summary = morningSummary,
                    onClick = onMorning,
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                PreferenceRow(
                    title = stringResource(R.string.date_shortcut_evening),
                    summary = eveningSummary,
                    onClick = onEvening,
                )
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
