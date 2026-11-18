package org.tasks.data

import android.database.Cursor
import android.database.MatrixCursor
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.runBlocking
import org.tasks.data.dao.Astrid2ContentProviderDao
import org.tasks.data.db.Database
import org.tasks.data.entity.TagData
import org.tasks.data.entity.Task
import javax.inject.Inject

class ContentProviderDaoBlocking @Inject constructor(
    private val dao: Astrid2ContentProviderDao,
    private val database: Database,
) {
    fun getTagNames(taskId: Long): List<String> = runBlocking {
        dao.getTagNames(taskId)
    }

    fun getAstrid2TaskProviderTasks(): List<Task> = runBlocking {
        dao.getAstrid2TaskProviderTasks()
    }

    fun tagDataOrderedByName(): List<TagData> = runBlocking {
        dao.tagDataOrderedByName()
    }

    fun getTasks(): Cursor = runBlocking { rawQuery("SELECT * FROM tasks WHERE memo = 0") }

    fun queryTodoAgenda(selection: String?, selectionArgs: Array<out String>?): Cursor = runBlocking {
        val memoClause = "memo = 0"
        val combined = when {
            selection.isNullOrBlank() -> memoClause
            else -> "$memoClause AND ($selection)"
        }
        rawQuery(
            androidx.sqlite.db.SupportSQLiteQueryBuilder
                .builder(
                    """${Task.TABLE_NAME}
                        LEFT JOIN caldav_tasks ON cd_task = _id
                        LEFT JOIN caldav_lists ON cdl_uuid = cd_calendar""".trimIndent(),
                )
                .selection(combined, selectionArgs)
                .create()
                .sql,
        )
    }

    fun getLists(): Cursor = runBlocking {
        rawQuery("""
            SELECT caldav_lists.*, caldav_accounts.cda_name
            FROM caldav_lists
            INNER JOIN caldav_accounts ON cdl_account = cda_uuid
            """.trimIndent()
        )
    }

    fun rawQuery(query: String): Cursor = runBlocking {
        database.useReaderConnection { transactor ->
            transactor.usePrepared(query) { it.toCursor() }
        }
    }
}

private fun SQLiteStatement.toCursor(): Cursor {
    val cursor = MatrixCursor(getColumnNames().toTypedArray())
    while (step()) {
        cursor.addRow((0 until getColumnCount()).map { getTextOrNull(it) })
    }
    return cursor
}
