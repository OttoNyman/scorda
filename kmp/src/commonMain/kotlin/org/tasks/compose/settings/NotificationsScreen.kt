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
import org.tasks.notifications.AlertLevel
import org.tasks.viewmodel.LockScreenAsk
import tasks.kmp.generated.resources.alarm_on_lock_screen
import tasks.kmp.generated.resources.alarm_on_lock_screen_allow
import tasks.kmp.generated.resources.alarm_on_lock_screen_ask
import tasks.kmp.generated.resources.alarm_on_lock_screen_ask_title
import tasks.kmp.generated.resources.alarm_on_lock_screen_decline
import tasks.kmp.generated.resources.alarm_on_lock_screen_off
import tasks.kmp.generated.resources.alarm_on_lock_screen_denied
import tasks.kmp.generated.resources.alarm_on_lock_screen_on
import tasks.kmp.generated.resources.alarm_on_lock_screen_xiaomi
import tasks.kmp.generated.resources.alarm_on_lock_screen_xiaomi_title
import tasks.kmp.generated.resources.alarm_need_exact
import tasks.kmp.generated.resources.alarm_need_exact_title
import tasks.kmp.generated.resources.notifications_master_summary
import tasks.kmp.generated.resources.priority_high_summary
import tasks.kmp.generated.resources.priority_medium_summary
import tasks.kmp.generated.resources.priority_none_summary
import tasks.kmp.generated.resources.alarm_ring_duration
import tasks.kmp.generated.resources.alarm_ring_duration_value
import tasks.kmp.generated.resources.alert_caption_alarm
import tasks.kmp.generated.resources.alert_caption_once
import tasks.kmp.generated.resources.alert_caption_persistent
import tasks.kmp.generated.resources.filter_no_priority
import tasks.kmp.generated.resources.priority_always_on
import tasks.kmp.generated.resources.priority_high
import tasks.kmp.generated.resources.priority_low
import tasks.kmp.generated.resources.priority_medium
import tasks.kmp.generated.resources.alert_super_bypass
import tasks.kmp.generated.resources.alert_super_bypass_summary
import tasks.kmp.generated.resources.cancel
import tasks.kmp.generated.resources.ok
import tasks.kmp.generated.resources.priority_level_disable_message
import tasks.kmp.generated.resources.priority_levels_hint
import tasks.kmp.generated.resources.priority_levels_title
import tasks.kmp.generated.resources.sound_by_priority
import tasks.kmp.generated.resources.alarms_can_be_late
import tasks.kmp.generated.resources.more_reminder_settings
import tasks.kmp.generated.resources.reminder_check_autostart
import tasks.kmp.generated.resources.reminder_check_background
import tasks.kmp.generated.resources.reminder_check_battery
import tasks.kmp.generated.resources.reminder_check_exact
import tasks.kmp.generated.resources.reminder_check_notifications
import tasks.kmp.generated.resources.reminder_check_full_screen
import tasks.kmp.generated.resources.reminder_check_lock_screen
import tasks.kmp.generated.resources.reminder_check_popups
import tasks.kmp.generated.resources.reminder_check_power_save
import tasks.kmp.generated.resources.reminder_check_full_screen_summary
import tasks.kmp.generated.resources.reminder_check_lock_screen_summary
import tasks.kmp.generated.resources.reminder_check_ok
import tasks.kmp.generated.resources.reminder_check_problem
import tasks.kmp.generated.resources.reminder_check_recents
import tasks.kmp.generated.resources.reminder_check_recents_hint
import tasks.kmp.generated.resources.reminder_check_unknown
import tasks.kmp.generated.resources.reminder_test_button
import tasks.kmp.generated.resources.reminder_test_countdown
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
import tasks.kmp.generated.resources.more_settings
import tasks.kmp.generated.resources.notification_disable_battery_optimizations_description
import tasks.kmp.generated.resources.notification_troubleshooting_summary
import tasks.kmp.generated.resources.notifications
import tasks.kmp.generated.resources.persistent_notifications
import tasks.kmp.generated.resources.persistent_notifications_description
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
import tasks.kmp.generated.resources.swipe_title
import tasks.kmp.generated.resources.swipe_action
import tasks.kmp.generated.resources.swipe_snooze
import tasks.kmp.generated.resources.swipe_dismiss
import tasks.kmp.generated.resources.swipe_complete
import tasks.kmp.generated.resources.swipe_snooze_help
import tasks.kmp.generated.resources.swipe_dismiss_help
import tasks.kmp.generated.resources.swipe_complete_help
import tasks.kmp.generated.resources.swipe_help_always
import tasks.kmp.generated.resources.swipe_to_snooze_time_5_minutes
import tasks.kmp.generated.resources.ok
import org.tasks.preferences.SwipeAction
import tasks.kmp.generated.resources.troubleshooting
import tasks.kmp.generated.resources.wearable_notifications
import tasks.kmp.generated.resources.wearable_notifications_summary

