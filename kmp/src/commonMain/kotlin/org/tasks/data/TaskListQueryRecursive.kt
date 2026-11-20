package org.tasks.data

import com.todoroo.astrid.api.PermaSql
import com.todoroo.astrid.core.SortHelper
import org.tasks.data.dao.TaskDao.TaskCriteria.activeAndVisible
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Task
import org.tasks.data.sql.Criterion
import org.tasks.data.sql.Join
import org.tasks.data.sql.QueryTemplate
import org.tasks.filters.CaldavFilter
import org.tasks.filters.Filter
import org.tasks.preferences.QueryPreferences

internal object TaskListQueryRecursive {
    fun getRecursiveQuery(
        filter: Filter,
        preferences: QueryPreferences,
        limit: Int? = null,
        showCollapsed: Boolean = false,
        pinning: Boolean = false,
    ): String {
        val pinSort = if (pinning) TaskListQuery.PINNED_ORDER else "0"
        val parentQuery = when (filter) {
            is CaldavFilter -> newCaldavQuery(filter.uuid)
            else -> PermaSql.replacePlaceholdersForQuery(filter.sql!!)
        }
        val groupMode =
            if (preferences.groupMode == SortHelper.GROUP_NONE) SortHelper.GROUP_NONE
            else SortHelper.SORT_DUE
        val sortMode = SortHelper.SORT_DUE
        val subtaskMode = SortHelper.SORT_DUE
        val completedMode = SortHelper.SORT_COMPLETED
        val groupAscending =
            preferences.groupAscending && groupMode != SortHelper.GROUP_NONE
        val sortAscending = true
        val subtaskAscending = true
        val completedAtBottom = preferences.completedTasksAtBottom
        val parentCompleted = if (completedAtBottom) "tasks.completed > 0" else "0"
        val hideCompletedSubtasks = if (preferences.showCompletedSubtasks) {
            ""
        } else {
            "AND NOT (tasks.completed > 0 AND indent > 0)"
        }
        val hideCompletedFromCount = if (preferences.showCompletedSubtasks) {
            ""
        } else {
            "AND NOT r.subtask_complete"
        }
        val folded = if (showCollapsed) "0" else "recursive_tasks.collapsed > 0"
        val completionSort = if (completedAtBottom) {
            "(CASE WHEN tasks.completed > 0 THEN ${SortHelper.orderSelectForSortTypeRecursive(completedMode, false, preferences)} ELSE 0 END)"
        } else {
            "0"
        }
        val query = """
            WITH RECURSIVE recursive_tasks AS (
                SELECT
                    tasks._id AS task,
                    $parentCompleted AS parent_complete,
                    0 AS subtask_complete,
                    $completionSort AS completion_sort,
                    0 AS parent,
                    tasks.collapsed AS collapsed,
                    0 AS hidden,
                    0 AS indent,
                    UPPER(tasks.title) AS sort_title,
                    $pinSort AS pin_sort,
                    ${SortHelper.orderSelectForSortTypeRecursive(groupMode, true, preferences)} AS primary_group,
                    ${SortHelper.orderSelectForSortTypeRecursive(sortMode, false, preferences)} AS primary_sort,
                    NULL as secondary_sort,
                    ${SortHelper.getSortGroup(groupMode)} AS sort_group,
                    '/' || tasks._id || '/' as recursive_path
                FROM tasks
                $parentQuery
                UNION ALL SELECT
                    tasks._id AS task,
                    recursive_tasks.parent_complete AS parent_complete,
                    tasks.completed > 0 AS subtask_complete,
                    $completionSort AS completion_sort,
                    recursive_tasks.task AS parent,
                    tasks.collapsed AS collapsed,
                    CASE WHEN $folded OR recursive_tasks.hidden > 0 THEN 1 ELSE 0 END AS hidden,
                    recursive_tasks.indent + 1 AS indent,
                    UPPER(tasks.title) AS sort_title,
                    recursive_tasks.pin_sort AS pin_sort,
                    recursive_tasks.primary_group AS primary_group,
                    recursive_tasks.primary_sort AS primary_sort,
                    ${SortHelper.orderSelectForSortTypeRecursive(subtaskMode, false, preferences)} AS secondary_sort,
                    recursive_tasks.sort_group AS sort_group,
                    recursive_tasks.recursive_path || tasks._id || '/' AS recursive_path
                FROM tasks
                INNER JOIN recursive_tasks ON tasks.parent = recursive_tasks.task
                WHERE
                    ${activeAndVisible()}
                    AND recursive_tasks.recursive_path NOT LIKE '%/' || tasks._id || '/%'
                ORDER BY
                    parent_complete,
                    indent DESC,
                    pin_sort DESC,
                    ${if (completedAtBottom) "subtask_complete," else ""}
                    completion_sort ${if (preferences.completedAscending) "" else "DESC"},
                    ${SortHelper.orderForGroupTypeRecursive(groupMode, groupAscending)},
                    ${SortHelper.orderForSortTypeRecursive(sortMode, sortAscending, subtaskMode, subtaskAscending)}
            ),
            max_indent AS (
                SELECT
                    *,
                    MAX(recursive_tasks.indent) OVER (PARTITION BY task) AS max_indent,
                    ROW_NUMBER() OVER () AS sequence
                FROM recursive_tasks
            ),
            descendants_recursive AS (
                SELECT
                    r.parent,
                    r.task as descendant,
                    r.subtask_complete as descendant_complete
                FROM recursive_tasks r
                WHERE r.parent > 0 $hideCompletedFromCount
                UNION
                SELECT
                    d.parent,
                    r.task as descendant,
                    r.subtask_complete as descendant_complete
                FROM recursive_tasks r
                    JOIN descendants_recursive d ON r.parent = d.descendant $hideCompletedFromCount
            ),
            descendants AS (
                SELECT
                    parent,
                    COUNT(*) as children,
                    SUM(NOT descendant_complete) as uncompleted_children
                FROM descendants_recursive
                GROUP BY parent
            )
            SELECT
                ${TaskListQuery.FIELDS.joinToString(",\n") { it.toStringInSelect() }},
                group_concat(distinct(tag_uid)) AS tags,
                indent,
                sort_group,
                children,
                uncompleted_children,
                primary_sort,
                secondary_sort,
                parent_complete
            FROM tasks
                INNER JOIN max_indent
                    ON tasks._id = max_indent.task
                    AND indent = max_indent
                    AND hidden = 0
                    $hideCompletedSubtasks
                LEFT JOIN descendants ON descendants.parent = tasks._id
                LEFT JOIN tags ON tags.task = tasks._id
                ${TaskListQuery.JOINS}
            GROUP BY tasks._id
            ORDER BY sequence
            ${limit?.let { "LIMIT $it" } ?: ""}
        """.trimIndent()

        return SortHelper.adjustQueryForFlags(preferences, query)
    }

    private fun newCaldavQuery(list: String) =
            QueryTemplate()
                    .join(
                        Join.inner(
                            CaldavTask.TABLE,
                            Criterion.and(
                                    CaldavTask.CALENDAR.eq(list),
                                    CaldavTask.TASK.eq(Task.ID),
                                    CaldavTask.DELETED.eq(0))))
                    .where(Criterion.and(activeAndVisible(), Task.PARENT.eq(0)))
                    .toString()
}
