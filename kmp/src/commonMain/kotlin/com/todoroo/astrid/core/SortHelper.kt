package com.todoroo.astrid.core

import org.tasks.data.dao.ORDER_BY_MANUAL
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.Task
import org.tasks.data.sql.Functions.upper
import org.tasks.data.sql.Order
import org.tasks.data.sql.Order.Companion.asc
import org.tasks.data.sql.Order.Companion.desc
import org.tasks.data.sql.OrderType
import org.tasks.db.QueryUtils.showCompleted
import org.tasks.db.QueryUtils.showHidden
import org.tasks.preferences.QueryPreferences

object SortHelper {
    const val GROUP_NONE: Int = -1
    const val SORT_AUTO: Int = 0
    const val SORT_ALPHA: Int = 1
    const val SORT_DUE: Int = 2
    const val SORT_IMPORTANCE: Int = 3
    const val SORT_MODIFIED: Int = 4
    const val SORT_CREATED: Int = 5
    const val SORT_GTASKS: Int = 6
    const val SORT_CALDAV: Int = 7
    const val SORT_START: Int = 8
    const val SORT_LIST: Int = 9
    const val SORT_COMPLETED: Int = 10
    const val SORT_MANUAL: Int = 11

    private const val NO_TIME_TO_END_OF_DAY = 43_140_000L
    private const val NO_TIME_TO_START_OF_DAY = 43_200_000L

    private const val ADJUSTED_START_DATE =
        "(CASE WHEN (hideUntil / 1000) % 60 > 0 THEN hideUntil ELSE (hideUntil + $NO_TIME_TO_END_OF_DAY) END)"

    private const val NO_DATE = 3538339200000L

    private const val GROUP_START_DATE =
        ("((CASE WHEN (tasks.hideUntil=0) THEN " + NO_DATE + " ELSE "
                + "tasks.hideUntil END)+tasks.importance * 1000)")

    private val SORT_START_DATE = ("((CASE WHEN (tasks.hideUntil=0) THEN " + NO_DATE + " ELSE "
            + ADJUSTED_START_DATE.replace("hideUntil", "tasks.hideUntil")
            + " END)+tasks.importance * 1000)")

    private val ORDER_TITLE = asc(upper(Task.TITLE))
    private val ORDER_LIST = asc(upper(CaldavCalendar.ORDER))
        .addSecondaryExpression(asc(CaldavCalendar.NAME))

    private fun undatedValue(atBottom: Boolean): Long = if (atBottom) NO_DATE else 0L

    private fun adjustedDueDate(
        column: String,
        noTimeAtEndOfDay: Boolean,
        importance: String,
    ): String {
        val shift = if (noTimeAtEndOfDay) "+ $NO_TIME_TO_END_OF_DAY" else "- $NO_TIME_TO_START_OF_DAY"
        val placed = "($column $shift) + $importance * 1000"
        return "(CASE WHEN ($column / 1000) % 60 > 0 THEN $column ELSE $placed END)"
    }

    private fun groupDueDate(undatedAtBottom: Boolean): String {
        val undated = undatedValue(undatedAtBottom)
        return "(CASE WHEN (tasks.dueDate=0) THEN $undated ELSE tasks.dueDate END)"
    }

    private fun sortDueDate(undatedAtBottom: Boolean, noTimeAtEndOfDay: Boolean): String {
        val undated = undatedValue(undatedAtBottom)
        val adjusted = adjustedDueDate("tasks.dueDate", noTimeAtEndOfDay, "tasks.importance")
        return "(CASE WHEN (tasks.dueDate=0) THEN $undated + tasks.importance * 1000 ELSE $adjusted END)"
    }

    /** Takes a SQL query, and if there isn't already an order, creates an order.  */
    fun adjustQueryForFlagsAndSort(
        preferences: QueryPreferences, originalSql: String?, sort: Int
    ): String {
        var originalSql = originalSql
        if (originalSql == null) {
            originalSql = ""
        }
        if (!originalSql.contains("ORDER BY", ignoreCase = true)) {
            var order = orderForSortType(sort, preferences)

            if (order.orderType == OrderType.ASC != preferences.sortAscending) {
                order = order.reverse()
            }
            originalSql += " ORDER BY $order"
        }

        return adjustQueryForFlags(preferences, originalSql)
    }

