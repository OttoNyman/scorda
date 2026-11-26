package org.tasks.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tasks.R
import org.tasks.quickalarm.QUICK_ALARM_PRESETS_MINUTES
import org.tasks.quickalarm.addQuickAlarmPreset
import org.tasks.quickalarm.formatQuickAlarmOffset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAlarmDialog(
    alarmLevelEnabled: Boolean,
    canExactAlarms: Boolean,
    ringsInSilent: Boolean,
    notificationsEnabled: Boolean,
    confirmEnabled: Boolean = true,
    onOpenAlarmLevelSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenSilentBypassSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onConfirm: (title: String?, intervalMinutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var intervalMinutes by rememberSaveable { mutableIntStateOf(0) }
    var title by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_alarm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = formatQuickAlarmOffset(intervalMinutes),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QUICK_ALARM_PRESETS_MINUTES.forEach { preset ->
                        TextButton(
                            onClick = {
                                intervalMinutes = addQuickAlarmPreset(intervalMinutes, preset)
                            },
                        ) {
                            Text(stringResource(R.string.quick_alarm_preset_min, preset))
                        }
                    }
                    TextButton(onClick = { intervalMinutes = 0 }) {
                        Text(stringResource(R.string.quick_alarm_reset))
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.quick_alarm_title_hint)) },
                )
                if (!notificationsEnabled) {
                    WarningRow(
                        text = stringResource(R.string.quick_alarm_warn_notifications),
                        onClick = onOpenNotificationSettings,
                    )
                }
                if (!alarmLevelEnabled) {
                    WarningRow(
                        text = stringResource(R.string.quick_alarm_warn_level),
                        onClick = onOpenAlarmLevelSettings,
                    )
                }
                if (!canExactAlarms) {
                    WarningRow(
                        text = stringResource(R.string.quick_alarm_warn_exact),
                        onClick = onOpenExactAlarmSettings,
                    )
                }
                if (!ringsInSilent) {
                    WarningRow(
                        text = stringResource(R.string.quick_alarm_warn_silent),
                        onClick = onOpenSilentBypassSettings,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmEnabled && intervalMinutes > 0,
                onClick = {
                    onConfirm(title.trim().ifEmpty { null }, intervalMinutes)
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun WarningRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}
