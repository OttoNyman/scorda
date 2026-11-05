package org.tasks.compose.settings

import org.tasks.themes.TasksIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.tasks.notifications.CheckState
import org.tasks.notifications.ModeLine
import org.tasks.notifications.ReminderCheck
import org.tasks.notifications.ReminderCheckId
import tasks.kmp.generated.resources.EPr_default_reminders_title
import tasks.kmp.generated.resources.EPr_default_reminders_mode_title
import tasks.kmp.generated.resources.alert_super_bypass
import tasks.kmp.generated.resources.alert_super_bypass_summary
import tasks.kmp.generated.resources.alarms_can_be_late
import tasks.kmp.generated.resources.more_reminder_settings
import tasks.kmp.generated.resources.reminder_check_autostart
import tasks.kmp.generated.resources.reminder_check_background
import tasks.kmp.generated.resources.reminder_check_battery
import tasks.kmp.generated.resources.reminder_check_exact
import tasks.kmp.generated.resources.reminder_check_notifications
import tasks.kmp.generated.resources.reminder_check_popups
import tasks.kmp.generated.resources.reminder_check_power_save
import tasks.kmp.generated.resources.reminder_check_recents
import tasks.kmp.generated.resources.reminder_check_recents_hint
import tasks.kmp.generated.resources.reminder_sound
import tasks.kmp.generated.resources.reminder_test_button
import tasks.kmp.generated.resources.reminder_test_pending
import tasks.kmp.generated.resources.reminders_are_on
import tasks.kmp.generated.resources.reminders_delay_subtitle
import tasks.kmp.generated.resources.reminders_mode_inexact
import tasks.kmp.generated.resources.reminders_mode_oem
import tasks.kmp.generated.resources.reminders_mode_oem_inexact
import tasks.kmp.generated.resources.reminders_mode_on_time
import tasks.kmp.generated.resources.reminders_not_working
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.tasks.kmp.formatTime
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.time.withMillisOfDay
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.EPr_voiceRemindersEnabled_desc_enabled
import tasks.kmp.generated.resources.EPr_voiceRemindersEnabled_title
import tasks.kmp.generated.resources.add_default_reminders
import tasks.kmp.generated.resources.bundle_notifications
import tasks.kmp.generated.resources.bundle_notifications_summary
import tasks.kmp.generated.resources.completion_sound
import tasks.kmp.generated.resources.default_reminder
import tasks.kmp.generated.resources.disable_battery_optimizations
import tasks.kmp.generated.resources.enabled
import tasks.kmp.generated.resources.more_settings
import tasks.kmp.generated.resources.notification_disable_battery_optimizations_description
import tasks.kmp.generated.resources.notification_troubleshooting_summary
import tasks.kmp.generated.resources.notifications
import tasks.kmp.generated.resources.persistent_notifications
import tasks.kmp.generated.resources.persistent_notifications_description
import tasks.kmp.generated.resources.quiet_hours
import tasks.kmp.generated.resources.quiet_hours_in_effect
import tasks.kmp.generated.resources.quiet_hours_summary
import tasks.kmp.generated.resources.rmd_EPr_quiet_hours_end_title
import tasks.kmp.generated.resources.rmd_EPr_quiet_hours_start_title
import tasks.kmp.generated.resources.rmd_EPr_rmd_time_title
import tasks.kmp.generated.resources.rmd_time_enabled_off
import tasks.kmp.generated.resources.rmd_time_enabled_on
import tasks.kmp.generated.resources.swipe_to_snooze_description
import tasks.kmp.generated.resources.swipe_to_snooze_time_1_hour
import tasks.kmp.generated.resources.swipe_to_snooze_time_15_minutes
import tasks.kmp.generated.resources.swipe_to_snooze_time_24_hours
import tasks.kmp.generated.resources.swipe_to_snooze_time_30_minutes
import tasks.kmp.generated.resources.swipe_to_snooze_time_description
import tasks.kmp.generated.resources.swipe_to_snooze_time_immediately
import tasks.kmp.generated.resources.swipe_to_snooze_title
import tasks.kmp.generated.resources.troubleshooting
import tasks.kmp.generated.resources.wearable_notifications
import tasks.kmp.generated.resources.wearable_notifications_summary