    fun adjustQueryForFlags(preferences: QueryPreferences, originalSql: String): String {
        var adjustedSql = originalSql

        if (preferences.showCompleted) {
            adjustedSql = showCompleted(adjustedSql)
        }
        if (preferences.showHidden) {
            adjustedSql = showHidden(adjustedSql)
        }

        return adjustedSql
    }

    private fun orderForSortType(sortType: Int, preferences: QueryPreferences): Order {
        val undated = undatedValue(preferences.undatedAtBottom)
        val adjusted = adjustedDueDate("dueDate", preferences.noTimeAtEndOfDay, "importance")
        val order = when (sortType) {
            SORT_ALPHA -> ORDER_TITLE
            SORT_DUE -> asc(
                "(CASE WHEN (dueDate=0) THEN $undated + importance * 1000 ELSE $adjusted END)"
            )
            SORT_START -> asc(
                "(CASE WHEN (hideUntil=0) THEN (strftime('%s','now')*1000)*2 ELSE "
                        + ADJUSTED_START_DATE
                        + " END)+importance"
            )
            SORT_IMPORTANCE -> asc("importance")
            SORT_MODIFIED -> desc(Task.MODIFICATION_DATE)
            SORT_CREATED -> desc(Task.CREATION_DATE)
            SORT_LIST -> ORDER_LIST
            else -> asc(
                "(CASE WHEN (dueDate=0) THEN $undated + importance * 1000 ELSE $adjusted END)"
            )
        }
        if (sortType != SORT_ALPHA) {
            order.addSecondaryExpression(ORDER_TITLE)
        }

        return order
    }

    fun getSortGroup(sortType: Int): String? {
        return when (sortType) {
            SORT_DUE -> "tasks.dueDate"
            SORT_START -> "tasks.hideUntil"
            SORT_IMPORTANCE -> "tasks.importance"
            SORT_MODIFIED -> "tasks.modified"
            SORT_CREATED -> "tasks.created"
            SORT_LIST -> "cdl_id"
            else -> null
        }
    }

    private fun sortGroup(column: String): String {
        return "datetime($column / 1000, 'unixepoch', 'localtime', 'start of day')"
    }

    fun orderSelectForSortTypeRecursive(
        sortType: Int,
        grouping: Boolean,
        preferences: QueryPreferences? = null,
    ): String {
        val undatedAtBottom = preferences?.undatedAtBottom ?: true
        val noTimeAtEndOfDay = preferences?.noTimeAtEndOfDay ?: true
        return when (sortType) {
            GROUP_NONE -> "1"
            SORT_ALPHA -> "UPPER(tasks.title)"
            SORT_DUE -> if (grouping) {
                sortGroup(groupDueDate(undatedAtBottom))
            } else {
                sortDueDate(undatedAtBottom, noTimeAtEndOfDay)
            }
            SORT_START -> if (grouping) sortGroup(GROUP_START_DATE) else SORT_START_DATE
            SORT_IMPORTANCE -> "tasks.importance"
            SORT_MODIFIED -> if (grouping) sortGroup("tasks.modified") else "tasks.modified"
            SORT_CREATED -> if (grouping) sortGroup("tasks.created") else "tasks.created"
            SORT_GTASKS -> "tasks.`order`"
            SORT_CALDAV -> ORDER_BY_MANUAL
            SORT_LIST -> "CASE WHEN cdl_order = -1 THEN cdl_name ELSE cdl_order END"
            SORT_COMPLETED -> "tasks.completed"
            else -> sortDueDate(undatedAtBottom, noTimeAtEndOfDay)
        }
    }

    fun orderForGroupTypeRecursive(groupMode: Int, ascending: Boolean): Order {
        return if (ascending
        ) asc("primary_group")
        else desc("primary_group")
    }

    fun orderForSortTypeRecursive(
        sortMode: Int,
        primaryAscending: Boolean,
        secondaryMode: Int,
        secondaryAscending: Boolean
    ): Order {
        val order = if (primaryAscending || sortMode == SORT_GTASKS || sortMode == SORT_CALDAV
        ) asc("primary_sort")
        else desc("primary_sort")
        order.addSecondaryExpression(
            if (secondaryAscending || secondaryMode == SORT_GTASKS || secondaryMode == SORT_CALDAV
            ) asc("secondary_sort")
            else desc("secondary_sort")
        )
        if (sortMode != SORT_ALPHA) {
            order.addSecondaryExpression(asc("sort_title"))
        }
        return order
    }
}
