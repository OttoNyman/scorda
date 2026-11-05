package org.tasks.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import org.tasks.extensions.Context.canScheduleExactAlarms
import org.tasks.extensions.Context.openAppNotificationSettings
import org.tasks.extensions.Context.openChannelNotificationSettings
import org.tasks.extensions.Context.openReminderSettings
import org.tasks.time.DateTimeUtils2.currentTimeMillis

class AndroidReminderHealth(
    private val context: Context,
) {
    fun facts(): ReminderFacts {
        val systemNotifications = context.getSystemService(NotificationManager::class.java)
        val channel = systemNotifications.getNotificationChannel(
            org.tasks.notifications.NotificationManager.NOTIFICATION_CHANNEL_DEFAULT,
        )
        val power = context.getSystemService(PowerManager::class.java)
        return ReminderFacts(
            sdk = Build.VERSION.SDK_INT,
            oem = oemFamily(Build.MANUFACTURER),
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            channelBlocked = channel?.importance == NotificationManager.IMPORTANCE_NONE,
            canExact = context.canScheduleExactAlarms(),
            batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
            powerSaveOn = power.isPowerSaveMode,
        )
    }

    fun openable(checks: List<ReminderCheck>): List<ReminderCheck> {
        val label = appLabel()
        return checks.filter { check ->
            check.state != CheckState.UNKNOWN ||
                oemCandidates(check.id, context.packageName, label).any { it.resolves() }
        }
    }

    fun open(id: ReminderCheckId) {
        val intent = when (id) {
            ReminderCheckId.NOTIFICATIONS -> {
                if (facts().channelBlocked) {
                    context.openChannelNotificationSettings()
                } else {
                    context.openAppNotificationSettings()
                }
                return
            }
            ReminderCheckId.EXACT_ALARMS -> {
                context.openReminderSettings()
                return
            }
            ReminderCheckId.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            ReminderCheckId.POWER_SAVE -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            ReminderCheckId.RECENTS_LOCK -> return
            else -> oemCandidates(id, context.packageName, appLabel()).firstOrNull { it.resolves() }
        } ?: return
        launch(intent)
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleTest() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            TEST_NOTIFICATION_ID,
            Intent(context, ReminderTestReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val triggerAt = currentTimeMillis() + TEST_DELAY_MS
        if (context.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun Intent.resolves(): Boolean =
        context.packageManager.resolveActivity(this, PackageManager.MATCH_DEFAULT_ONLY) != null

    private fun appLabel(): String =
        context.applicationInfo.loadLabel(context.packageManager).toString()

    private companion object {
        const val TEST_DELAY_MS = 10_000L
    }
}
