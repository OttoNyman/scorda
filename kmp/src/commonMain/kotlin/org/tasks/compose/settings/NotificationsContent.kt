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
    onReminderSound: (() -> Unit)? = null,
    onDefaultAlarms: () -> Unit = {},
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
        onToggleHealth = { viewModel.toggleHealth() },
        onHealthCheck = { viewModel.openHealthCheck(it) },
        onReminderTest = { viewModel.scheduleReminderTest() },
        moreExpanded = viewModel.moreExpanded,
        onToggleMore = { viewModel.toggleMore() },
        defaultAlarmsSummary = remindersSummary(viewModel.defaultAlarms, is24HourFormat),
        onDefaultAlarms = onDefaultAlarms,
        defaultRingMode = viewModel.defaultRingMode,
        onRingMode = { viewModel.openRingDialog() },
        superBypassSilent = viewModel.superBypassSilent,
        onSuperBypass = { viewModel.updateSuperBypass(it) },
        reminderSoundName = viewModel.reminderSoundName,
        onReminderSound = { onReminderSound?.invoke() },
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

    if (viewModel.showRingDialog) {
        RingLevelDialog(
            selected = viewModel.defaultRingMode,
            onSelect = { viewModel.updateDefaultRingMode(it) },
            onDismiss = { viewModel.dismissRingDialog() },
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
