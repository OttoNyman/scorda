package org.tasks.preferences

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import org.tasks.compose.pickers.DEFAULT_EVENING
import org.tasks.compose.pickers.DEFAULT_MORNING
import org.tasks.compose.pickers.QuickPickTimes
import org.tasks.kmp.org.tasks.themes.ColorProvider
import org.tasks.themes.BaseTheme
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Task

const val DEFAULT_REMINDER_TIME = 18 * 60 * 60 * 1000
const val DEFAULT_SNOOZE_MINUTES = 5

/** What swiping a reminder away does. Stored by ordinal: keep the order. */
enum class SwipeAction { SNOOZE, DISMISS, COMPLETE }
const val DEFAULT_LOCATION_UPDATE_INTERVAL = 15

val DEFAULT_ALARMS: List<Alarm> = listOf(
    Alarm.whenStarted(0),
    Alarm.whenDue(0),
    Alarm.whenOverdue(0),
)

val DEFAULT_ALARMS_JSON: Set<String> = DEFAULT_ALARMS.toAlarmJson()

val alarmOrder: Comparator<Alarm> = compareBy({ it.type }, { it.time })

fun List<Alarm>.toAlarmJson(): Set<String> = map { Json.encodeToString(it) }.toSet()

fun Set<String>.toAlarms(): List<Alarm> =
    mapNotNull {
        try {
            Json.decodeFromString<Alarm>(it)
        } catch (e: Exception) {
            Logger.e(e, tag = "AppPreferences") { "Failed to decode alarm: $it" }
            null
        }
    }
        .sortedWith(alarmOrder)

data class DatePickerPreferences(
    val shortcutMorning: Int = DEFAULT_MORNING,
    val shortcutEvening: Int = DEFAULT_EVENING,
    val alwaysDisplayFullDate: Boolean = false,
    val datePickerInputMode: Boolean = false,
    val timePickerInputMode: Boolean = false,
) {
    val quickPickTimes: QuickPickTimes
        get() = QuickPickTimes(shortcutMorning, shortcutEvening)
}

data class NotificationSettings(
    val notificationsEnabled: Boolean = true,
    val persistentNotifications: Boolean = true,
    val wearableNotifications: Boolean = true,
    val bundleNotifications: Boolean = false,
    val voiceReminders: Boolean = false,
    val swipeAction: SwipeAction = SwipeAction.SNOOZE,
    val swipeToSnoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val defaultRemindersEnabled: Boolean = true,
    val defaultReminderTime: Int = DEFAULT_REMINDER_TIME,
)

data class DrawerSettings(
    val filtersEnabled: Boolean = true,
    val todayFilter: Boolean = true,
    val recentlyModifiedFilter: Boolean = true,
    val tagsEnabled: Boolean = true,
    val hideUnusedTags: Boolean = false,
    val placesEnabled: Boolean = true,
    val hideUnusedPlaces: Boolean = false,
)

data class LookAndFeelSettings(
    val theme: Int = BaseTheme.DEFAULT,
    val themeColor: Int = ColorProvider.BLUE_500,
    val dynamicColor: Boolean = false,
    val markdown: Boolean = false,
    val openLastViewedList: Boolean = true,
    val defaultOpenFilter: String? = null,
    val languageTag: String? = null,
)

data class TaskDefaultSettings(
    val addTasksToTop: Boolean = true,
    val defaultList: String? = null,
    val defaultTags: List<String> = emptyList(),
    val defaultPriority: Int = Task.Priority.LOW,
    val defaultHideUntil: Int = Task.HIDE_UNTIL_NONE,
    /** Not a setting: the platforms fill in [Task.URGENCY_TODAY]. */
    val defaultDueDate: Int = Task.URGENCY_NONE,
    val defaultRecurrence: String? = null,
    val defaultRecurrenceFrom: Int = 0,
    val defaultAlarms: List<Alarm> = DEFAULT_ALARMS,
    val defaultRingMode: Int = 0,
    val defaultLocation: String? = null,
    val defaultLocationReminder: Int = 1,
    val locationUpdateIntervalMinutes: Int = DEFAULT_LOCATION_UPDATE_INTERVAL,
)

