package org.tasks.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Transaction
import co.touchlab.kermit.Logger
import org.tasks.data.db.SuspendDbUtils.chunkedMap
import org.tasks.data.db.SuspendDbUtils.eachChunk
import org.tasks.data.db.Database
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavCalendar

@Dao
abstract class DeletionDao(private val database: Database) {
    @Query("DELETE FROM tasks WHERE _id IN(:ids)")
    internal abstract suspend fun deleteTasks(ids: List<Long>)

    suspend fun hasRecurringAncestors(ids: List<Long>): List<Long> =
            ids.chunkedMap { internalHasRecurringAncestors(it) }

    @Query("""
WITH RECURSIVE recursive_tasks (descendent, parent, recurring) AS (
    SELECT _id, parent, 0
    FROM tasks
    WHERE _id IN (:ids)
      AND parent > 0
    UNION ALL
    SELECT recursive_tasks.descendent,
           tasks.parent,
           CASE
               WHEN recursive_tasks.recurring THEN 1
               WHEN recurrence IS NOT NULL AND recurrence != '' AND completed = 0 THEN 1
               ELSE 0
               END
    FROM tasks
             INNER JOIN recursive_tasks ON recursive_tasks.parent = _id
)
SELECT DISTINCT(descendent)
FROM recursive_tasks
WHERE recurring = 1
    """)
    abstract suspend fun internalHasRecurringAncestors(ids: List<Long>): List<Long>

    @Transaction
    open suspend fun delete(
        ids: List<Long>,
        cleanup: suspend (List<Long>) -> Unit,
    ) {
        Logger.d("DeletionDao") { "delete ids=$ids" }
        ids.eachChunk { deleteTasks(it) }
        cleanup(ids)
    }

    @Query("UPDATE tasks "
            + "SET modified = (strftime('%s','now')*1000), deleted = (strftime('%s','now')*1000) "
            + "WHERE _id IN(:ids)")
    internal abstract suspend fun markDeletedInternal(ids: List<Long>)

    @Transaction
    open suspend fun markDeleted(
        ids: Iterable<Long>,
        cleanup: suspend (List<Long>) -> Unit,
    ) {
        Logger.d("DeletionDao") { "markDeleted ids=$ids" }
        ids.eachChunk {
            markDeletedInternal(it)
            database.dirtyDao().setDirty(it)
        }
        cleanup(ids.toList())
    }

    @Query("UPDATE tasks SET modified = (strftime('%s','now')*1000), deleted = $DISCARDED WHERE _id IN(:ids)")
    internal abstract suspend fun markDiscardedInternal(ids: List<Long>)

    /** Like [markDeleted], but with the fixed [DISCARDED] stamp: the rows stay out of the trash. */
    @Transaction
    open suspend fun markDiscarded(
        ids: Iterable<Long>,
        cleanup: suspend (List<Long>) -> Unit,
    ) {
        Logger.d("DeletionDao") { "markDiscarded ids=$ids" }
        ids.eachChunk {
            markDiscardedInternal(it)
            database.dirtyDao().setDirty(it)
        }
        cleanup(ids.toList())
    }

    @Query("SELECT cd_task FROM caldav_tasks WHERE cd_calendar = :calendar AND cd_deleted = 0")
    internal abstract suspend fun getActiveCaldavTasks(calendar: String): List<Long>

    @Delete
    internal abstract suspend fun deleteCaldavCalendar(caldavCalendar: CaldavCalendar)

    @Transaction
    open suspend fun delete(
        caldavCalendar: CaldavCalendar,
        cleanup: suspend (List<Long>) -> Unit,
    ) {
        Logger.d("DeletionDao") { "deleting $caldavCalendar" }
        val tasks = getActiveCaldavTasks(caldavCalendar.uuid!!)
        delete(tasks, cleanup)
        deleteCaldavCalendar(caldavCalendar)
    }

    @Query("SELECT * FROM caldav_lists WHERE cdl_account = :account")
    abstract suspend fun getCalendars(account: String): List<CaldavCalendar>

