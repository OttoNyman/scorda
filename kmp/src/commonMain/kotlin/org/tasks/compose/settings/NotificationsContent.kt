package org.tasks.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.tasks.TasksUrls
import org.tasks.notifications.AlertLevel
import org.tasks.compose.pickers.TimePickerDialog
import org.tasks.viewmodel.NotificationsViewModel
import org.tasks.viewmodel.NotificationsViewModel.TimePickerTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsContent(
    viewModel: NotificationsViewModel,
    is24HourFormat: Boolean,
    openUri: (String) -> Unit,
    onBatteryOptimization: (() -> Unit)? = null,
    onCompletionSound: (() -> Unit)? = null,
    onLevelSound: ((AlertLevel) -> Unit)? = null,
    onLevelToggle: ((AlertLevel, Boolean) -> Unit)? = null,
    onConfirmLevelDisable: (() -> Unit)? = null,
    onDismissLevelDisable: (() -> Unit)? = null,
    onMoreSettings: (() -> Unit)? = null,
    moreSettingsSummary: String? = null,
    bottomInsets: @Composable () -> Unit = {},
) {
    if (!viewModel.loaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    val settings = viewModel.settings
    NotificationsScreen(
        is24HourFormat = is24HourFormat,
        showTroubleshooting = viewModel.showTroubleshooting,
        showBatteryOptimization = viewModel.showBatteryOptimization,
        showNotificationsEnabled = viewModel.showNotificationsEnabled,
        showCompletionSound = viewModel.showCompletionSound,
        completionSoundName = viewModel.completionSoundName,
        showOngoingNotifications = viewModel.showOngoingNotifications,
        showBundleNotifications = viewModel.showBundleNotifications,
        showVoiceReminders = viewModel.showVoiceReminders,
        showSystemNotificationSettings = viewModel.showSystemNotificationSettings,
        moreSettingsSummary = moreSettingsSummary,
        showSwipeToSnooze = viewModel.showSwipeToSnooze,
        notificationsEnabled = settings.notificationsEnabled,
        persistentEnabled = settings.persistentNotifications,
        wearableEnabled = settings.wearableNotifications,
        bundleEnabled = settings.bundleNotifications,
        voiceEnabled = settings.voiceReminders,
        swipeAction = settings.swipeAction,
        swipeToSnoozeMinutes = settings.swipeToSnoozeMinutes,
        defaultRemindersEnabled = settings.defaultRemindersEnabled,
        defaultReminderTime = settings.defaultReminderTime,
        onTroubleshooting = { openUri(TasksUrls.NOTIFICATIONS) },
        onBatteryOptimization = { onBatteryOptimization?.invoke() },
        onNotificationsEnabled = { viewModel.updateNotificationsEnabled(it) },
        onCompletionSound = { onCompletionSound?.invoke() },
        onPersistent = { viewModel.updatePersistent(it) },
        onWearable = { viewModel.updateWearable(it) },
        onBundle = { viewModel.updateBundle(it) },
        onVoice = { viewModel.updateVoice(it) },
        onMoreSettings = { onMoreSettings?.invoke() },
        onSwipeAction = { viewModel.showSwipeDialog = true },
        onSwipeHelp = { viewModel.showSwipeHelp = true },
        onSnoozeTime = { viewModel.openSnoozeDialog() },
        onDefaultReminders = { viewModel.updateDefaultReminders(it) },
        onReminderTime = { viewModel.openTimePicker(TimePickerTarget.DEFAULT_REMINDER) },
        showHealth = viewModel.showTroubleshooting,
        healthChecks = viewModel.healthChecks,
        modeLine = viewModel.modeLine,
        healthExpanded = viewModel.healthExpanded,
        testPending = viewModel.testPending,
        testSecondsLeft = viewModel.testSecondsLeft,
        onToggleHealth = { viewModel.toggleHealth() },
        onHealthCheck = { viewModel.openHealthCheck(it) },
        onReminderTest = { viewModel.scheduleReminderTest() },
        showPersistentSound = viewModel.levelPersistentEnabled,
        showAlarmSound = viewModel.levelAlarmEnabled,
        onceSoundName = viewModel.onceSoundName,
        persistentSoundName = viewModel.persistentSoundName,
        alarmSoundName = viewModel.alarmSoundName,
        onLevelSound = { onLevelSound?.invoke(it) },
        alarmRingSeconds = viewModel.alarmRingSeconds,
        onAlarmDuration = { viewModel.openAlarmDuration() },
        superBypassSilent = viewModel.superBypassSilent,
        onSuperBypass = { viewModel.updateSuperBypass(it) },
        levelSilent = viewModel.levelSilentEnabled,
        levelPersistent = viewModel.levelPersistentEnabled,
        levelAlarm = viewModel.levelAlarmEnabled,
        onLevelChange = { level, enabled -> onLevelToggle?.invoke(level, enabled) },
        pendingLevelDisable = viewModel.pendingLevelDisable,
        onConfirmLevelDisable = { onConfirmLevelDisable?.invoke() },
        onDismissLevelDisable = { onDismissLevelDisable?.invoke() },
        bottomInsets = bottomInsets,
    )

    if (viewModel.showSnoozeDialog) {
        SnoozeTimeDialog(
            selected = settings.swipeToSnoozeMinutes,
            onSelect = { viewModel.setSnoozeTime(it) },
            onDismiss = { viewModel.dismissSnoozeDialog() },
        )
    }

    if (viewModel.showSwipeDialog) {
        SwipeActionDialog(
            selected = settings.swipeAction,
            onSelect = { viewModel.updateSwipeAction(it) },
            onDismiss = { viewModel.showSwipeDialog = false },
        )
    }

    if (viewModel.showSwipeHelp) {
        SwipeHelpDialog(onDismiss = { viewModel.showSwipeHelp = false })
    }

    if (viewModel.showAlarmDurationDialog) {
        AlarmDurationDialog(
            selected = viewModel.alarmRingSeconds,
            onSelect = { viewModel.updateAlarmRingSeconds(it) },
            onDismiss = { viewModel.dismissAlarmDuration() },
        )
    }

    viewModel.timePickerTarget?.let { target ->
        key(target) {
            val initial = viewModel.timePickerInitialValue(target)
            TimePickerDialog(
                state = rememberTimePickerState(
                    initialHour = initial / (60 * 60_000),
                    initialMinute = (initial / 60_000) % 60,
                    is24Hour = is24HourFormat,
                ),
                selected = { viewModel.setTime(target, it) },
                dismiss = { viewModel.dismissTimePicker() },
            )
        }
    }
}
