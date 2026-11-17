package org.tasks.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.tasks.data.db.Table

/**
 * A repeating schedule. [anchor] is the `scheduled` value of the latest emitted occurrence,
 * encoded like [Task.dueDate].
 */
@Serializable
@Entity(
    tableName = Series.TABLE_NAME,
    indices = [Index(name = "index_series_uuid", value = ["uuid"], unique = true)],
)
data class Series(
    @PrimaryKey(autoGenerate = true)
    @Transient
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    @ColumnInfo(name = "uuid")
    val uuid: String,
    @ColumnInfo(name = "recurrence")
    val recurrence: String,
    @ColumnInfo(name = "repeat_from", defaultValue = Task.RepeatFrom.DUE_DATE.toString())
    val repeatFrom: Int = Task.RepeatFrom.DUE_DATE,
    @ColumnInfo(name = "anchor")
    val anchor: Long,
    @ColumnInfo(name = "template")
    val template: String,
    @ColumnInfo(name = "created")
    val created: Long,
    @ColumnInfo(name = "modified")
    val modified: Long,
    @ColumnInfo(name = "finished", defaultValue = "0")
    val finished: Long = 0,
) {
    companion object {
        const val TABLE_NAME = "series"
        val TABLE = Table(TABLE_NAME)
    }
}