    @Delete
    internal abstract suspend fun deleteCaldavAccount(caldavAccount: CaldavAccount)

    /**
     * Trashed tasks older than [before] with nothing left on a server. A task that still has a
     * remote id is waiting for its delete to be pushed and is skipped until then.
     */
    @Query("""
        SELECT _id FROM tasks
        WHERE deleted > 0 AND deleted < :before AND _id NOT IN (
            SELECT cd_task FROM caldav_tasks
                INNER JOIN caldav_lists ON cdl_uuid = cd_calendar
                INNER JOIN caldav_accounts ON cdl_account = cda_uuid
                WHERE cda_account_type != ${CaldavAccount.TYPE_LOCAL} AND cd_deleted = 0
                  AND cd_remote_id IS NOT NULL AND cd_remote_id != '')
    """)
    abstract suspend fun getPurgeable(before: Long): List<Long>

    /** [ids] plus the subtasks that went to the trash with them (same deletion timestamp). */
    @Query("""
WITH RECURSIVE trashed (task, stamp) AS (
    SELECT _id, deleted
    FROM tasks
    WHERE _id IN (:ids)
      AND deleted > 0
    UNION ALL
    SELECT tasks._id, trashed.stamp
    FROM tasks
             INNER JOIN trashed ON trashed.task = tasks.parent
    WHERE tasks.deleted = trashed.stamp)
SELECT DISTINCT task
FROM trashed
    """)
    internal abstract suspend fun getTrashedTree(ids: List<Long>): List<Long>

    @Query("UPDATE tasks SET modified = (strftime('%s','now')*1000), deleted = 0 WHERE _id IN (:ids)")
    internal abstract suspend fun restoreInternal(ids: List<Long>)

    @Query("""
        UPDATE tasks SET parent = 0
        WHERE _id IN (:ids) AND parent > 0
          AND parent NOT IN (SELECT _id FROM tasks WHERE deleted = 0)
    """)
    internal abstract suspend fun detachFromTrashedParents(ids: List<Long>)

    /** Takes tasks out of the trash and returns every id that came back. */
    @Transaction
    open suspend fun restore(ids: List<Long>): List<Long> {
        val restored = ids.chunkedMap { getTrashedTree(it) }.distinct()
        Logger.d("DeletionDao") { "restore ids=$restored" }
        restored.eachChunk { restoreInternal(it) }
        restored.eachChunk {
            detachFromTrashedParents(it)
            database.dirtyDao().setDirty(it)
        }
        return restored
    }

    @Query("UPDATE tasks SET deleted = $EXPIRED WHERE _id IN (:ids) AND deleted > 0")
    internal abstract suspend fun expireInternal(ids: List<Long>)

    /** Marks trashed tasks as due for purging now; they no longer show in the trash. */
    suspend fun expire(ids: List<Long>) = ids.eachChunk { expireInternal(it) }

    @Query("SELECT _id FROM tasks WHERE deleted > $DISCARDED")
    abstract suspend fun getTrash(): List<Long>

    @Transaction
    open suspend fun delete(
        caldavAccount: CaldavAccount,
        cleanup: suspend (List<Long>) -> Unit,
    ): List<CaldavCalendar> {
        Logger.d("DeletionDao") { "deleting $caldavAccount" }
        val calendars = getCalendars(caldavAccount.uuid!!)
        calendars.forEach { delete(it, cleanup) }
        deleteCaldavAccount(caldavAccount)
        return calendars
    }

    @Query("""
        SELECT CASE
            WHEN deleted > 0 THEN 1
            ELSE 0
        END
        FROM tasks
        WHERE _id = :task
    """)
    abstract suspend fun isDeleted(task: Long): Boolean

    companion object {
        /** Deletion timestamp of a trashed task the user asked to delete for good. */
        const val EXPIRED = 1L

        /** Deletion timestamp of a row the repeat schedule dropped: purged, never shown in the trash. */
        const val DISCARDED = 2L
    }
}
