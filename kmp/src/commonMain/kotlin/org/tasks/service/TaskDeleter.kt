package org.tasks.service

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.caldav.VtodoCache
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.DeletionDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.db.SuspendDbUtils.chunkedMap
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.Task
import org.tasks.filters.filterPreferencesKey
import org.tasks.preferences.FilterPreferences.Companion.delete
import org.tasks.preferences.TasksPreferences
import org.tasks.time.DateTimeUtils2.currentTimeMillis

class TaskDeleter(
    private val deletionDao: DeletionDao,
    private val taskDao: TaskDao,
    private val caldavDao: CaldavDao,
    private val refreshBroadcaster: RefreshBroadcaster,
    private val vtodoCache: VtodoCache,
    private val tasksPreferences: TasksPreferences,
    private val taskCleanup: TaskCleanup,
) {

    suspend fun markMoved(taskIds: List<Long>) {
        vtodoCache.delete(taskIds)
        caldavDao.markDeleted(taskIds)
    }

    suspend fun markDeleted(item: Task) = markDeleted(listOf(item.id))

    suspend fun markDeleted(taskIds: List<Long>): List<Task> = withContext(NonCancellable) {
        val ids = taskIds
            .toSet()
            .plus(taskIds.chunkedMap(taskDao::getChildren))
            .let { taskDao.fetch(it.toList()) }
            .filterNot { it.readOnly }
            .map { it.id }
        vtodoCache.delete(ids)
        deletionDao.markDeleted(
            ids = ids,
            cleanup = { taskCleanup.trashed(it) }
        )
        refreshBroadcaster.broadcastRefresh()
        taskDao.fetch(ids)
    }

    /**
     * Drops rows the schedule no longer wants, with their subtasks. They skip the trash and go on
     * the next [purge]; [restore] still brings them back before that.
     */
    suspend fun discard(taskIds: List<Long>) = withContext(NonCancellable) {
        val ids = taskIds
            .toSet()
            .plus(taskIds.chunkedMap(taskDao::getChildren))
            .toList()
        vtodoCache.delete(ids)
        deletionDao.markDiscarded(
            ids = ids,
            cleanup = { taskCleanup.trashed(it) }
        )
        refreshBroadcaster.broadcastRefresh()
    }

    /** Takes tasks out of the trash, with the subtasks that were trashed along with them. */
    suspend fun restore(taskIds: List<Long>): List<Task> = withContext(NonCancellable) {
        val ids = deletionDao.restore(taskIds)
        refreshBroadcaster.broadcastRefresh()
        taskDao.fetch(ids)
    }

    /** Deletes trashed tasks for good; null means the whole trash. */
    suspend fun deleteForever(taskIds: List<Long>? = null) = withContext(NonCancellable) {
        deletionDao.expire(taskIds ?: deletionDao.getTrash())
        purge()
    }

    /**
     * Drops trashed tasks past the retention period. Tasks whose delete has not reached the
     * server yet stay until a later run.
     */
    suspend fun purge(now: Long = currentTimeMillis()) {
        val ids = deletionDao.getPurgeable(now - TRASH_RETENTION_MILLIS)
        if (ids.isEmpty()) {
            return
        }
        vtodoCache.delete(ids)
        // before the rows go: comments are found through their task
        taskCleanup.cleanup(ids)
        deletionDao.delete(ids = ids, cleanup = {})
        refreshBroadcaster.broadcastRefresh()
    }

    suspend fun delete(task: Task) = delete(task.id)

    suspend fun delete(task: Long) = delete(listOf(task))

    suspend fun delete(tasks: List<Long>) {
        vtodoCache.delete(tasks)
        deletionDao.delete(
            ids = tasks,
            cleanup = { taskCleanup.cleanup(it) }
        )
        refreshBroadcaster.broadcastRefresh()
    }

    suspend fun delete(list: CaldavCalendar) {
        vtodoCache.delete(list)
        deletionDao.delete(
            caldavCalendar = list,
            cleanup = { taskCleanup.cleanup(it) }
        )
        tasksPreferences.delete(list.filterPreferencesKey())
        refreshBroadcaster.broadcastRefresh()
    }

    suspend fun delete(account: CaldavAccount) {
        vtodoCache.delete(account)
        val calendars = deletionDao.delete(
            caldavAccount = account,
            cleanup = { taskCleanup.cleanup(it) }
        )
        calendars.forEach { tasksPreferences.delete(it.filterPreferencesKey()) }
        refreshBroadcaster.broadcastRefresh()
    }

    suspend fun isDeleted(task: Long): Boolean = deletionDao.isDeleted(task)

    companion object {
        const val TRASH_RETENTION_DAYS = 30
        private const val TRASH_RETENTION_MILLIS = TRASH_RETENTION_DAYS * 24L * 60 * 60 * 1000
    }
}
