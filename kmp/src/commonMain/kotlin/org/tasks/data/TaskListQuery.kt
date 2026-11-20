package org.tasks.data

import co.touchlab.kermit.Logger
import kotlin.jvm.JvmStatic
import org.tasks.data.TaskListQueryNonRecursive.getNonRecursiveQuery
import org.tasks.data.TaskListQueryRecursive.getRecursiveQuery
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Geofence
import org.tasks.data.entity.Place
import org.tasks.data.entity.Task
import org.tasks.data.sql.Criterion
import org.tasks.data.sql.Field.Companion.field
import org.tasks.data.sql.Join
import org.tasks.constructor.Constructor
import org.tasks.db.QueryUtils.showHidden
import org.tasks.filters.AstridOrderingFilter
import org.tasks.filters.Filter
import org.tasks.filters.MemosFilter
import org.tasks.filters.SearchFilter
import org.tasks.preferences.QueryPreferences
import org.tasks.time.DateTimeUtils2.currentTimeMillis

object TaskListQuery {
    /** Pinned first, latest pin on top; a done or trashed task keeps its pin but does not lead. */
    const val PINNED_ORDER =
        "(CASE WHEN tasks.completed = 0 AND tasks.deleted = 0 THEN tasks.pinned ELSE 0 END)"

    private const val CALDAV_METADATA_JOIN = "for_caldav"
    private val JOIN_CALDAV = Criterion.and(
            Task.ID.eq(field("$CALDAV_METADATA_JOIN.cd_task")),
            field("$CALDAV_METADATA_JOIN.cd_deleted").eq(0))
    val JOINS = """
        ${Join.left(CaldavTask.TABLE.`as`(CALDAV_METADATA_JOIN), JOIN_CALDAV)}
        ${Join.left(CaldavCalendar.TABLE, field("$CALDAV_METADATA_JOIN.cd_calendar").eq(CaldavCalendar.UUID))}
        ${Join.left(CaldavAccount.TABLE, CaldavCalendar.ACCOUNT.eq(CaldavAccount.UUID))}
        ${Join.left(Geofence.TABLE, Geofence.TASK.eq(Task.ID))}
        ${Join.left(Place.TABLE, Place.UID.eq(Geofence.PLACE))}
    """.trimIndent()
    val FIELDS = listOf(
            field("tasks.*"),
            field("$CALDAV_METADATA_JOIN.*"),
            field("${CaldavAccount.ACCOUNT_TYPE}").`as`("accountType"),
            field("geofences.*"),
            field("places.*"),
            field(
                "(SELECT time FROM alarms WHERE alarms.task = tasks._id AND type = ${org.tasks.data.entity.Alarm.TYPE_SNOOZE} LIMIT 1)"
            ).`as`("snooze_time"),
        )

    @JvmStatic
    fun getQuery(
        preferences: QueryPreferences,
        filter: Filter,
        limit: Int? = null,
        showCollapsed: Boolean = false,
        features: Constructor? = null,
    ): String {
        val start = currentTimeMillis()
        // Memos never nest, and only the recursive query orders by `order` inside priority bands.
        val flat = features != null && filter !is MemosFilter
        val pinning = features?.pinning == true
        val sql = when {
            flat -> getNonRecursiveQuery(filter, preferences, limit, pinning)
            filter.supportsManualSort() && preferences.isManualSort ->
                getRecursiveQuery(filter, preferences, limit, showCollapsed, pinning)

            filter is AstridOrderingFilter && preferences.isAstridSort ->
                getNonRecursiveQuery(filter, preferences, limit, pinning)

            filter.supportsSorting() ->
                getRecursiveQuery(filter, preferences, limit, showCollapsed, pinning)

            else -> getNonRecursiveQuery(filter, preferences, limit, pinning)
        }
        return sql
            .let { if (flat) it.replace("(tasks.parent=0)", "(1)") else it }
            .let { if (features?.startDate == false) showHidden(it) else it }
            .also { Logger.v("TaskListQuery") { "Building query took ${currentTimeMillis() - start}ms" } }
    }
}
