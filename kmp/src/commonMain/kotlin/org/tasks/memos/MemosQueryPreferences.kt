package org.tasks.memos

import com.todoroo.astrid.core.SortHelper
import org.tasks.preferences.QueryPreferences

class MemosQueryPreferences(
    private val base: QueryPreferences,
    private val memosSortMode: Int,
    private val groupByPriority: Boolean = true,
) : QueryPreferences by base {

    private val myOrder = MemosPreferences.isImportanceMode(memosSortMode)

    override var sortMode: Int
        get() = if (myOrder) SortHelper.SORT_CALDAV else memosSortMode
        set(_) {}

    override var groupMode: Int
        get() = if (myOrder && groupByPriority) SortHelper.SORT_IMPORTANCE else SortHelper.GROUP_NONE
        set(_) {}

    override var sortAscending: Boolean
        get() = myOrder && base.sortAscending
        set(value) {
            base.sortAscending = value
        }

    override var groupAscending: Boolean
        get() = base.groupAscending
        set(value) {
            base.groupAscending = value
        }
}