interface AppPreferences {
    suspend fun getInstallVersion(): Int
    suspend fun setInstallVersion(value: Int)
    suspend fun getInstallDate(): Long
    suspend fun setInstallDate(value: Long)
    suspend fun getDeviceInstallVersion(): Int
    suspend fun setDeviceInstallVersion(value: Int)
    suspend fun isDefaultDueTimeEnabled(): Boolean
    suspend fun defaultLocationReminder(): Int
    suspend fun defaultAlarms(): List<Alarm>
    suspend fun defaultRingMode(): Int
    suspend fun defaultDueTime(): Int
    suspend fun defaultPriority(): Int
    suspend fun levelSilentEnabled(): Boolean = false
    suspend fun levelPersistentEnabled(): Boolean = false
    suspend fun levelAlarmEnabled(): Boolean = true
    suspend fun enabledLevels(): Set<org.tasks.notifications.AlertLevel> =
        org.tasks.notifications.enabledLevels(
            silent = levelSilentEnabled(),
            persistent = levelPersistentEnabled(),
            alarm = levelAlarmEnabled(),
        )
    suspend fun alarmRingSeconds(): Int = 60
    suspend fun alarmRingMs(): Long =
        org.tasks.notifications.clampAlarmRingMs(alarmRingSeconds() * 1000L)
    suspend fun locationUpdateIntervalMinutes(): Int = DEFAULT_LOCATION_UPDATE_INTERVAL
    suspend fun addTasksToTop(): Boolean = true
    suspend fun taskDefaults(): TaskDefaultSettings
    suspend fun setAddTasksToTop(value: Boolean)
    suspend fun setDefaultList(value: String?)
    suspend fun setDefaultTags(value: List<String>)
    suspend fun setDefaultPriority(value: Int)
    suspend fun setDefaultHideUntil(value: Int)
    suspend fun setDefaultRecurrence(value: String?)
    suspend fun setDefaultRecurrenceFrom(value: Int)

    suspend fun setDefaultAlarms(value: List<Alarm>)
    suspend fun setDefaultRingMode(value: Int)
    suspend fun setLevelSilentEnabled(value: Boolean) {}
    suspend fun setLevelPersistentEnabled(value: Boolean) {}
    suspend fun setLevelAlarmEnabled(value: Boolean) {}
    suspend fun setAlarmRingSeconds(value: Int) {}
    suspend fun setDefaultLocation(value: String?)
    suspend fun setDefaultLocationReminder(value: Int)
    suspend fun setLocationUpdateIntervalMinutes(value: Int)
    suspend fun datePickerPreferences(): DatePickerPreferences = DatePickerPreferences()
    suspend fun setDatePickerInputMode(value: Boolean) {}
    suspend fun setTimePickerInputMode(value: Boolean) {}
    suspend fun drawerSettings(): DrawerSettings
    suspend fun setFiltersEnabled(value: Boolean)
    suspend fun setTodayFilter(value: Boolean)
    suspend fun setRecentlyModifiedFilter(value: Boolean)
    suspend fun setTagsEnabled(value: Boolean)
    suspend fun setHideUnusedTags(value: Boolean)
    suspend fun setPlacesEnabled(value: Boolean)
    suspend fun setHideUnusedPlaces(value: Boolean)
    suspend fun lookAndFeelSettings(): LookAndFeelSettings
    suspend fun setTheme(value: Int)
    suspend fun setThemeColor(value: Int)
    suspend fun setDynamicColor(value: Boolean)
    suspend fun setMarkdown(value: Boolean)
    suspend fun setOpenLastViewedList(value: Boolean)
    suspend fun setDefaultOpenFilter(value: String?)
    suspend fun setLanguageTag(value: String?)
    suspend fun notificationSettings(): NotificationSettings
    suspend fun setNotificationsEnabled(value: Boolean)
    suspend fun setPersistentNotifications(value: Boolean)
    suspend fun setWearableNotifications(value: Boolean)
    suspend fun setBundleNotifications(value: Boolean)
    suspend fun setVoiceReminders(value: Boolean)
    suspend fun setSwipeAction(value: SwipeAction)
    suspend fun setSwipeToSnoozeMinutes(value: Int)
    suspend fun setDefaultRemindersEnabled(value: Boolean)
    suspend fun setDefaultReminderTime(value: Int)
}
