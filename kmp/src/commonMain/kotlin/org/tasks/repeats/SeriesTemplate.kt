package org.tasks.repeats

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.tasks.checklist.Checklist
import org.tasks.data.TaskSaver
import org.tasks.data.dao.AlarmDao
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.GoogleTaskDao
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.TagDao
import org.tasks.data.dao.TagDataDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_GOOGLE_TASKS
import org.tasks.data.entity.CaldavAccount.Companion.pushesRemoteParent
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Geofence
import org.tasks.data.entity.Series
import org.tasks.data.entity.Task
import org.tasks.preferences.AppPreferences

/** What an occurrence looks like; stored as JSON in [Series.template]. */
@Serializable
data class SeriesTemplate(
    val title: String? = null,
    val notes: String? = null,
    val priority: Int = Task.Priority.NONE,
    /** `hideUntil - dueDate`, null when the task has no start date. */
    val startOffset: Long? = null,
    val alarms: List<AlarmTemplate> = emptyList(),
    /** [org.tasks.data.entity.TagData] remote ids. */
    val tags: List<String> = emptyList(),
    /** Place uid. */
    val place: String? = null,
    val arrival: Boolean = false,
    val departure: Boolean = false,
    /** Calendar uuid. */
    val list: String? = null,
    /** Only in templates saved before subtasks were retired; never captured or emitted. */
    val subtasks: List<SeriesTemplate> = emptyList(),
    /** Subtasks only: `subtask.dueDate - parent.dueDate`, null means no due date. */
    val dueOffset: Long? = null,
)

@Serializable
data class AlarmTemplate(
    val type: Int,
    /** For [Alarm.TYPE_DATE_TIME] it is `alarm time - due date` of the captured task (absolute if the emitted row gets no due). */
    val time: Long = 0,
    val repeat: Int = 0,
    val interval: Long = 0,
)

class SeriesTemplates(
    private val taskDao: TaskDao,
    private val taskSaver: TaskSaver,
    private val alarmDao: AlarmDao,
    private val tagDao: TagDao,
    private val tagDataDao: TagDataDao,
    private val locationDao: LocationDao,
    private val caldavDao: CaldavDao,
    private val googleTaskDao: GoogleTaskDao,
    private val preferences: AppPreferences,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(t: SeriesTemplate): String = json.encodeToString(SeriesTemplate.serializer(), t)

    fun decode(json: String): SeriesTemplate =
        this.json.decodeFromString(SeriesTemplate.serializer(), json)

    suspend fun capture(taskId: Long): SeriesTemplate = capture(taskDao.fetch(taskId)!!, null)

    private suspend fun capture(task: Task, parentDue: Long?): SeriesTemplate {
        // emit gives this row a due date only when it has one itself and (for subtasks) so does its parent
        val hasDue = task.hasDueDate() && (parentDue == null || parentDue > 0)
        val geofence = locationDao.getGeofencesForTask(task.id).firstOrNull()
        return SeriesTemplate(
            title = task.title,
            notes = task.notes,
            priority = task.priority,
            startOffset = if (task.hasStartDate() && task.hasDueDate()) {
                task.hideUntil - task.dueDate
            } else {
                null
            },
            alarms = alarmDao.getAlarms(task.id)
                .filter { it.type != Alarm.TYPE_SNOOZE }
                .map { AlarmTemplate(it.type, if (it.type == Alarm.TYPE_DATE_TIME && hasDue) it.time - task.dueDate else it.time, it.repeat, it.interval) },
            tags = tagDataDao.getTagDataForTask(task.id).mapNotNull { it.remoteId },
            place = geofence?.place,
            arrival = geofence?.isArrival ?: false,
            departure = geofence?.isDeparture ?: false,
            list = caldavDao.getTask(task.id)?.calendar,
            dueOffset = if (parentDue != null && parentDue > 0 && task.hasDueDate()) task.dueDate - parentDue else null,
        )
    }

    /**
     * [t] in a list that exists: its own, else the list of the newest of [occurrences] that is in
     * one (lists move and go: sync, list deletion, logout). Null when there is neither.
     */
    suspend fun inLiveList(t: SeriesTemplate, occurrences: List<Task>): SeriesTemplate? {
        val list = t.list ?: return t
        if (caldavDao.getCalendarByUuid(list) != null) {
            return t
        }
        val live = occurrences
            .sortedByDescending { it.scheduled }
            .firstNotNullOfOrNull { row ->
                caldavDao.getTask(row.id)?.calendar?.takeIf { caldavDao.getCalendarByUuid(it) != null }
            }
            ?: return null
        return t.inList(live)
    }

    private fun SeriesTemplate.inList(uuid: String): SeriesTemplate =
        copy(list = list?.let { uuid }, subtasks = subtasks.map { it.inList(uuid) })

    suspend fun emit(series: Series, scheduled: Long, template: SeriesTemplate): Task =
        create(template, series.uuid, scheduled, scheduled, parent = null)

    private suspend fun create(
        t: SeriesTemplate,
        series: String?,
        scheduled: Long,
        due: Long,
        parent: Task?,
    ): Task {
        val task = Task(
            title = t.title,
            notes = Checklist.uncheckAll(t.notes),
            priority = t.priority,
            dueDate = due,
            hideUntil = if (t.startOffset != null && due > 0) due + t.startOffset else 0,
            series = series,
            scheduled = scheduled,
            parent = parent?.id ?: 0,
        )
        if (parent != null) {
            task.suppressRefresh()
        }
        taskDao.createNew(task)
        addToList(task, t.list, parent)
        tagDao.insert(task, tagDataDao.getByUuid(t.tags))
        if (t.place != null) {
            locationDao.insert(
                Geofence(
                    task = task.id,
                    place = t.place,
                    isArrival = t.arrival,
                    isDeparture = t.departure,
                )
            )
        }
        if (t.alarms.isNotEmpty()) {
            alarmDao.insert(t.alarms.map { Alarm(0, task.id, if (it.type == Alarm.TYPE_DATE_TIME) it.time + due else it.time, it.type, it.repeat, it.interval) })
        }
        taskSaver.save(task, null)
        return task
    }

    private suspend fun addToList(task: Task, list: String?, parent: Task?) {
        if (list == null) return
        val accountType = caldavDao.getCalendarByUuid(list)?.account
            ?.let { caldavDao.getAccountByUuid(it) }
            ?.accountType
        // subtasks go in capture order, so they are added at the bottom
        val top = parent == null && preferences.addTasksToTop()
        if (accountType == TYPE_GOOGLE_TASKS) {
            googleTaskDao.insertAndShift(task, CaldavTask(task = task.id, calendar = list, remoteId = null), top)
        } else {
            val caldavTask = CaldavTask(task = task.id, calendar = list)
            if (parent != null && pushesRemoteParent(accountType)) {
                caldavTask.remoteParent = caldavDao.getRemoteIdForTask(parent.id)
            }
            caldavDao.insert(task, caldavTask, top)
        }
    }
}