val SnoozeTimes: List<Pair<Int, StringResource>> = listOf(
    0 to Res.string.swipe_to_snooze_time_immediately,
    15 to Res.string.swipe_to_snooze_time_15_minutes,
    30 to Res.string.swipe_to_snooze_time_30_minutes,
    60 to Res.string.swipe_to_snooze_time_1_hour,
    1440 to Res.string.swipe_to_snooze_time_24_hours,
)

private fun snoozeLabel(minutes: Int): StringResource =
    SnoozeTimes.firstOrNull { it.first == minutes }?.second ?: SnoozeTimes.first().second

private fun timeSummary(millisOfDay: Int, is24HourFormat: Boolean): String =
    formatTime(currentTimeMillis().withMillisOfDay(millisOfDay), is24HourFormat)

@Composable
fun NotificationsScreen(
    is24HourFormat: Boolean,
    isCurrentlyQuietHours: Boolean,
    showTroubleshooting: Boolean,
    showBatteryOptimization: Boolean,
    showNotificationsEnabled: Boolean,
    showCompletionSound: Boolean,
    completionSoundName: String,
    showOngoingNotifications: Boolean,
    showBundleNotifications: Boolean,
    showVoiceReminders: Boolean,
    showSystemNotificationSettings: Boolean,
    moreSettingsSummary: String?,
    showSwipeToSnooze: Boolean,
    notificationsEnabled: Boolean,
    persistentEnabled: Boolean,
    wearableEnabled: Boolean,
    bundleEnabled: Boolean,
    voiceEnabled: Boolean,
    swipeToSnoozeEnabled: Boolean,
    swipeToSnoozeMinutes: Int,
    defaultRemindersEnabled: Boolean,
    defaultReminderTime: Int,
    quietHoursEnabled: Boolean,
    quietHoursStart: Int,
    quietHoursEnd: Int,
    onTroubleshooting: () -> Unit,
    onBatteryOptimization: () -> Unit,
    onNotificationsEnabled: (Boolean) -> Unit,
    onCompletionSound: () -> Unit,
    onPersistent: (Boolean) -> Unit,
    onWearable: (Boolean) -> Unit,
    onBundle: (Boolean) -> Unit,
    onVoice: (Boolean) -> Unit,
    onMoreSettings: () -> Unit,
    onSwipeToSnooze: (Boolean) -> Unit,
    onSnoozeTime: () -> Unit,
    onDefaultReminders: (Boolean) -> Unit,
    onReminderTime: () -> Unit,
    onQuietHours: (Boolean) -> Unit,
    onQuietStart: () -> Unit,
    onQuietEnd: () -> Unit,
    showHealth: Boolean = false,
    healthChecks: List<ReminderCheck> = emptyList(),
    modeLine: ModeLine = ModeLine.ON_TIME,
    healthExpanded: Boolean = false,
    testPending: Boolean = false,
    onToggleHealth: () -> Unit = {},
    onHealthCheck: (ReminderCheckId) -> Unit = {},
    onReminderTest: () -> Unit = {},
    moreExpanded: Boolean = false,
    onToggleMore: () -> Unit = {},
    defaultAlarmsSummary: String = "",
    onDefaultAlarms: () -> Unit = {},
    defaultRingMode: Int = 0,
    onRingMode: () -> Unit = {},
    superBypassSilent: Boolean = false,
    onSuperBypass: (Boolean) -> Unit = {},
    reminderSoundName: String = "",
    onReminderSound: () -> Unit = {},
    bottomInsets: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))

        if (showHealth) {
            ReminderHealthCard(
                checks = healthChecks,
                modeLine = modeLine,
                expanded = healthExpanded,
                testPending = testPending,
                onToggle = onToggleHealth,
                onCheck = onHealthCheck,
                onTest = onReminderTest,
            )
        }

        if (isCurrentlyQuietHours) {
            DangerCard(
                icon = TasksIcons.NOTIFICATIONS_OFF,
                title = stringResource(Res.string.quiet_hours_in_effect),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = SettingsContentPadding),
            )
            Spacer(modifier = Modifier.height(SettingsContentPadding))
        }

        SectionHeader(
            stringResource(Res.string.quiet_hours),
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        QuietHoursBlock(
            is24HourFormat = is24HourFormat,
            quietHoursEnabled = quietHoursEnabled,
            quietHoursStart = quietHoursStart,
            quietHoursEnd = quietHoursEnd,
            onQuietHours = onQuietHours,
            onQuietStart = onQuietStart,
            onQuietEnd = onQuietEnd,
        )

        SectionHeader(
            stringResource(Res.string.default_reminder),
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                SwitchPreferenceRow(
                    title = stringResource(Res.string.add_default_reminders),
                    summary = if (defaultRemindersEnabled) {
                        stringResource(Res.string.rmd_time_enabled_on)
                    } else {
                        stringResource(Res.string.rmd_time_enabled_off)
                    },
                    checked = defaultRemindersEnabled,
                    onCheckedChange = onDefaultReminders,
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                PreferenceRow(
                    title = stringResource(Res.string.rmd_EPr_rmd_time_title),
                    summary = timeSummary(defaultReminderTime, is24HourFormat),
                    onClick = onReminderTime,
                )
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                PreferenceRow(
                    title = stringResource(Res.string.EPr_default_reminders_title),
                    summary = defaultAlarmsSummary,
                    onClick = onDefaultAlarms,
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(Res.string.EPr_default_reminders_mode_title),
                    summary = ringSummary(defaultRingMode),
                    onClick = onRingMode,
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                SwitchPreferenceRow(
                    title = stringResource(Res.string.alert_super_bypass),
                    summary = stringResource(Res.string.alert_super_bypass_summary),
                    checked = superBypassSilent,
                    onCheckedChange = onSuperBypass,
                )
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        SettingsItemCard(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
            PreferenceRow(
                title = stringResource(Res.string.more_reminder_settings),
                onClick = onToggleMore,
            )
        }
        if (moreExpanded) {
            Spacer(modifier = Modifier.height(SettingsContentPadding))
            CardGroup(
                listOfNotNull(
                    NotificationRow.ENABLED.takeIf { showNotificationsEnabled },
                    NotificationRow.COMPLETION_SOUND.takeIf { showCompletionSound },
                    NotificationRow.PERSISTENT.takeIf { showOngoingNotifications },
                    NotificationRow.WEARABLE.takeIf { showOngoingNotifications },
                    NotificationRow.BUNDLE.takeIf { showBundleNotifications },
                    NotificationRow.VOICE.takeIf { showVoiceReminders },
                    NotificationRow.REMINDER_SOUND,
                    NotificationRow.SYSTEM.takeIf { showSystemNotificationSettings },
                )
            ) { row ->
                when (row) {
                    NotificationRow.ENABLED ->
                        SwitchPreferenceRow(
                            title = stringResource(Res.string.notifications),
                            checked = notificationsEnabled,
                            onCheckedChange = onNotificationsEnabled,
                        )
                    NotificationRow.COMPLETION_SOUND ->
                        PreferenceRow(
                            title = stringResource(Res.string.completion_sound),
                            summary = completionSoundName,
                            onClick = onCompletionSound,
                        )
                    NotificationRow.PERSISTENT ->
                        SwitchPreferenceRow(
                            title = stringResource(Res.string.persistent_notifications),
                            summary = stringResource(Res.string.persistent_notifications_description),
                            checked = persistentEnabled,
                            onCheckedChange = onPersistent,
                        )
                    NotificationRow.WEARABLE ->
                        SwitchPreferenceRow(
                            title = stringResource(Res.string.wearable_notifications),
                            summary = stringResource(Res.string.wearable_notifications_summary),
                            checked = wearableEnabled,
                            onCheckedChange = onWearable,
                        )
                    NotificationRow.BUNDLE ->
                        SwitchPreferenceRow(
                            title = stringResource(Res.string.bundle_notifications),
                            summary = stringResource(Res.string.bundle_notifications_summary),
                            checked = bundleEnabled,
                            onCheckedChange = onBundle,
                        )
                    NotificationRow.VOICE ->
                        SwitchPreferenceRow(
                            title = stringResource(Res.string.EPr_voiceRemindersEnabled_title),
                            summary = stringResource(Res.string.EPr_voiceRemindersEnabled_desc_enabled),
                            checked = voiceEnabled,
                            onCheckedChange = onVoice,
                        )
                    NotificationRow.REMINDER_SOUND ->
                        PreferenceRow(
                            title = stringResource(Res.string.reminder_sound),
                            summary = reminderSoundName,
                            onClick = onReminderSound,
                        )
                    NotificationRow.SYSTEM ->
                        PreferenceRow(
                            title = stringResource(Res.string.more_settings),
                            summary = moreSettingsSummary,
                            icon = TasksIcons.OPEN_IN_NEW,
                            onClick = onMoreSettings,
                        )
                }
            }
            if (showSwipeToSnooze) {
                SectionHeader(
                    stringResource(Res.string.swipe_to_snooze_title),
                    modifier = Modifier.padding(horizontal = SettingsContentPadding),
                )
                Column(
                    modifier = Modifier.padding(horizontal = SettingsContentPadding),
                    verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
                ) {
                    SettingsItemCard(position = CardPosition.First) {
                        SwitchPreferenceRow(
                            title = stringResource(Res.string.enabled),
                            checked = swipeToSnoozeEnabled,
                            onCheckedChange = onSwipeToSnooze,
                        )
                    }
                    SettingsItemCard(position = CardPosition.Last) {
                        PreferenceRow(
                            title = stringResource(Res.string.swipe_to_snooze_description),
                            summary = stringResource(
                                Res.string.swipe_to_snooze_time_description,
                                stringResource(snoozeLabel(swipeToSnoozeMinutes)),
                            ),
                            enabled = swipeToSnoozeEnabled,
                            onClick = onSnoozeTime,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        bottomInsets()
    }
}

private enum class TroubleshootingRow {
    TROUBLESHOOTING,
    BATTERY_OPTIMIZATION,
}

private enum class NotificationRow {
    ENABLED,
    COMPLETION_SOUND,
    PERSISTENT,
    WEARABLE,
    BUNDLE,
    VOICE,
    REMINDER_SOUND,
    SYSTEM,
}

@Composable
private fun <T : Any> CardGroup(rows: List<T>, row: @Composable (T) -> Unit) {
    if (rows.isEmpty()) return
    Column(
        modifier = Modifier.padding(horizontal = SettingsContentPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
    ) {
        rows.forEachIndexed { index, item ->
            key(item) {
                SettingsItemCard(position = CardPosition.forIndex(index, rows.size)) {
                    row(item)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(SettingsContentPadding))
}

@Composable
fun SnoozeTimeDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.swipe_to_snooze_description)) },
        text = {
            Column {
                SnoozeTimes.forEach { (minutes, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = minutes == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(minutes) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = minutes == selected,
                            onClick = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
fun RingLevelDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.EPr_default_reminders_mode_title)) },
        text = {
            Column {
                RingModeOptions.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mode == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(mode) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == selected, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ringSummary(mode: Int): String {
    val label = RingModeOptions.firstOrNull { it.first == mode }?.second
        ?: RingModeOptions.first { it.first == 0 }.second
    return stringResource(label)
}

@Composable
private fun QuietHoursBlock(
    is24HourFormat: Boolean,
    quietHoursEnabled: Boolean,
    quietHoursStart: Int,
    quietHoursEnd: Int,
    onQuietHours: (Boolean) -> Unit,
    onQuietStart: () -> Unit,
    onQuietEnd: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = SettingsContentPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
    ) {
        SettingsItemCard(position = CardPosition.First) {
            SwitchPreferenceRow(
                title = stringResource(Res.string.enabled),
                summary = stringResource(Res.string.quiet_hours_summary),
                checked = quietHoursEnabled,
                onCheckedChange = onQuietHours,
            )
        }
        SettingsItemCard(position = CardPosition.Middle) {
            PreferenceRow(
                title = stringResource(Res.string.rmd_EPr_quiet_hours_start_title),
                summary = timeSummary(quietHoursStart, is24HourFormat),
                enabled = quietHoursEnabled,
                onClick = onQuietStart,
            )
        }
        SettingsItemCard(position = CardPosition.Last) {
            PreferenceRow(
                title = stringResource(Res.string.rmd_EPr_quiet_hours_end_title),
                summary = timeSummary(quietHoursEnd, is24HourFormat),
                enabled = quietHoursEnabled,
                onClick = onQuietEnd,
            )
        }
    }
    Spacer(modifier = Modifier.height(SettingsContentPadding))
}

@Composable
private fun ReminderHealthCard(
    checks: List<ReminderCheck>,
    modeLine: ModeLine,
    expanded: Boolean,
    testPending: Boolean,
    onToggle: () -> Unit,
    onCheck: (ReminderCheckId) -> Unit,
    onTest: () -> Unit,
) {
    val problem = checks.any { it.state == CheckState.PROBLEM } || modeLine != ModeLine.ON_TIME
    Column(
        modifier = Modifier.padding(horizontal = SettingsContentPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
    ) {
        SettingsItemCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                PreferenceRow(
                    title = stringResource(
                        if (problem) Res.string.reminders_not_working else Res.string.reminders_are_on
                    ),
                    summary = stringResource(Res.string.reminders_delay_subtitle),
                    onClick = onToggle,
                )
                Text(
                    text = stringResource(modeText(modeLine)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                TextButton(onClick = onTest, enabled = !testPending, modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        stringResource(
                            if (testPending) Res.string.reminder_test_pending else Res.string.reminder_test_button
                        )
                    )
                }
            }
        }
        if (expanded) {
            checks.forEach { check ->
                SettingsItemCard {
                    PreferenceRow(
                        title = stringResource(checkTitle(check.id)),
                        summary = if (check.id == ReminderCheckId.RECENTS_LOCK) {
                            stringResource(Res.string.reminder_check_recents_hint)
                        } else {
                            null
                        },
                        onClick = if (check.state == CheckState.MANUAL) null else ({ onCheck(check.id) }),
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(SettingsContentPadding))
}

private fun modeText(modeLine: ModeLine) = when (modeLine) {
    ModeLine.ON_TIME -> Res.string.reminders_mode_on_time
    ModeLine.INEXACT -> Res.string.reminders_mode_inexact
    ModeLine.OEM_SAFETY -> Res.string.reminders_mode_oem
    ModeLine.OEM_AND_INEXACT -> Res.string.reminders_mode_oem_inexact
}

private fun checkTitle(id: ReminderCheckId) = when (id) {
    ReminderCheckId.NOTIFICATIONS -> Res.string.reminder_check_notifications
    ReminderCheckId.EXACT_ALARMS -> Res.string.reminder_check_exact
    ReminderCheckId.BATTERY -> Res.string.reminder_check_battery
    ReminderCheckId.POWER_SAVE -> Res.string.reminder_check_power_save
    ReminderCheckId.AUTOSTART -> Res.string.reminder_check_autostart
    ReminderCheckId.BACKGROUND -> Res.string.reminder_check_background
    ReminderCheckId.POPUPS -> Res.string.reminder_check_popups
    ReminderCheckId.RECENTS_LOCK -> Res.string.reminder_check_recents
}
