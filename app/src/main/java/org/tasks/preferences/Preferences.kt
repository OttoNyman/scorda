package org.tasks.preferences

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.todoroo.andlib.utility.AndroidUtilities
import com.todoroo.astrid.activity.BeastModePreferences
import com.todoroo.astrid.activity.applyShareEntry
import com.todoroo.astrid.core.SortHelper
import org.tasks.BuildConfig
import org.tasks.R
import org.tasks.Strings.isNullOrEmpty
import org.tasks.billing.Purchase
import org.tasks.data.entity.Alarm
import org.tasks.compose.pickers.QuickPickTimes
import org.tasks.data.entity.Task
import org.tasks.data.entity.TaskAttachment
import org.tasks.extensions.Context.getResourceUri
import org.tasks.kmp.org.tasks.themes.ColorProvider.BLUE_500
import org.tasks.kmp.org.tasks.time.DateFormats
import org.tasks.kmp.org.tasks.time.DateFormatter
import org.tasks.themes.ThemeBase
import org.tasks.time.DateTime
import timber.log.Timber
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

class Preferences @JvmOverloads constructor(
        private val context: Context,
        name: String? = getSharedPreferencesName(context)
) : QueryPreferences, AppPreferences {
    private val prefs: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private val notificationDefaults = NotificationSettings()

    private val drawerDefaults = DrawerSettings()

    private val lookAndFeelDefaults = LookAndFeelSettings()

    init {
        DateFormatter.datePattern = DateFormats.patternOf(dateFormat)
    }

    fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }


    fun showBackupWarnings() = !getBoolean(R.string.p_backups_ignore_warnings, false)

    override suspend fun addTasksToTop(): Boolean = getBoolean(R.string.p_add_to_top, true)

    fun backButtonSavesTask(): Boolean = getBoolean(R.string.p_back_button_saves_task, true)

    override suspend fun isDefaultDueTimeEnabled(): Boolean =
        getBoolean(R.string.p_rmd_time_enabled, true)

    override suspend fun defaultDueTime(): Int =
        getInt(R.string.p_rmd_time, notificationDefaults.defaultReminderTime)

    override suspend fun notificationSettings() = NotificationSettings(
        persistentNotifications = getBoolean(
            R.string.p_rmd_persistent,
            notificationDefaults.persistentNotifications
        ),
        wearableNotifications = getBoolean(
            R.string.p_wearable_notifications,
            notificationDefaults.wearableNotifications
        ),
        bundleNotifications = getBoolean(
            R.string.p_bundle_notifications,
            notificationDefaults.bundleNotifications
        ),
        voiceReminders = getBoolean(
            R.string.p_voiceRemindersEnabled,
            notificationDefaults.voiceReminders
        ),
        swipeAction = swipeAction(),
        swipeToSnoozeMinutes = getIntegerFromString(
            R.string.p_rmd_swipe_to_snooze_time_minutes,
            notificationDefaults.swipeToSnoozeMinutes
        ),
        defaultRemindersEnabled = getBoolean(
            R.string.p_rmd_time_enabled,
            notificationDefaults.defaultRemindersEnabled
        ),
        defaultReminderTime = getMillisOfDayPref(
            R.string.p_rmd_time,
            notificationDefaults.defaultReminderTime
        ),
    )

    override suspend fun setNotificationsEnabled(value: Boolean) {
        Timber.w("Ignoring notificationsEnabled=%s; the OS permission is the switch here", value)
    }

    override suspend fun setPersistentNotifications(value: Boolean) =
        setBoolean(R.string.p_rmd_persistent, value)

    override suspend fun setWearableNotifications(value: Boolean) =
        setBoolean(R.string.p_wearable_notifications, value)

    override suspend fun setBundleNotifications(value: Boolean) =
        setBoolean(R.string.p_bundle_notifications, value)

    override suspend fun setVoiceReminders(value: Boolean) =
        setBoolean(R.string.p_voiceRemindersEnabled, value)

    override suspend fun setSwipeAction(value: SwipeAction) =
        setStringFromInteger(R.string.p_rmd_swipe_action, value.ordinal)

    override suspend fun setSwipeToSnoozeMinutes(value: Int) =
        setStringFromInteger(R.string.p_rmd_swipe_to_snooze_time_minutes, value)

    override suspend fun setDefaultRemindersEnabled(value: Boolean) =
        setBoolean(R.string.p_rmd_time_enabled, value)

    override suspend fun setDefaultReminderTime(value: Int) = setInt(R.string.p_rmd_time, value)

    override suspend fun drawerSettings() = DrawerSettings(
        filtersEnabled = getBoolean(R.string.p_filters_enabled, drawerDefaults.filtersEnabled),
        todayFilter = getBoolean(R.string.p_show_today_filter, drawerDefaults.todayFilter),
        recentlyModifiedFilter = getBoolean(
            R.string.p_show_recently_modified_filter,
            drawerDefaults.recentlyModifiedFilter
        ),
        tagsEnabled = getBoolean(R.string.p_tags_enabled, drawerDefaults.tagsEnabled),
        hideUnusedTags = getBoolean(R.string.p_tags_hide_unused, drawerDefaults.hideUnusedTags),
        placesEnabled = getBoolean(R.string.p_places_enabled, drawerDefaults.placesEnabled),
        hideUnusedPlaces = getBoolean(
            R.string.p_places_hide_unused,
            drawerDefaults.hideUnusedPlaces
        ),
    )

    override suspend fun setFiltersEnabled(value: Boolean) =
        setBoolean(R.string.p_filters_enabled, value)

    override suspend fun setTodayFilter(value: Boolean) =
        setBoolean(R.string.p_show_today_filter, value)

    override suspend fun setRecentlyModifiedFilter(value: Boolean) =
        setBoolean(R.string.p_show_recently_modified_filter, value)

    override suspend fun setTagsEnabled(value: Boolean) =
        setBoolean(R.string.p_tags_enabled, value)

    override suspend fun setHideUnusedTags(value: Boolean) =
        setBoolean(R.string.p_tags_hide_unused, value)

    override suspend fun setPlacesEnabled(value: Boolean) =
        setBoolean(R.string.p_places_enabled, value)

    override suspend fun setHideUnusedPlaces(value: Boolean) =
        setBoolean(R.string.p_places_hide_unused, value)

    override suspend fun lookAndFeelSettings() = LookAndFeelSettings(
        theme = getInt(R.string.p_theme, lookAndFeelDefaults.theme),
        themeColor = getInt(R.string.p_theme_color, lookAndFeelDefaults.themeColor),
        dynamicColor = getBoolean(R.string.p_dynamic_color, lookAndFeelDefaults.dynamicColor),
        markdown = getBoolean(R.string.p_markdown, lookAndFeelDefaults.markdown),
        openLastViewedList = getBoolean(
            R.string.p_open_last_viewed_list,
            lookAndFeelDefaults.openLastViewedList
        ),
        defaultOpenFilter = getStringValue(R.string.p_default_open_filter),
        languageTag = null,
    )

    override suspend fun setTheme(value: Int) = setInt(R.string.p_theme, value)

    override suspend fun setThemeColor(value: Int) = setInt(R.string.p_theme_color, value)

    override suspend fun setDynamicColor(value: Boolean) =
        setBoolean(R.string.p_dynamic_color, value)

    override suspend fun setMarkdown(value: Boolean) = setBoolean(R.string.p_markdown, value)

    override suspend fun setOpenLastViewedList(value: Boolean) =
        setBoolean(R.string.p_open_last_viewed_list, value)

    override suspend fun setDefaultOpenFilter(value: String?) =
        setString(R.string.p_default_open_filter, value)

    override suspend fun setLanguageTag(value: String?) = Unit

    val dateShortcutMorning: Int
        get() = getMillisPerDayPref(R.string.p_date_shortcut_morning, R.integer.default_morning)

    val dateShortcutAfternoon: Int
        get() = getMillisPerDayPref(R.string.p_date_shortcut_afternoon, R.integer.default_afternoon)

    val dateShortcutEvening: Int
        get() = getMillisPerDayPref(R.string.p_date_shortcut_evening, R.integer.default_evening)

    val purchases: List<Purchase>
        get() = try {
            getStringSet(R.string.p_purchases).map(::Purchase)
        } catch (e: Exception) {
            Timber.e(e)
            emptyList()
        }

    fun setPurchases(purchases: Collection<Purchase>) {
        setPurchases(purchases.map(Purchase::toJson).toHashSet())
    }

    private fun setPurchases(set: HashSet<String>) {
        try {
            setStringSet(R.string.p_purchases, set)
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    val dateShortcutNight: Int
        get() = getMillisPerDayPref(R.string.p_date_shortcut_night, R.integer.default_night)

    val quickPickTimes: QuickPickTimes
        get() = QuickPickTimes(
            dateShortcutMorning,
            dateShortcutAfternoon,
            dateShortcutEvening,
            dateShortcutNight,
        )

    private fun getMillisPerDayPref(resId: Int, defResId: Int): Int =
        getMillisOfDayPref(resId, context.resources.getInteger(defResId))

    private fun getMillisOfDayPref(resId: Int, defaultValue: Int): Int {
        val setting = getInt(resId, -1)
        return if (setting < 0 || setting > DateTime.MAX_MILLIS_PER_DAY) {
            defaultValue
        } else setting
    }

    val completionSound: Uri?
        get() = null

    private fun getRingtone(pref: Int, default: Uri): Uri? {
        val ringtone = getStringValue(pref)
        return when {
            ringtone == null -> default
            ringtone.isNotBlank() -> ringtone.toUri()
            else -> null
        }
    }

    val isTrackingEnabled: Boolean
        get() = getBoolean(R.string.p_collect_statistics, true)

    @SuppressLint("ApplySharedPref")
    fun clear() {
        prefs.edit().clear().commit()
    }

    fun setDefaults() {
        PreferenceManager.setDefaultValues(context, R.xml.preferences_notifications, true)
        PreferenceManager.setDefaultValues(context, R.xml.preferences_task_defaults, true)
        PreferenceManager.setDefaultValues(context, R.xml.preferences_date_and_time, true)
        PreferenceManager.setDefaultValues(context, R.xml.preferences_backups, true)
        PreferenceManager.setDefaultValues(context, R.xml.preferences_advanced, true)
        BeastModePreferences.setDefaultOrder(this, context)
    }

    fun reset() {
        clear()
        setDefaults()
    }

    fun getStringValue(keyResource: Int): String? = getStringValue(context.getString(keyResource))

    fun getStringValue(key: String?): String? = try {
        prefs.getString(key, null)
    } catch (e: Exception) {
        Timber.e(e)
        null
    }

    override suspend fun defaultAlarms(): List<Alarm> =
        getStringSet(R.string.p_default_alarms, DEFAULT_ALARMS_JSON).toAlarms()

    override suspend fun setDefaultAlarms(value: List<Alarm>) {
        setStringSet(R.string.p_default_alarms, value.toAlarmJson())
    }

    override suspend fun defaultRingMode(): Int =
        getIntegerFromString(R.string.p_default_reminders_mode_key, 0)

    override suspend fun levelSilentEnabled(): Boolean =
        getBoolean(R.string.p_level_silent_enabled, false)

    override suspend fun levelPersistentEnabled(): Boolean =
        getBoolean(R.string.p_level_persistent_enabled, false)

    override suspend fun levelAlarmEnabled(): Boolean =
        getBoolean(R.string.p_level_alarm_enabled, true)

    override suspend fun alarmRingSeconds(): Int =
        getInt(R.string.p_alarm_ring_seconds, 60)
            .coerceIn(20, 60)
            .let { it - (it - 20) % 5 }

    override suspend fun setLevelSilentEnabled(value: Boolean) =
        setBoolean(R.string.p_level_silent_enabled, value)

    override suspend fun setLevelPersistentEnabled(value: Boolean) =
        setBoolean(R.string.p_level_persistent_enabled, value)

    override suspend fun setLevelAlarmEnabled(value: Boolean) =
        setBoolean(R.string.p_level_alarm_enabled, value)

    override suspend fun setAlarmRingSeconds(value: Int) =
        setInt(
            R.string.p_alarm_ring_seconds,
            org.tasks.notifications.clampAlarmRingMs(value * 1000L).toInt() / 1000,
        )

    override suspend fun defaultLocationReminder(): Int =
        getIntegerFromString(
            R.string.p_default_location_reminder_key,
            taskSettingDefaults.defaultLocationReminder
        ).let { if (it == 2) 2 else 1 }

    override suspend fun locationUpdateIntervalMinutes(): Int =
        getIntegerFromString(R.string.p_location_update_interval, DEFAULT_LOCATION_UPDATE_INTERVAL)

    private val taskSettingDefaults = TaskDefaultSettings()

    override suspend fun taskDefaults() = TaskDefaultSettings(
        addTasksToTop = addTasksToTop(),
        defaultList = defaultListUuid(),
        defaultTags = defaultTags,
        defaultPriority = defaultPriority(),
        defaultHideUntil = getIntegerFromString(
            R.string.p_default_hideUntil_key,
            taskSettingDefaults.defaultHideUntil
        ),
        // Not a setting any more: a new task is due today.
        defaultDueDate = Task.URGENCY_TODAY,
        defaultRecurrence = getStringValue(R.string.p_default_recurrence)?.takeIf { it.isNotBlank() },
        defaultRecurrenceFrom = getIntegerFromString(
            R.string.p_default_recurrence_from,
            taskSettingDefaults.defaultRecurrenceFrom
        ),
        defaultAlarms = defaultAlarms(),
        defaultRingMode = defaultRingMode(),
        defaultLocation = getStringValue(R.string.p_default_location),
        defaultLocationReminder = defaultLocationReminder(),
        locationUpdateIntervalMinutes = locationUpdateIntervalMinutes(),
    )

    override suspend fun setAddTasksToTop(value: Boolean) = setBoolean(R.string.p_add_to_top, value)

    private fun defaultListUuid(): String? =
        getStringValue(R.string.p_default_list)?.substringAfter(':')?.takeIf { it.isNotBlank() }

    override suspend fun setDefaultList(value: String?) =
        setString(R.string.p_default_list, value?.let { "${DefaultFilterProvider.TYPE_CALDAV}:$it" })

    private var defaultTags: List<String>
        get() = getStringValue(R.string.p_default_tags)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = setString(R.string.p_default_tags, value.joinToString(","))

    override suspend fun setDefaultTags(value: List<String>) { defaultTags = value }

    override suspend fun setDefaultPriority(value: Int) =
        setStringFromInteger(R.string.p_default_importance_key, value)

    override suspend fun setDefaultHideUntil(value: Int) =
        setStringFromInteger(R.string.p_default_hideUntil_key, value)

    override suspend fun setDefaultRecurrence(value: String?) =
        setString(R.string.p_default_recurrence, value)

    override suspend fun setDefaultRecurrenceFrom(value: Int) =
        setStringFromInteger(R.string.p_default_recurrence_from, value)

    override suspend fun setDefaultRingMode(value: Int) =
        setStringFromInteger(R.string.p_default_reminders_mode_key, value)

    fun ringtoneForLevel(level: org.tasks.notifications.AlertLevel): String? {
        val key = when (level) {
            org.tasks.notifications.AlertLevel.ONCE -> R.string.p_ringtone_once
            org.tasks.notifications.AlertLevel.PERSISTENT -> R.string.p_ringtone_persistent
            org.tasks.notifications.AlertLevel.SUPER -> R.string.p_ringtone_alarm
            org.tasks.notifications.AlertLevel.SILENT -> return null
        }
        return getStringValue(key)
    }

    override suspend fun setDefaultLocation(value: String?) =
        setString(R.string.p_default_location, value)

    override suspend fun setDefaultLocationReminder(value: Int) =
        setStringFromInteger(R.string.p_default_location_reminder_key, value)

    override suspend fun setLocationUpdateIntervalMinutes(value: Int) =
        setStringFromInteger(R.string.p_location_update_interval, value)

    val fontSize: Int
        get() = getInt(R.string.p_fontSize, 16)

    fun getIntegerFromString(keyResource: Int, defaultValue: Int): Int =
        getIntegerFromString(context.getString(keyResource), defaultValue)

    fun getIntegerFromString(keyResource: String?, defaultValue: Int): Int =
        getStringValue(keyResource)?.toIntOrNull() ?: defaultValue

    private fun getUri(key: Int): Uri? {
        val uri = getStringValue(key)
        return if (isNullOrEmpty(uri)) null else Uri.parse(uri)
    }

    fun setUri(key: Int, uri: URI) {
        setString(key, uri.toString())
    }

    fun setUri(key: Int, uri: Uri) {
        setString(key, uri.toString())
    }

    fun setString(key: Int, newValue: String?) {
        setString(context.getString(key), newValue)
    }

    fun setString(key: String?, newValue: String?) {
        Timber.d("Setting $key -> ${newValue?.let { if (BuildConfig.DEBUG) it else if (it.isBlank()) "" else "<redacted>" }}")
        val editor = prefs.edit()
        editor.putString(key, newValue)
        editor.apply()
    }

    fun setStringSet(key: Int, newValue: Set<String>) =
        setStringSet(context.getString(key), newValue)

    fun setStringSet(key: String, newValue: Set<String>) {
        Timber.d("Setting $key -> ${newValue.size} items")
        val editor = prefs.edit()
        editor.putStringSet(key, newValue)
        editor.apply()
    }

    internal fun getStringSet(key: Int, defaultValue: Set<String> = emptySet()) =
        getStringSet(context.getString(key), defaultValue)

    private fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String> =
        prefs.getStringSet(key, defaultValue)!!

    fun setStringFromInteger(keyResource: Int, newValue: Int) {
        val editor = prefs.edit()
        val key = context.getString(keyResource)
        Timber.d("Setting $key -> $newValue")
        editor.putString(key, newValue.toString())
        editor.apply()
    }

    fun getBoolean(key: String?, defValue: Boolean): Boolean = try {
        prefs.getBoolean(key, defValue)
    } catch (e: ClassCastException) {
        Timber.w(e)
        defValue
    }

    fun getBoolean(keyResources: Int, defValue: Boolean): Boolean =
            getBoolean(context.getString(keyResources), defValue)

    fun setBoolean(keyResource: Int, value: Boolean) {
        setBoolean(context.getString(keyResource), value)
    }

    fun setBoolean(key: String?, value: Boolean) {
        Timber.d("Setting $key -> $value")
        val editor = prefs.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    fun readConstructor(): org.tasks.constructor.Constructor = org.tasks.constructor.Constructor(
        repeat = getBoolean("constructor_repeat", false),
        overdue = getBoolean("constructor_overdue", false),
        notes = getBoolean("constructor_notes", false),
        // retired: lists repeat tags without sync; the saved flag and the tasks' lists stay untouched
        lists = false,
        checklist = getBoolean("constructor_checklist", false),
        tags = getBoolean("constructor_tags", false),
        filters = getBoolean("constructor_filters", false),
        priority = true,
        geo = getBoolean("constructor_geo", false),
        // retired: a task has one time; the saved flag and the tasks' start dates stay untouched
        startDate = false,
        // retired: "in advance" replaced it; the tasks' extra reminders stay in the database
        extraReminders = false,
        advance = getBoolean("constructor_advance", false),
        files = getBoolean("constructor_files", false),
        localLists = getBoolean("constructor_local_lists", false),
        memos = getBoolean("constructor_memos", false),
        pinning = getBoolean("constructor_pinning", false),
        pinShade = getBoolean("constructor_pin_shade", false),
    )

    fun writeConstructor(value: org.tasks.constructor.Constructor) {
        setBoolean("constructor_repeat", value.repeat)
        setBoolean("constructor_overdue", value.overdue)
        setBoolean("constructor_notes", value.notes)
        setBoolean("constructor_checklist", value.checklist)
        setBoolean("constructor_tags", value.tags)
        setBoolean("constructor_filters", value.filters)
        setBoolean("constructor_priority", value.priority)
        setBoolean("constructor_geo", value.geo)
        setBoolean("constructor_advance", value.advance)
        setBoolean("constructor_files", value.files)
        setBoolean("constructor_local_lists", value.localLists)
        setBoolean("constructor_memos", value.memos)
        setBoolean("constructor_pinning", value.pinning)
        setBoolean("constructor_pin_shade", value.pinShade)
    }

    fun getInt(resourceId: Int, defValue: Int): Int =
            getInt(context.getString(resourceId), defValue)

    fun getInt(key: String?, defValue: Int): Int = prefs.getInt(key, defValue)

    fun setInt(resourceId: Int, value: Int) {
        setInt(context.getString(resourceId), value)
    }

    fun setInt(key: String?, value: Int) {
        Timber.d("Setting $key -> $value")
        val editor = prefs.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    fun getLong(resourceId: Int, defValue: Long): Long =
            getLong(context.getString(resourceId), defValue)

    fun getLong(key: String?, defValue: Long): Long = prefs.getLong(key, defValue)

    fun setLong(resourceId: Int, value: Long) {
        setLong(context.getString(resourceId), value)
    }

    fun setLong(key: String?, value: Long) {
        Timber.d("Setting $key -> $value")
        val editor = prefs.edit()
        editor.putLong(key, value)
        editor.apply()
    }

    fun setLongIfGreater(key: String, value: Long, defValue: Long = 0L): Boolean =
            synchronized(longUpdateLock) {
                if (prefs.getLong(key, defValue) < value) {
                    setLong(key, value)
                    true
                } else {
                    false
                }
            }

    fun clear(key: String?) {
        Timber.d("Clearing $key")
        val editor = prefs.edit()
        editor.remove(key)
        editor.apply()
    }

    val lastSetVersion: Int
        get() = getInt(R.string.p_current_version, 0)

    fun setCurrentVersion(version: Int) {
        setInt(R.string.p_current_version, version)
    }

    var installVersion: Int
        get() = getInt(R.string.p_install_version, 0)
        set(value) = setInt(R.string.p_install_version, value)

    var installDate: Long
        get() = getLong(R.string.p_install_date, 0L)
        set(value) = setLong(R.string.p_install_date, value)

    var deviceInstallVersion: Int
        get() = getInt(R.string.p_device_install_version, 0)
        set(value) = setInt(R.string.p_device_install_version, value)

    override suspend fun getInstallVersion() = installVersion
    override suspend fun setInstallVersion(value: Int) { installVersion = value }
    override suspend fun getInstallDate() = installDate
    override suspend fun setInstallDate(value: Long) { installDate = value }
    override suspend fun getDeviceInstallVersion() = deviceInstallVersion
    override suspend fun setDeviceInstallVersion(value: Int) { deviceInstallVersion = value }

    override var sortMode: Int
        get() = SortHelper.SORT_DUE
        set(_) { setInt(R.string.p_sort_mode, SortHelper.SORT_DUE) }

    override var groupMode: Int
        get() {
            val stored = getInt(R.string.p_group_mode, SortHelper.SORT_DUE)
            return if (stored == SortHelper.GROUP_NONE) SortHelper.GROUP_NONE else SortHelper.SORT_DUE
        }
        set(value) {
            setInt(
                R.string.p_group_mode,
                if (value == SortHelper.GROUP_NONE) SortHelper.GROUP_NONE else SortHelper.SORT_DUE,
            )
        }

    override var completedMode: Int
        get() = SortHelper.SORT_COMPLETED
        set(_) { setInt(R.string.p_completed_mode, SortHelper.SORT_COMPLETED) }

    override var subtaskMode: Int
        get() = getInt(R.string.p_subtask_mode, SortHelper.SORT_MANUAL)
        set(value) { setInt(R.string.p_subtask_mode, value) }

    override var showHidden: Boolean
        get() = getBoolean(R.string.p_show_hidden_tasks, true)
        set(value) { setBoolean(R.string.p_show_hidden_tasks, value) }

    override var showCompleted: Boolean
        get() = getBoolean(R.string.p_show_completed_tasks, true)
        set(value) { setBoolean(R.string.p_show_completed_tasks, value) }

    override var showCompletedSubtasks: Boolean
        get() = getBoolean(R.string.p_show_completed_subtasks, true)
        set(value) { setBoolean(R.string.p_show_completed_subtasks, value) }

    /** [DateFormats.AUTO], [DateFormats.SYSTEM] or a pattern. Replaces the old "always full date" switch. */
    var dateFormat: String
        get() = getStringValue(R.string.p_date_format)
            ?: if (getBoolean(R.string.p_always_display_full_date, false)) DateFormats.SYSTEM else DateFormats.AUTO
        set(value) {
            setString(R.string.p_date_format, value)
            DateFormatter.datePattern = DateFormats.patternOf(value)
        }

    override var alwaysDisplayFullDate: Boolean
        get() = dateFormat != DateFormats.AUTO
        set(value) { dateFormat = if (value) DateFormats.SYSTEM else DateFormats.AUTO }

    override var completedTasksAtBottom: Boolean
        get() = getBoolean(R.string.p_completed_tasks_at_bottom, true)
        set(value) { setBoolean(R.string.p_completed_tasks_at_bottom, value) }

    val backupDirectory: Uri?
        get() = getDirectory(R.string.p_backup_dir, "backups")

    val appPrivateStorage: Uri
        get() = root.uri

    val attachmentsDirectory: Uri?
        get() = getDirectory(R.string.p_attachment_dir, TaskAttachment.FILES_DIRECTORY_DEFAULT)

    private fun getDirectory(pref: Int, name: String): Uri? {
        val uri = getUri(pref)
        if (uri != null) {
            when (uri.scheme) {
                ContentResolver.SCHEME_FILE -> {
                    val file = File(uri.path)
                    try {
                        if (file.canWrite()) {
                            return uri
                        }
                    } catch (ignored: SecurityException) {
                    }
                }
                ContentResolver.SCHEME_CONTENT -> if (hasWritePermission(context, uri)) {
                    return uri
                }
            }
        }
        return getDefaultDirectory(name)
    }

    private fun getDefaultDirectory(name: String): Uri? =
            root
                    .createDirectory(name)
                    ?.uri
                    ?: getDefaultFileLocation(name)?.let { Uri.fromFile(it) }

    private val root: DocumentFile
        get() = DocumentFile.fromFile(context.getExternalFilesDir(null) ?: context.filesDir)

    private fun getDefaultFileLocation(type: String): File? {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val path = File(baseDir, type)
        return if (path.isDirectory || path.mkdirs()) path else null
    }

    private fun hasWritePermission(context: Context, uri: Uri): Boolean =
            (PackageManager.PERMISSION_GRANTED
                    == context.checkUriPermission(
                    uri,
                    Binder.getCallingPid(),
                    Binder.getCallingUid(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))

    fun remove(resId: Int) {
        val editor = prefs.edit()
        editor.remove(context.getString(resId))
        editor.apply()
    }

    fun removeByPrefix(prefix: String) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(prefix) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    fun bundleNotifications(): Boolean = getBoolean(R.string.p_bundle_notifications, false)

    fun shareEntryEnabled(): Boolean = getBoolean(R.string.p_share_entry, true)

    fun setShareEntryEnabled(enabled: Boolean) {
        setBoolean(R.string.p_share_entry, enabled)
        context.applyShareEntry(enabled)
    }

    fun applyShareEntry() {
        context.applyShareEntry(shareEntryEnabled())
    }

    fun usePersistentReminders(): Boolean =
            AndroidUtilities.preUpsideDownCake() && getBoolean(R.string.p_rmd_persistent, true)

    fun swipeAction(): SwipeAction = SwipeAction.DISMISS

    fun swipeToSnoozeIntervalMS(): Long =
        TimeUnit.MINUTES.toMillis(
            getIntegerFromString(
                R.string.p_rmd_swipe_to_snooze_time_minutes,
                DEFAULT_SNOOZE_MINUTES
            ).toLong()
        )

    var lastSync: Long
        get() = getLong(R.string.p_last_sync, 0L)
        set(value) {
            setLong(R.string.p_last_sync, value)
        }

    fun <T> getPrefs(c: Class<T>): Map<String, T> =
        prefs.all.filter { (_, value) -> c.isInstance(value) } as Map<String, T>

    val isPerListSortEnabled: Boolean
        get() = false

    override var isManualSort: Boolean
        get() = false
        set(_) { setBoolean(R.string.p_manual_sort, false) }

    // Legacy Astrid manual order is retired in Scorda: "My order" covers manual sorting.
    val isAstridSortEnabled: Boolean
        get() = false

    override var isAstridSort: Boolean
        get() = isAstridSortEnabled && getBoolean(R.string.p_astrid_sort, false)
        set(value) {
            setBoolean(R.string.p_astrid_sort, value)
        }

    override var sortAscending: Boolean
        get() = true
        set(_) { setBoolean(R.string.p_sort_ascending, true) }

    override var undatedAtBottom: Boolean
        get() = getBoolean(R.string.p_undated_at_bottom, true)
        set(value) { setBoolean(R.string.p_undated_at_bottom, value) }

    override var noTimeAtEndOfDay: Boolean
        get() = getBoolean(R.string.p_no_time_at_end_of_day, true)
        set(value) { setBoolean(R.string.p_no_time_at_end_of_day, value) }

    override var groupAscending: Boolean
        get() = getBoolean(R.string.p_group_ascending, true)
        set(value) { setBoolean(R.string.p_group_ascending, value) }

    override var completedAscending: Boolean
        get() = getBoolean(R.string.p_completed_ascending, false)
        set(value) { setBoolean(R.string.p_completed_ascending, value) }

    override var subtaskAscending: Boolean
        get() = getBoolean(R.string.p_subtask_ascending, false)
        set(value) { setBoolean(R.string.p_subtask_ascending, value) }

    override suspend fun defaultPriority(): Int = Task.Priority.LOW

    val themeBase: Int
        get() = getInt(R.string.p_theme, ThemeBase.DEFAULT_BASE_THEME)

    val showStartDateChip: Boolean
        get() = getBoolean(R.string.p_start_date_chip, true)

    val showPlaceChip: Boolean
        get() = getBoolean(R.string.p_place_chips, true)

    val showListChip: Boolean
        get() = getBoolean(R.string.p_list_chips, true)

    val showTagChip: Boolean
        get() = getBoolean(R.string.p_tag_chips, true)

    fun alreadyNotified(account: String?, scope: String?): Boolean =
            getBoolean(context.getString(R.string.p_notified_oauth_error, account, scope), false)

    fun setAlreadyNotified(account: String?, scope: String?, value: Boolean) {
        setBoolean(context.getString(R.string.p_notified_oauth_error, account, scope), value)
    }

    val defaultThemeColor: Int
        get() = getInt(R.string.p_theme_color, BLUE_500)

    val dynamicColor: Boolean
        get() = getBoolean(R.string.p_dynamic_color, false)

    val markdown: Boolean
        get() = getBoolean(R.string.p_markdown, false)

    var lastReviewRequest: Long
        get() = getLong(R.string.p_last_review_request, 0L)
        set(value) = setLong(R.string.p_last_review_request, value)

    var warnNotificationsDisabled: Boolean
        get() = getBoolean(R.string.p_warn_notifications_disabled, true)
        set(value) = setBoolean(R.string.p_warn_notifications_disabled, value)

    var warnAlarmsDisabled: Boolean
        get() = getBoolean(R.string.p_warn_alarms_disabled, true)
        set(value) = setBoolean(R.string.p_warn_alarms_disabled, value)

    var warnMicrosoft: Boolean
        get() = getBoolean(R.string.p_warn_microsoft, true)
        set(value) = setBoolean(R.string.p_warn_microsoft, value)

    var warnGoogleTasks: Boolean
        get() = getBoolean(R.string.p_warn_google_tasks, true)
        set(value) = setBoolean(R.string.p_warn_google_tasks, value)

    var shownBeastModeHint: Boolean
        get() = getBoolean(R.string.p_shown_beast_mode_hint, false)
        set(value) = setBoolean(R.string.p_shown_beast_mode_hint, value)

    val linkify: Boolean
        get() = getBoolean(R.string.p_linkify_task_edit, false)

    @OptIn(ExperimentalMaterial3Api::class)
    var calendarDisplayMode: DisplayMode
        get() = if (getIntegerFromString(R.string.p_picker_mode_date, 0) == 1)
            DisplayMode.Input else DisplayMode.Picker
        set(mode) {
            setStringFromInteger(R.string.p_picker_mode_date, if (mode == DisplayMode.Input) 1 else 0)
        }

    @OptIn(ExperimentalMaterial3Api::class)
    var timeDisplayMode: DisplayMode
        get() = if (getIntegerFromString(R.string.p_picker_mode_time, 0) == 1)
            DisplayMode.Input else DisplayMode.Picker
        set(mode) {
            setStringFromInteger(R.string.p_picker_mode_time, if (mode == DisplayMode.Input) 1 else 0)
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override suspend fun datePickerPreferences() = DatePickerPreferences(
        shortcutMorning = dateShortcutMorning,
        shortcutAfternoon = dateShortcutAfternoon,
        shortcutEvening = dateShortcutEvening,
        shortcutNight = dateShortcutNight,
        alwaysDisplayFullDate = alwaysDisplayFullDate,
        datePickerInputMode = calendarDisplayMode == DisplayMode.Input,
        timePickerInputMode = timeDisplayMode == DisplayMode.Input,
    )

    @OptIn(ExperimentalMaterial3Api::class)
    override suspend fun setDatePickerInputMode(value: Boolean) {
        calendarDisplayMode = if (value) DisplayMode.Input else DisplayMode.Picker
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override suspend fun setTimePickerInputMode(value: Boolean) {
        timeDisplayMode = if (value) DisplayMode.Input else DisplayMode.Picker
    }

    companion object {
        private val longUpdateLock = Any()

        private fun getSharedPreferencesName(context: Context): String =
                context.packageName + "_preferences"
    }
}
