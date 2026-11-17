package org.tasks.filters

import org.tasks.CommonParcelize
import org.tasks.data.dao.DeletionDao
import org.tasks.data.entity.Task
import org.tasks.data.sql.Criterion.Companion.and
import org.tasks.data.sql.Order.Companion.desc
import org.tasks.data.sql.QueryTemplate
import org.tasks.resources.getString
import org.tasks.themes.TasksIcons
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.completed
import tasks.kmp.generated.resources.trash

/** Every completed task from every list, newest first: the place to review what got done. */
@CommonParcelize
data class CompletedFilter(
    override val title: String,
) : Filter() {
    override val icon: String
        get() = TasksIcons.CHECK_CIRCLE

    override val sql: String
        get() = QueryTemplate()
            .where(and(Task.COMPLETION_DATE.gt(0), Task.DELETION_DATE.lte(0)))
            .orderBy(desc(Task.COMPLETION_DATE))
            .toString()

    override fun supportsHiddenTasks() = false

    override fun supportsSubtasks() = false

    override fun supportsSorting() = false

    override fun areItemsTheSame(other: FilterListItem): Boolean = other is CompletedFilter

    companion object {
        suspend fun create() = CompletedFilter(getString(Res.string.completed))
    }
}

/** Deleted tasks, kept for [org.tasks.service.TaskDeleter.TRASH_RETENTION_DAYS] days. */
@CommonParcelize
data class TrashFilter(
    override val title: String,
) : Filter() {
    override val icon: String
        get() = TasksIcons.DELETE

    override val sql: String
        get() = QueryTemplate()
            .where(Task.DELETION_DATE.gt(DeletionDao.DISCARDED))
            .orderBy(desc(Task.DELETION_DATE))
            .toString()

    override val isReadOnly: Boolean
        get() = true

    override fun supportsHiddenTasks() = false

    override fun supportsSubtasks() = false

    override fun supportsSorting() = false

    override fun areItemsTheSame(other: FilterListItem): Boolean = other is TrashFilter

    companion object {
        suspend fun create() = TrashFilter(getString(Res.string.trash))
    }
}
