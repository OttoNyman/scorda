package org.tasks.constructor

import org.tasks.data.entity.Alarm

data class Constructor(
    val repeat: Boolean = false,
    val overdue: Boolean = false,
    val notes: Boolean = false,
    val lists: Boolean = false,
    val subtasks: Boolean = false,
    val tags: Boolean = false,
    val filters: Boolean = false,
    val priority: Boolean = false,
    val geo: Boolean = false,
    val startDate: Boolean = false,
    val extraReminders: Boolean = false,
    val calendar: Boolean = false,
    val timer: Boolean = false,
    val files: Boolean = false,
) {
    fun effective(
        hasSyncAccount: Boolean,
        geofences: Boolean,
        calendarEvents: Boolean,
    ): Constructor = copy(
        lists = lists || hasSyncAccount,
        geo = geo && geofences,
        calendar = calendar && calendarEvents,
    )

    fun schedules(alarm: Alarm): Boolean = when (alarm.type) {
        Alarm.TYPE_SNOOZE -> true
        Alarm.TYPE_GEO_ENTER, Alarm.TYPE_GEO_EXIT -> geo
        Alarm.TYPE_RANDOM, Alarm.TYPE_DATE_TIME -> extraReminders
        Alarm.TYPE_REL_START -> startDate
        Alarm.TYPE_REL_END -> when {
            alarm.time == 0L && alarm.repeat == 0 -> true
            alarm.time < 0L -> extraReminders
            alarm.time == 0L && alarm.repeat > 0 -> extraReminders
            alarm.time > 0L -> overdue
            else -> false
        }
        else -> false
    }

    val showsDrawer: Boolean
        get() = lists || tags || filters || geo
}

data class ConstructorCounts(
    val repeat: Int = 0,
    val overdue: Int = 0,
    val geo: Int = 0,
    val calendar: Int = 0,
    val extraReminders: Int = 0,
    val startDate: Int = 0,
)

enum class Preset { Simple, Reminders, Tasks, Full }

val Preset.flags: Constructor
    get() = when (this) {
        Preset.Simple -> Constructor()
        Preset.Reminders -> Constructor(repeat = true, overdue = true, notes = true)
        Preset.Tasks -> Constructor(
            repeat = true,
            overdue = true,
            notes = true,
            lists = true,
            subtasks = true,
            tags = true,
            filters = true,
        )
        Preset.Full -> Constructor(
            repeat = true,
            overdue = true,
            notes = true,
            lists = true,
            subtasks = true,
            tags = true,
            filters = true,
            priority = true,
            geo = true,
            startDate = true,
            extraReminders = true,
            calendar = true,
            timer = true,
            files = true,
        )
    }

fun matchingPreset(
    raw: Constructor,
    hasSyncAccount: Boolean,
    geofences: Boolean,
    calendarEvents: Boolean,
): Preset? = Preset.entries.firstOrNull { preset ->
    // An account forces lists on the raw side only: Simple must not look selected while lists are on.
    preset.flags.effective(hasSyncAccount = false, geofences, calendarEvents) ==
        raw.effective(hasSyncAccount, geofences, calendarEvents)
}

fun Constructor.allowsSort(mode: Int): Boolean = when (mode) {
    com.todoroo.astrid.core.SortHelper.SORT_START -> startDate
    com.todoroo.astrid.core.SortHelper.SORT_IMPORTANCE -> priority
    com.todoroo.astrid.core.SortHelper.SORT_LIST -> lists
    else -> true
}
