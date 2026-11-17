package org.tasks.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import org.tasks.data.entity.Series
import org.tasks.data.entity.Task

@Dao
interface SeriesDao {
    @Insert
    suspend fun insert(series: Series): Long

    @Update
    suspend fun update(series: Series)

    @Query("SELECT * FROM series WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): Series?

    @Query("SELECT * FROM series")
    suspend fun getAll(): List<Series>

    @Query("SELECT * FROM series WHERE finished = 0")
    suspend fun getActive(): List<Series>

    @Query("SELECT * FROM tasks WHERE series = :uuid AND deleted = 0")
    suspend fun getOccurrences(uuid: String): List<Task>

    @Query("SELECT * FROM tasks WHERE series = :uuid AND deleted = 0 AND scheduled = :scheduled")
    suspend fun findOccurrence(uuid: String, scheduled: Long): Task?

    @Query("""
        SELECT * FROM tasks
        WHERE recurrence IS NOT NULL AND recurrence != '' AND series IS NULL AND deleted = 0
    """)
    suspend fun getLegacyRepeating(): List<Task>

    /** Series that no task row refers to any more, trashed rows included. */
    @Query("DELETE FROM series WHERE uuid NOT IN (SELECT series FROM tasks WHERE series IS NOT NULL)")
    suspend fun deleteOrphans()
}
