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
    /** One extra ring before the reminder time. */
    val advance: Boolean = false,
    val files: Boolean = false,
    /** Raw: keep local lists next to synced ones. Effective: the local block is shown. */
    val localLists: Boolean = false,
) {
    fun effective(
        sync: Boolean,
        geofences: Boolean,
    ): Constructor = copy(
        localLists = lists && (!sync || localLists),
        geo = geo && geofences,
    )

    /** While syncing, a state without the local block keeps every task in Google. */
    fun keepsEverythingInGoogle(sync: Boolean): Boolean = sync && !(lists && localLists)

    fun withPreset(preset: Preset): Constructor = preset.flags.copy(localLists = localLists)

    fun schedules(alarm: Alarm): Boolean = when (alarm.type) {
        Alarm.TYPE_SNOOZE -> true
        Alarm.TYPE_GEO_ENTER, Alarm.TYPE_GEO_EXIT -> geo
        Alarm.TYPE_RANDOM, Alarm.TYPE_DATE_TIME -> extraReminders
        Alarm.TYPE_REL_START -> startDate
        Alarm.TYPE_REL_END -> when {
            alarm.time == 0L && alarm.repeat == 0 -> true
            alarm.time < 0L -> if (alarm.repeat == 0) advance || extraReminders else extraReminders
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
    val extraReminders: Int = 0,
    val startDate: Int = 0,
    /** Local lists that would move to Google. */
    val localLists: Int = 0,
)

enum class Preset { Simple, Full }

val Preset.flags: Constructor
    get() = when (this) {
        Preset.Simple -> Constructor()
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
            // start date, extra reminders and the device calendar are retired
            advance = true,
            files = true,
        )
    }

fun Constructor.allowsSort(mode: Int): Boolean = when (mode) {
    com.todoroo.astrid.core.SortHelper.SORT_START -> startDate
    com.todoroo.astrid.core.SortHelper.SORT_IMPORTANCE -> priority
    com.todoroo.astrid.core.SortHelper.SORT_LIST -> lists
    else -> true
}