val SnoozeTimes: List<Pair<Int, StringResource>> = listOf(
    0 to Res.string.swipe_to_snooze_time_immediately,
    5 to Res.string.swipe_to_snooze_time_5_minutes,
    15 to Res.string.swipe_to_snooze_time_15_minutes,
    30 to Res.string.swipe_to_snooze_time_30_minutes,
    60 to Res.string.swipe_to_snooze_time_1_hour,
    1440 to Res.string.swipe_to_snooze_time_24_hours,
)

private fun snoozeLabel(minutes: Int): StringResource =
    SnoozeTimes.firstOrNull { it.first == minutes }?.second ?: SnoozeTimes.first().second

private fun timeSummary(millisOfDay: Int, is24HourFormat: Boolean): String =
    formatTime(currentTimeMillis().withMillisOfDay(millisOfDay), is24HourFormat)

data class PendingLevelDisable(
    val level: AlertLevel,
    val tasks: Int,
    val memos: Int,
)

@Composable
fun NotificationsScreen(
    is24HourFormat: Boolean,
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
    swipeAction: SwipeAction,
    swipeToSnoozeMinutes: Int,
    defaultRemindersEnabled: Boolean,
    defaultReminderTime: Int,
    onTroubleshooting: () -> Unit,
    onBatteryOptimization: () -> Unit,
    onNotificationsEnabled: (Boolean) -> Unit,
    onCompletionSound: () -> Unit,
    onPersistent: (Boolean) -> Unit,
    onWearable: (Boolean) -> Unit,
    onBundle: (Boolean) -> Unit,
    onVoice: (Boolean) -> Unit,
    onMoreSettings: () -> Unit,
    onSwipeAction: () -> Unit,
    onSwipeHelp: () -> Unit,
    onSnoozeTime: () -> Unit,
    onDefaultReminders: (Boolean) -> Unit,
    onReminderTime: () -> Unit,
    showHealth: Boolean = false,
    healthChecks: List<ReminderCheck> = emptyList(),
    modeLine: ModeLine = ModeLine.ON_TIME,
    healthExpanded: Boolean = false,
    testPending: Boolean = false,
    testSecondsLeft: Int = 0,
    onToggleHealth: () -> Unit = {},
    onHealthCheck: (ReminderCheckId) -> Unit = {},
    onReminderTest: () -> Unit = {},
    moreExpanded: Boolean = false,
    onToggleMore: () -> Unit = {},
    showPersistentSound: Boolean = false,
    showAlarmSound: Boolean = true,
    onceSoundName: String = "",
    persistentSoundName: String = "",
    alarmSoundName: String = "",
    onLevelSound: (AlertLevel) -> Unit = {},
    alarmRingSeconds: Int = 60,
    onAlarmDuration: () -> Unit = {},
    superBypassSilent: Boolean = false,
    onSuperBypass: (Boolean) -> Unit = {},
    alarmOnLockScreen: Boolean = false,
    onAlarmLockScreen: (Boolean) -> Unit = {},
    lockScreenAsk: LockScreenAsk? = null,
    onConfirmAlarmLockScreen: () -> Unit = {},
    onDismissAlarmLockScreen: () -> Unit = {},
    lockScreenDenied: Boolean = false,
    onDismissLockScreenDenied: () -> Unit = {},
    askExactAlarms: Boolean = false,
    onConfirmExactAlarms: () -> Unit = {},
    onDismissExactAlarms: () -> Unit = {},
    levelSilent: Boolean = false,
    levelPersistent: Boolean = false,
    levelAlarm: Boolean = true,
    onLevelChange: (AlertLevel, Boolean) -> Unit = { _, _ -> },
    pendingLevelDisable: PendingLevelDisable? = null,
    onConfirmLevelDisable: () -> Unit = {},
    onDismissLevelDisable: () -> Unit = {},
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
                testSecondsLeft = testSecondsLeft,
                onToggle = onToggleHealth,
                onCheck = onHealthCheck,
                onTest = onReminderTest,
            )
        }
        if (showSystemNotificationSettings) {
            SettingsItemCard(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
                PreferenceRow(
                    title = stringResource(Res.string.more_settings),
                    summary = moreSettingsSummary,
                    icon = TasksIcons.OPEN_IN_NEW,
                    onClick = onMoreSettings,
                )
            }
            Spacer(modifier = Modifier.height(SettingsContentPadding))
        }

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
        SectionHeader(
            stringResource(Res.string.priority_levels_title),
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        Text(
            text = stringResource(Res.string.priority_levels_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        Spacer(modifier = Modifier.height(SettingsSectionGap))
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                SwitchPreferenceRow(
                    title = stringResource(Res.string.priority_high),
                    summary = stringResource(Res.string.priority_high_summary),
                    checked = levelAlarm,
                    onCheckedChange = { onLevelChange(AlertLevel.SUPER, it) },
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                SwitchPreferenceRow(
                    title = stringResource(Res.string.priority_medium),
                    summary = stringResource(Res.string.priority_medium_summary),
                    checked = levelPersistent,
                    onCheckedChange = { onLevelChange(AlertLevel.PERSISTENT, it) },
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                SwitchPreferenceRow(
                    title = stringResource(Res.string.priority_low),
                    summary = stringResource(Res.string.priority_always_on),
                    checked = true,
                    enabled = false,
                    onCheckedChange = {},
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                SwitchPreferenceRow(
                    title = stringResource(Res.string.filter_no_priority),
                    summary = stringResource(Res.string.priority_none_summary),
                    checked = levelSilent,
                    onCheckedChange = { onLevelChange(AlertLevel.SILENT, it) },
                )
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        SectionHeader(
            stringResource(Res.string.sound_by_priority),
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        CardGroup(
            listOfNotNull(
                SoundRow.ONCE,
                SoundRow.PERSISTENT.takeIf { showPersistentSound },
                SoundRow.ALARM.takeIf { showAlarmSound },
                SoundRow.DURATION.takeIf { showAlarmSound },
                SoundRow.BYPASS.takeIf { showAlarmSound },
                SoundRow.LOCK.takeIf { showAlarmSound },
            )
        ) { row ->
            when (row) {
                SoundRow.ONCE ->
                    PreferenceRow(
                        title = stringResource(Res.string.alert_caption_once),
                        summary = onceSoundName,
                        onClick = { onLevelSound(AlertLevel.ONCE) },
                    )
                SoundRow.PERSISTENT ->
                    PreferenceRow(
                        title = stringResource(Res.string.alert_caption_persistent),
                        summary = persistentSoundName,
                        onClick = { onLevelSound(AlertLevel.PERSISTENT) },
                    )
                SoundRow.ALARM ->
                    PreferenceRow(
                        title = stringResource(Res.string.alert_caption_alarm),
                        summary = buildString {
                            append(alarmSoundName)
                            if (alarmSoundName.isNotBlank()) append(" · ")
                            append(stringResource(Res.string.alarm_ring_duration_value, alarmRingSeconds))
                        },
                        onClick = { onLevelSound(AlertLevel.SUPER) },
                    )
                SoundRow.DURATION ->
                    PreferenceRow(
                        title = stringResource(Res.string.alarm_ring_duration),
                        summary = stringResource(Res.string.alarm_ring_duration_value, alarmRingSeconds),
                        onClick = onAlarmDuration,
                    )
                SoundRow.BYPASS ->
                    SwitchPreferenceRow(
                        title = stringResource(Res.string.alert_super_bypass),
                        summary = stringResource(Res.string.alert_super_bypass_summary),
                        checked = superBypassSilent,
                        onCheckedChange = onSuperBypass,
                    )
                SoundRow.LOCK ->
                    SwitchPreferenceRow(
                        title = stringResource(Res.string.alarm_on_lock_screen),
                        summary = stringResource(
                            if (alarmOnLockScreen) {
                                Res.string.alarm_on_lock_screen_on
                            } else {
                                Res.string.alarm_on_lock_screen_off
                            }
                        ),
                        checked = alarmOnLockScreen,
                        onCheckedChange = onAlarmLockScreen,
                    )
            }
        }

        val moreRows = listOfNotNull(
            NotificationRow.ENABLED.takeIf { showNotificationsEnabled },
            NotificationRow.PERSISTENT.takeIf { showOngoingNotifications },
            NotificationRow.WEARABLE.takeIf { showOngoingNotifications },
            NotificationRow.BUNDLE.takeIf { showBundleNotifications },
            NotificationRow.VOICE.takeIf { showVoiceReminders },
        )
        if (moreRows.isNotEmpty()) {
            Spacer(modifier = Modifier.height(SettingsContentPadding))
            CardGroup(moreRows) { row ->
                when (row) {
                    NotificationRow.ENABLED ->
                        SwitchPreferenceRow(
                            title = stringResource(Res.string.notifications),
                            summary = stringResource(Res.string.notifications_master_summary),
                            checked = notificationsEnabled,
                            onCheckedChange = onNotificationsEnabled,
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
                }
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        bottomInsets()
    }

    pendingLevelDisable?.let { pendingDisable ->
        AlertDialog(
            onDismissRequest = onDismissLevelDisable,
            title = { Text(stringResource(Res.string.priority_levels_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.priority_level_disable_message,
                        pendingDisable.tasks,
                        pendingDisable.memos,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmLevelDisable) {
                    Text(stringResource(Res.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissLevelDisable) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    lockScreenAsk?.let { ask ->
        val xiaomi = ask == LockScreenAsk.XIAOMI
        WhyDialog(
            title = stringResource(
                if (xiaomi) Res.string.alarm_on_lock_screen_xiaomi_title
                else Res.string.alarm_on_lock_screen_ask_title
            ),
            body = stringResource(
                if (xiaomi) Res.string.alarm_on_lock_screen_xiaomi
                else Res.string.alarm_on_lock_screen_ask
            ),
            confirm = stringResource(Res.string.alarm_on_lock_screen_allow),
            onConfirm = onConfirmAlarmLockScreen,
            onDismiss = onDismissAlarmLockScreen,
        )
    }

    if (lockScreenDenied) {
        AlertDialog(
            onDismissRequest = onDismissLockScreenDenied,
            text = { Text(stringResource(Res.string.alarm_on_lock_screen_denied)) },
            confirmButton = {
                TextButton(onClick = onDismissLockScreenDenied) {
                    Text(stringResource(Res.string.ok))
                }
            },
        )
    }

    if (askExactAlarms) {
        WhyDialog(
            title = stringResource(Res.string.alarm_need_exact_title),
            body = stringResource(Res.string.alarm_need_exact),
            confirm = stringResource(Res.string.alarm_on_lock_screen_allow),
            onConfirm = onConfirmExactAlarms,
            onDismiss = onDismissExactAlarms,
        )
    }
}

@Composable
private fun WhyDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.alarm_on_lock_screen_decline))
            }
        },
    )
}

private enum class TroubleshootingRow {
    TROUBLESHOOTING,
    BATTERY_OPTIMIZATION,
}

private enum class NotificationRow {
    ENABLED,
    PERSISTENT,
    WEARABLE,
    BUNDLE,
    VOICE,
}

private enum class SoundRow {
    ONCE,
    PERSISTENT,
    ALARM,
    DURATION,
    BYPASS,
    LOCK,
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
fun AlarmDurationDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f),
        title = { Text(stringResource(Res.string.alarm_ring_duration)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                (20..60 step 5).forEach { seconds ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = seconds == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(seconds) },
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = seconds == selected, onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(Res.string.alarm_ring_duration_value, seconds),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}

private val SwipeOptions: List<Triple<SwipeAction, StringResource, StringResource>> = listOf(
    Triple(SwipeAction.SNOOZE, Res.string.swipe_snooze, Res.string.swipe_snooze_help),
    Triple(SwipeAction.DISMISS, Res.string.swipe_dismiss, Res.string.swipe_dismiss_help),
    Triple(SwipeAction.COMPLETE, Res.string.swipe_complete, Res.string.swipe_complete_help),
)

private fun swipeLabel(action: SwipeAction): StringResource =
    SwipeOptions.first { it.first == action }.second

@Composable
fun SwipeActionDialog(
    selected: SwipeAction,
    onSelect: (SwipeAction) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.swipe_title)) },
        text = {
            Column {
                SwipeOptions.forEach { (action, label, _) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = action == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(action) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = action == selected, onClick = null)
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
fun SwipeHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.swipe_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SwipeOptions.forEach { (_, label, help) ->
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(help),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = stringResource(Res.string.swipe_help_always),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.ok)) }
        },
    )
}

@Composable
private fun ReminderHealthCard(
    checks: List<ReminderCheck>,
    modeLine: ModeLine,
    expanded: Boolean,
    testPending: Boolean,
    testSecondsLeft: Int,
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
                        if (testPending) {
                            stringResource(Res.string.reminder_test_countdown, testSecondsLeft)
                        } else {
                            stringResource(Res.string.reminder_test_button)
                        }
                    )
                }
            }
        }
        if (expanded) {
            checks.forEach { check ->
                SettingsItemCard {
                    PreferenceRow(
                        title = stringResource(checkTitle(check.id)),
                        summary = checkSummary(check),
                        onClick = if (check.state == CheckState.MANUAL) null else ({ onCheck(check.id) }),
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(SettingsContentPadding))
}

@Composable
private fun checkSummary(check: ReminderCheck): String = when (check.id) {
    ReminderCheckId.RECENTS_LOCK -> stringResource(Res.string.reminder_check_recents_hint)
    ReminderCheckId.FULL_SCREEN -> when (check.state) {
        CheckState.OK -> stringResource(Res.string.reminder_check_ok)
        else -> stringResource(Res.string.reminder_check_full_screen_summary)
    }
    ReminderCheckId.LOCK_SCREEN -> stringResource(Res.string.reminder_check_lock_screen_summary)
    else -> when (check.state) {
        CheckState.OK -> stringResource(Res.string.reminder_check_ok)
        CheckState.PROBLEM -> stringResource(Res.string.reminder_check_problem)
        CheckState.UNKNOWN -> stringResource(Res.string.reminder_check_unknown)
        CheckState.MANUAL -> stringResource(Res.string.reminder_check_unknown)
    }
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
    ReminderCheckId.FULL_SCREEN -> Res.string.reminder_check_full_screen
    ReminderCheckId.LOCK_SCREEN -> Res.string.reminder_check_lock_screen
}
