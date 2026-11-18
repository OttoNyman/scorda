package org.tasks.filters

import org.tasks.CommonParcelize
import org.tasks.data.entity.Task
import org.tasks.data.sql.Criterion.Companion.and
import org.tasks.data.sql.QueryTemplate
import org.tasks.resources.getString
import org.tasks.themes.TasksIcons
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.filter_memos

@CommonParcelize
data class MemosFilter(
    override val title: String,
) : Filter() {
    override val icon: String
        get() = TasksIcons.NOTE

    override val sql: String
        get() = QueryTemplate()
            .where(and(Task.MEMO.eq(1), Task.DELETION_DATE.lte(0)))
            .toString()

    override fun supportsHiddenTasks() = false

    override fun supportsSubtasks() = false

    override fun supportsManualSort() = false

    override fun supportsSorting() = true

    override fun areItemsTheSame(other: FilterListItem): Boolean {
        return other is MemosFilter
    }

    companion object {
        const val FILTER_KEY = "builtin_memos"

        suspend fun create() = MemosFilter(getString(Res.string.filter_memos))
    }
}