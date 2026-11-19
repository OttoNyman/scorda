package org.tasks.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.todoroo.astrid.core.SortHelper
import kotlinx.coroutines.runBlocking

class DataStoreQueryPreferences(
    private val tasksPreferences: TasksPreferences,
) : QueryPreferences {

    override var sortMode: Int
        get() = SortHelper.SORT_DUE
        set(_) {}
    override var groupMode: Int by pref(Keys.groupMode, SortHelper.SORT_DUE)
    override var completedMode: Int
        get() = SortHelper.SORT_COMPLETED
        set(_) {}
    override var subtaskMode: Int by pref(Keys.subtaskMode, SortHelper.SORT_MANUAL)
    override var isManualSort: Boolean
        get() = false
        set(_) {}
    override var isAstridSort: Boolean
        get() = false
        set(_) {}
    override var sortAscending: Boolean
        get() = true
        set(_) {}
    override var groupAscending: Boolean by pref(Keys.groupAscending, true)
    override var completedAscending: Boolean by pref(Keys.completedAscending, false)
    override var subtaskAscending: Boolean by pref(Keys.subtaskAscending, false)
    override var showHidden: Boolean by pref(Keys.showHidden, false)
    override var showCompleted: Boolean by pref(Keys.showCompleted, true)
    override var showCompletedSubtasks: Boolean by pref(Keys.showCompletedSubtasks, true)
    override val alwaysDisplayFullDate: Boolean = false
    override var completedTasksAtBottom: Boolean by pref(Keys.completedTasksAtBottom, true)
    override var undatedAtBottom: Boolean by pref(Keys.undatedAtBottom, true)
    override var noTimeAtEndOfDay: Boolean by pref(Keys.noTimeAtEndOfDay, true)

    private inline fun <reified T> pref(
        key: androidx.datastore.preferences.core.Preferences.Key<T>,
        defaultValue: T,
    ) = object : kotlin.properties.ReadWriteProperty<Any?, T> {
        private var cached: T? = null

        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T {
            if (cached == null) {
                cached = runBlocking { tasksPreferences.get(key, defaultValue) }
            }
            return cached!!
        }

        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
            cached = value
            runBlocking { tasksPreferences.set(key, value) }
        }
    }

    private object Keys {
        val sortMode = intPreferencesKey("sort_mode")
        val groupMode = intPreferencesKey("group_mode")
        val completedMode = intPreferencesKey("completed_mode")
        val subtaskMode = intPreferencesKey("subtask_mode")
        val manualSort = booleanPreferencesKey("manual_sort")
        val astridSort = booleanPreferencesKey("astrid_sort")
        val sortAscending = booleanPreferencesKey("sort_ascending")
        val groupAscending = booleanPreferencesKey("group_ascending")
        val completedAscending = booleanPreferencesKey("completed_ascending")
        val subtaskAscending = booleanPreferencesKey("subtask_ascending")
        val completedTasksAtBottom = booleanPreferencesKey("completed_tasks_at_bottom")
        val showHidden = booleanPreferencesKey("show_hidden")
        val showCompleted = booleanPreferencesKey("show_completed")
        val showCompletedSubtasks = booleanPreferencesKey("show_completed_subtasks")
        val undatedAtBottom = booleanPreferencesKey("undated_at_bottom")
        val noTimeAtEndOfDay = booleanPreferencesKey("no_time_at_end_of_day")
    }
}
