package org.tasks.memos

import androidx.datastore.preferences.core.intPreferencesKey
import com.todoroo.astrid.core.SortHelper
import kotlinx.coroutines.runBlocking
import org.tasks.preferences.TasksPreferences

object MemosPreferences {
    val sortMode = intPreferencesKey("p_memos_sort_mode")

    const val DEFAULT_SORT = SortHelper.SORT_IMPORTANCE

    fun read(tasksPreferences: TasksPreferences): Int =
        runBlocking { tasksPreferences.get(sortMode, DEFAULT_SORT) }

    suspend fun set(tasksPreferences: TasksPreferences, mode: Int) {
        tasksPreferences.set(sortMode, mode)
    }

    /** «Мой порядок»: the only mode that can be dragged. The others are SORT_MODIFIED and SORT_CREATED. */
    fun isImportanceMode(mode: Int): Boolean = mode == SortHelper.SORT_IMPORTANCE
}