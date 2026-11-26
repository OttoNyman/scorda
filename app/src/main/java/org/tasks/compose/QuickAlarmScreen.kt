package org.tasks.compose

import android.content.Intent
import android.media.AudioManager
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.extensions.Context.canScheduleExactAlarms
import org.tasks.notifications.AndroidReminderHealth
import org.tasks.notifications.AlertLevel
import org.tasks.notifications.ReminderCheckId
import org.tasks.preferences.NotificationPreferences
import org.tasks.preferences.Preferences
import org.tasks.quickalarm.QuickAlarmViewModel
import org.tasks.quickalarm.quickAlarmDueAtMillis
import org.tasks.time.DateTimeUtils2.currentTimeMillis

@Composable
fun QuickAlarmScreen(
    preferences: Preferences,
    viewModel: QuickAlarmViewModel,
    reminderHealth: AndroidReminderHealth,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var alarmLevelEnabled by remember { mutableStateOf(true) }
    var canExact by remember { mutableStateOf(true) }
    var ringsInSilent by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    suspend fun refreshWarnings() {
        alarmLevelEnabled = AlertLevel.SUPER in preferences.enabledLevels()
        canExact = context.canScheduleExactAlarms()
        val audio = context.getSystemService(AudioManager::class.java)
        val ringerNormal = audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL
        val bypass = preferences.getBoolean(R.string.p_super_bypass_silent, false)
        ringsInSilent = !alarmLevelEnabled || ringerNormal || bypass
        val facts = reminderHealth.facts(alarmEnabled = alarmLevelEnabled)
        notificationsEnabled = facts.notificationsEnabled && !facts.channelBlocked
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        lifecycleOwner.lifecycleScope.launch { refreshWarnings() }
    }

    LaunchedEffect(viewModel.done) {
        if (viewModel.done) onFinish()
    }

    LaunchedEffect(Unit) {
        viewModel.failures.collect {
            Toast.makeText(context, R.string.quick_alarm_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    QuickAlarmDialog(
        alarmLevelEnabled = alarmLevelEnabled,
        canExactAlarms = canExact,
        ringsInSilent = ringsInSilent,
        notificationsEnabled = notificationsEnabled,
        confirmEnabled = !viewModel.saving && !viewModel.done,
        onOpenAlarmLevelSettings = {
            context.startActivity(Intent(context, NotificationPreferences::class.java))
        },
        onOpenExactAlarmSettings = {
            reminderHealth.open(ReminderCheckId.EXACT_ALARMS)
        },
        onOpenSilentBypassSettings = {
            context.startActivity(Intent(context, NotificationPreferences::class.java))
        },
        onOpenNotificationSettings = {
            reminderHealth.open(ReminderCheckId.NOTIFICATIONS)
        },
        onConfirm = { title, intervalMinutes ->
            lifecycleOwner.lifecycleScope.launch {
                refreshWarnings()
                val dueAt = quickAlarmDueAtMillis(currentTimeMillis(), intervalMinutes)
                val timeLabel = DateFormat.getTimeFormat(context).format(dueAt)
                val defaultTitle = context.getString(R.string.quick_alarm_default_title, timeLabel)
                viewModel.confirm(title, dueAt, defaultTitle)
            }
        },
        onDismiss = {
            if (!viewModel.saving && !viewModel.done) onFinish()
        },
    )
}
