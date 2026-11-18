package org.tasks.memos

import com.todoroo.astrid.core.SortHelper
import org.tasks.preferences.QueryPreferences

class MemosQueryPreferences(
    private val base: QueryPreferences,
    private val memosSortMode: Int,
    private val groupByPriority: Boolean = true,
) : QueryPreferences by base {

    override var sortMode: Int
        get() = when (memosSortMode) {
            SortHelper.SORT_MODIFIED -> SortHelper.SORT_MODIFIED
            else -> SortHelper.SORT_CALDAV
        }
        set(_) {}

    override var groupMode: Int
        get() = when {
            memosSortMode == SortHelper.SORT_MODIFIED || !groupByPriority -> SortHelper.GROUP_NONE
            else -> SortHelper.SORT_IMPORTANCE
        }
        set(_) {}

    override var sortAscending: Boolean
        get() = memosSortMode != SortHelper.SORT_MODIFIED && base.sortAscending
        set(value) {
            base.sortAscending = value
        }

    override var groupAscending: Boolean
        get() = base.groupAscending
        set(value) {
            base.groupAscending = value
        }
}