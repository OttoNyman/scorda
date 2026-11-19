package org.tasks.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.todoroo.astrid.core.SortHelper
import kotlinx.coroutines.runBlocking

class FilterPreferences(
    private val globalPreferences: QueryPreferences,
    private val tasksPreferences: TasksPreferences,
    private val filterKey: String,
) : QueryPreferences by globalPreferences {

    private fun getInt(suffix: String, default: Int): Int {
        return runBlocking { tasksPreferences.get(intPreferencesKey(getKey(suffix)), default) }
    }

    private fun setInt(suffix: String, value: Int) {
        runBlocking { tasksPreferences.set(intPreferencesKey(getKey(suffix)), value) }
    }

    private fun getBoolean(suffix: String, default: Boolean): Boolean {
        return runBlocking { tasksPreferences.get(booleanPreferencesKey(getKey(suffix)), default) }
    }

    private fun setBoolean(suffix: String, value: Boolean) {
        runBlocking { tasksPreferences.set(booleanPreferencesKey(getKey(suffix)), value) }
    }

    private fun getKey(suffix: String) = "${KEY_PREFIX}_${filterKey}_$suffix"

    override var sortMode: Int
        get() = SortHelper.SORT_DUE
        set(_) {}

    override var groupMode: Int
        get() {
            val stored = getInt(KEY_GROUP_MODE, globalPreferences.groupMode)
            return if (stored == SortHelper.GROUP_NONE) SortHelper.GROUP_NONE else SortHelper.SORT_DUE
        }
        set(value) = setInt(
            KEY_GROUP_MODE,
            if (value == SortHelper.GROUP_NONE) SortHelper.GROUP_NONE else SortHelper.SORT_DUE,
        )

    override var completedMode: Int
        get() = SortHelper.SORT_COMPLETED
        set(_) {}

    override var subtaskMode: Int
        get() = getInt(KEY_SUBTASK_MODE, globalPreferences.subtaskMode)
        set(value) = setInt(KEY_SUBTASK_MODE, value)

    override var isManualSort: Boolean
        get() = false
        set(_) {}

    override var isAstridSort: Boolean
        get() = false
        set(_) {}

    override var sortAscending: Boolean
        get() = true
        set(_) {}

    override var groupAscending: Boolean
        get() = getBoolean(KEY_GROUP_ASCENDING, globalPreferences.groupAscending)
        set(value) = setBoolean(KEY_GROUP_ASCENDING, value)

    override var completedAscending: Boolean
        get() = getBoolean(KEY_COMPLETED_ASCENDING, globalPreferences.completedAscending)
        set(value) = setBoolean(KEY_COMPLETED_ASCENDING, value)

    override var subtaskAscending: Boolean
        get() = getBoolean(KEY_SUBTASK_ASCENDING, globalPreferences.subtaskAscending)
        set(value) = setBoolean(KEY_SUBTASK_ASCENDING, value)

    override var completedTasksAtBottom: Boolean
        get() = getBoolean(KEY_COMPLETED_AT_BOTTOM, globalPreferences.completedTasksAtBottom)
        set(value) = setBoolean(KEY_COMPLETED_AT_BOTTOM, value)

    override var showCompletedSubtasks: Boolean
        get() = getBoolean(KEY_SHOW_COMPLETED_SUBTASKS, globalPreferences.showCompletedSubtasks)
        set(value) = setBoolean(KEY_SHOW_COMPLETED_SUBTASKS, value)

    companion object {
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_GROUP_MODE = "group_mode"
        private const val KEY_COMPLETED_MODE = "completed_mode"
        private const val KEY_SUBTASK_MODE = "subtask_mode"
        private const val KEY_MANUAL_SORT = "manual_sort"
        private const val KEY_ASTRID_SORT = "astrid_sort"
        private const val KEY_SORT_ASCENDING = "sort_ascending"
        private const val KEY_GROUP_ASCENDING = "group_ascending"
        private const val KEY_COMPLETED_ASCENDING = "completed_ascending"
        private const val KEY_SUBTASK_ASCENDING = "subtask_ascending"
        private const val KEY_COMPLETED_AT_BOTTOM = "completed_at_bottom"
        private const val KEY_SHOW_COMPLETED_SUBTASKS = "show_completed_subtasks"
        private const val KEY_PREFIX = "filter_sort"

        suspend fun TasksPreferences.delete(filterKey: String) {
            removeByPrefix("${KEY_PREFIX}_${filterKey}_")
        }

        suspend fun TasksPreferences.delete(filterKeys: Collection<String>) {
            removeByPrefixes(filterKeys.map { "${KEY_PREFIX}_${it}_" })
        }
    }
}
