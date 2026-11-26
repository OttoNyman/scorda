package com.todoroo.astrid.service

import com.todoroo.astrid.api.PermaSql
import com.todoroo.astrid.utility.TitleParser.parse
import org.tasks.R
import org.tasks.data.GoogleTask
import org.tasks.data.TaskSaver
import org.tasks.data.UUIDHelper
import org.tasks.data.createDueDate
import org.tasks.data.createGeofence
import org.tasks.data.createHideUntil
import org.tasks.data.dao.AlarmDao
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.GoogleTaskDao
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.TagDao
import org.tasks.data.dao.TagDataDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Place
import org.tasks.data.entity.Tag
import org.tasks.data.entity.Task
import org.tasks.data.entity.Task.Companion.DUE_DATE
import org.tasks.data.entity.Task.Companion.HIDE_UNTIL
import org.tasks.data.entity.Task.Companion.HIDE_UNTIL_NONE
import org.tasks.data.entity.Task.Companion.IMPORTANCE
import org.tasks.data.entity.Task.Companion.URGENCY_SPECIFIC_DAY_TIME
import org.tasks.data.getDefaultAlarms
import org.tasks.data.setDefaultReminders
import org.tasks.filters.CaldavFilter
import org.tasks.filters.Filter
import org.tasks.filters.mapFromSerializedString
import org.tasks.jobs.WorkManager
import org.tasks.location.LocationService
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.preferences.Preferences
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.time.startOfDay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class TaskCreator @Inject constructor(
    private val preferences: Preferences,
    private val tagDataDao: TagDataDao,
    private val taskDao: TaskDao,
    private val taskSaver: TaskSaver,
    private val tagDao: TagDao,
    private val googleTaskDao: GoogleTaskDao,
    private val defaultFilterProvider: DefaultFilterProvider,
    private val caldavDao: CaldavDao,
    private val locationDao: LocationDao,
    private val alarmDao: AlarmDao,
    private val featureGate: org.tasks.constructor.FeatureGate,
    private val locationService: LocationService,
    private val workManager: WorkManager,
) {
    suspend fun createQuickAlarm(
        title: String?,
        dueAtMillis: Long,
        defaultTitle: String,
    ): Task = withContext(NonCancellable) {
        val resolved = title?.trim().orEmpty().ifEmpty { defaultTitle }
        val task = createWithValues(null, resolved, parseTitle = false, applyDefaults = false)
        task.priority = Task.Priority.HIGH
        task.dueDate = createDueDate(URGENCY_SPECIFIC_DAY_TIME, dueAtMillis)
        clearPlaceForSchedule(task, applyDefaults = false)
        taskDao.createNew(task)
        // Ring first: later follow-up failures must not leave a silent task that looks like success.
        try {
            alarmDao.insert(
                listOf(Alarm(type = Alarm.TYPE_REL_END, time = 0, task = task.id)),
            )
            persistAfterCreate(task, insertAlarms = false)
            workManager.triggerNotifications(expedited = true)
        } catch (e: Exception) {
            Timber.e(e, "quick alarm follow-up failed for taskId=${task.id}")
        }
        task
    }

    suspend fun basicQuickAddTask(
        title: String,
        filter: Filter? = null,
        parseTitle: Boolean = true,
        applyDefaults: Boolean = true,
        configure: (Task) -> Unit = {},
    ): Task {
        val task = createWithValues(filter, title.trim { it <= ' ' }, parseTitle, applyDefaults)
        configure(task)
        // Tasker (and other callers) can set a due date after create(). Date wins, and the
        // defaults create() skipped for a place task come back.
        clearPlaceForSchedule(task, applyDefaults)
        taskDao.createNew(task)
        persistAfterCreate(task, insertAlarms = true)
        return task
    }

    private suspend fun persistAfterCreate(task: Task, insertAlarms: Boolean) {
        tagDao.insert(task, task.tags)
        val addToTop = preferences.addTasksToTop()
        if (task.hasTransitory(GoogleTask.KEY)) {
            googleTaskDao.insertAndShift(
                task,
                CaldavTask(
                    task = task.id,
                    calendar = task.getTransitory<String>(GoogleTask.KEY)!!,
                    remoteId = null
                ),
                addToTop
            )
        } else if (task.hasTransitory(CaldavTask.KEY)) {
            caldavDao.insert(
                task,
                CaldavTask(
                    task = task.id,
                    calendar = task.getTransitory(CaldavTask.KEY),
                ),
                addToTop
            )
        } else {
            val remoteList = defaultFilterProvider.getDefaultList()
            if (remoteList is CaldavFilter) {
                if (remoteList.isGoogleTasks) {
                    googleTaskDao.insertAndShift(
                        task,
                        CaldavTask(
                            task = task.id,
                            calendar = remoteList.uuid,
                            remoteId = null
                        ),
                        addToTop
                    )
                } else {
                    caldavDao.insert(
                        task,
                        CaldavTask(
                            task = task.id,
                            calendar = remoteList.uuid,
                        ),
                        addToTop
                    )
                }
            }
        }
        val place = task.getTransitory<String>(Place.KEY)
            ?.takeIf { featureGate.current.geo }
            ?.let { locationDao.getPlace(it) }
        if (place != null) {
            locationDao.insert(createGeofence(place.uid, preferences).copy(task = task.id))
            locationService.updateGeofences(place)
        }
        taskSaver.save(task, null)
        if (insertAlarms && place == null) {
            alarmDao.insert(
                task.getDefaultAlarms(preferences.isDefaultDueTimeEnabled())
                    .filter { featureGate.current.schedules(it) },
            )
        }
    }

    suspend fun createMemo(title: String?): Task =
        create(null, title, parseTitle = false, applyDefaults = false).apply {
            memo = true
            parent = 0
        }

    suspend fun createWithValues(title: String?, stampDueToday: Boolean = true): Task {
        return create(null, title, stampDueToday = stampDueToday)
    }

    suspend fun createWithValues(
        filter: Filter?,
        title: String?,
        parseTitle: Boolean = true,
        applyDefaults: Boolean = true,
    ): Task =
        create(mapFromSerializedString(filter?.valuesForNewTasks), title, parseTitle, applyDefaults)

    /**
     * Create task from the given content values, saving it. This version doesn't need to start with a
     * base task model.
     */
    internal suspend fun create(
        values: Map<String, Any>?,
        title: String?,
        parseTitle: Boolean = true,
        applyDefaults: Boolean = true,
        stampDueToday: Boolean = true,
    ): Task {
        val task = Task(
            title = title?.trim { it <= ' ' },
            creationDate = currentTimeMillis(),
            modificationDate = currentTimeMillis(),
            remoteId = UUIDHelper.newUUID(),
            priority = if (applyDefaults) preferences.defaultPriority() else Task.Priority.LOW,
        )
        val tags = ArrayList<String>()
        values?.entries?.forEach { (key, value) ->
            when (key) {
                Tag.KEY -> tags.add(value as String)
                GoogleTask.KEY, CaldavTask.KEY -> task.putTransitory(key, value)
                Place.KEY -> if (featureGate.current.geo) {
                    putPlaceIfExists(task, value as? String)
                }
                DUE_DATE.name -> value.substitute()?.toLongOrNull()?.let { task.dueDate =
                    createDueDate(Task.URGENCY_SPECIFIC_DAY, it) }
                IMPORTANCE.name -> value.substitute()?.toIntOrNull()?.let { task.priority = it }
                HIDE_UNTIL.name ->
                    value.substitute()?.toLongOrNull()?.let { task.hideUntil = it.startOfDay() }
            }
        }
        if (parseTitle) {
            try {
                parse(
                    tagDataDao,
                    task,
                    tags,
                    featureGate.current,
                    preferences.dateShortcutMorning,
                    preferences.dateShortcutEvening,
                )
            } catch (e: Throwable) {
                Timber.e(e)
            }
        }
        // A date, start date or repeat (explicit or typed in the title) is a schedule.
        val scheduled = task.blocksPlace(featureGate.current.startDate) || !task.recurrence.isNullOrBlank()
        // Default place only when nothing scheduled the task.
        if (
            applyDefaults &&
            featureGate.current.geo &&
            !scheduled &&
            !task.hasTransitory(Place.KEY)
        ) {
            preferences.getStringValue(R.string.p_default_location)
                ?.takeIf { it.isNotBlank() }
                ?.let { uid -> putPlaceIfExists(task, uid) }
        }
        // Schedule wins when both ended up set (filter place + due, or a place list + typed repeat).
        if (scheduled && task.hasTransitory(Place.KEY)) {
            task.clearTransitory(Place.KEY)
        }
        val placeTask = task.hasTransitory(Place.KEY)
        if (applyDefaults && !placeTask) {
            applyDefaultRecurrence(task)
        }
        if (applyDefaults) {
            task.setDefaultReminders(preferences)
        }
        if (
            applyDefaults &&
            stampDueToday &&
            task.dueDate <= 0 &&
            values?.containsKey(DUE_DATE.name) != true &&
            !placeTask
        ) {
            // No setting: a new task is due today unless it has a place or says otherwise.
            task.dueDate = createDueDate(Task.URGENCY_TODAY, 0)
        }
        if (
            applyDefaults &&
            featureGate.current.startDate &&
            values?.containsKey(HIDE_UNTIL.name) != true &&
            !placeTask
        ) {
            task.hideUntil = task.createHideUntil(
                    preferences.getIntegerFromString(R.string.p_default_hideUntil_key, HIDE_UNTIL_NONE),
                    0
            )
        }
        task.putTransitory(Tag.KEY, tags)
        return task
    }

    /**
     * Due/start/repeat win over a transitory place. Restores default recurrence and hideUntil
     * that create() skipped while the task looked like a place task.
     */
    fun clearPlaceForSchedule(task: Task, applyDefaults: Boolean = true) {
        if (!task.hasTransitory(Place.KEY) || !task.blocksPlace(featureGate.current.startDate)) {
            return
        }
        task.clearTransitory(Place.KEY)
        if (!applyDefaults) return
        applyDefaultRecurrence(task)
        if (featureGate.current.startDate && task.hideUntil <= 0) {
            task.hideUntil = task.createHideUntil(
                preferences.getIntegerFromString(R.string.p_default_hideUntil_key, HIDE_UNTIL_NONE),
                0,
            )
        }
    }

    private fun applyDefaultRecurrence(task: Task) {
        if (!task.recurrence.isNullOrBlank()) return
        preferences.getStringValue(R.string.p_default_recurrence)
            ?.takeIf { it.isNotBlank() }
            ?.let {
                task.recurrence = it
                task.repeatFrom = Task.RepeatFrom.DUE_DATE
            }
    }

    private suspend fun putPlaceIfExists(task: Task, uid: String?) {
        val placeUid = uid?.takeIf { it.isNotBlank() } ?: return
        if (locationDao.getPlace(placeUid) != null) {
            task.putTransitory(Place.KEY, placeUid)
        }
    }

    companion object {
        private fun Any?.substitute(): String? =
            (this as? String)?.let { PermaSql.replacePlaceholdersForNewTask(it) }
    }
}
