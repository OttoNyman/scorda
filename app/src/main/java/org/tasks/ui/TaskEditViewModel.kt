package org.tasks.ui

import android.content.Context
import android.net.Uri
import androidx.annotation.MainThread
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todoroo.astrid.activity.BeastModePreferences
import com.todoroo.astrid.activity.TaskEditFragment
import com.todoroo.astrid.alarms.AlarmService
import com.todoroo.astrid.service.TaskCreator
import org.tasks.data.dao.TaskDao
import org.tasks.data.TaskSaver
import com.todoroo.astrid.files.FilesControlSet
import com.todoroo.astrid.repeats.RepeatControlSet
import org.tasks.resources.getString
import org.tasks.service.TaskCompleter
import org.tasks.data.getDefaultAlarms
import org.tasks.service.TaskDeleter
import org.tasks.data.TaskMover
import com.todoroo.astrid.tags.TagsControlSet
import com.todoroo.astrid.ui.ReminderControlSet
import com.todoroo.astrid.ui.StartDateControlSet
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.tasks.R
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.no_title
import org.tasks.Strings
import org.tasks.analytics.Firebase
import org.tasks.data.Location
import org.tasks.checklist.Checklist
import org.tasks.data.createDueDate
import org.tasks.data.dao.AlarmDao
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.GoogleTaskDao
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.SeriesDao
import org.tasks.data.dao.TagDao
import org.tasks.data.dao.TagDataDao
import org.tasks.data.dao.TaskAttachmentDao
import org.tasks.data.dao.UserActivityDao
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Alarm.Companion.TYPE_REL_END
import org.tasks.data.entity.Alarm.Companion.TYPE_REL_START
import org.tasks.data.entity.Attachment
import org.tasks.data.entity.Series
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.SYNC_ALARMS
import org.tasks.data.entity.SYNC_LOCATION
import org.tasks.data.entity.SYNC_TAGS
import org.tasks.data.entity.TagData
import org.tasks.data.entity.Task
import org.tasks.data.entity.Task.Companion.hasDueTime
import org.tasks.data.entity.TaskAttachment
import org.tasks.data.entity.UserActivity
import org.tasks.data.getLocation
import org.tasks.data.setPicture
import org.tasks.date.DateTimeUtils.toDateTime
import org.tasks.files.FileHelper
import org.tasks.filters.CaldavFilter
import org.tasks.filters.Filter
import org.tasks.kmp.org.tasks.taskedit.TaskEditViewState
import org.tasks.location.LocationService
import org.tasks.memos.MemoConverter
import org.tasks.memos.ToMemoResult
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.preferences.Preferences
import org.tasks.repeats.Frequency
import org.tasks.reminders.acceptsDefault
import org.tasks.reminders.withAdvance
import org.tasks.repeats.Recur
import org.tasks.repeats.SeriesEditScope
import org.tasks.repeats.SeriesScheduler
import org.tasks.repeats.anchoredToDueDate
import org.tasks.time.DateTime
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.time.startOfDay
import javax.inject.Inject

@HiltViewModel
class TaskEditViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val taskDao: TaskDao,
    private val taskSaver: TaskSaver,
    private val taskDeleter: TaskDeleter,
    private val taskMover: TaskMover,
    private val locationDao: LocationDao,
    private val locationService: LocationService,
    private val tagDao: TagDao,
    private val tagDataDao: TagDataDao,
    private val preferences: Preferences,
    private val googleTaskDao: GoogleTaskDao,
    private val caldavDao: CaldavDao,
    private val taskCompleter: TaskCompleter,
    private val alarmService: AlarmService,
    private val taskListEvents: TaskListEventBus,
    private val firebase: Firebase? = null,
    private val userActivityDao: UserActivityDao,
    private val alarmDao: AlarmDao,
    private val taskAttachmentDao: TaskAttachmentDao,
    private val defaultFilterProvider: DefaultFilterProvider,
    private val featureGate: org.tasks.constructor.FeatureGate,
    private val seriesDao: SeriesDao,
    private val seriesScheduler: SeriesScheduler,
    private val taskCreator: TaskCreator,
    private val memoConverter: MemoConverter,
    private val taskPinner: org.tasks.pins.TaskPinner? = null,
) : ViewModel() {
    private val resources = context.resources
    private var cleared = false

    private var task: Task = run {
        val argTask = savedStateHandle.get<Task>(TaskEditFragment.EXTRA_TASK)
            ?: throw IllegalArgumentException("task is null")

        val wasRecreated = savedStateHandle.contains(EXTRA_WAS_INIT)
            .also { savedStateHandle[EXTRA_WAS_INIT] = true }
        if (wasRecreated) {
            runBlocking {
                if (argTask.isNew) {
                    taskDao.fetch(argTask.uuid)
                } else {
                    taskDao.fetch(argTask.id)
                }
            } ?: argTask
        } else {
            argTask
        }
    }.apply { notes = notes?.stripCarriageReturns() } // copying here broke tests 🙄

    /** The schedule of an occurrence: the editor shows and edits its rule, never the row's. */
    private val series: Series? = task.series?.let { runBlocking { seriesDao.getByUuid(it) } }

    /** Occurrence of a running series: it can't be deleted (spec point 12). */
    val isLiveOccurrence: Boolean = series?.finished == 0L

    private val _originalState = MutableStateFlow(
        TaskEditViewState(
            task = series?.let { task.copy(recurrence = it.recurrence, repeatFrom = it.repeatFrom) } ?: task,
            showBeastModeHint = false,
            showComments = showsComments(task.memo),
            showKeyboard = task.isNew && task.title.isNullOrBlank(),
            backButtonSavesTask = preferences.backButtonSavesTask(),
            isReadOnly = task.readOnly,
            linkify = preferences.linkify,
            alwaysDisplayFullDate = preferences.alwaysDisplayFullDate,
            showEditScreenWithoutUnlock = preferences.showEditScreenWithoutUnlock,
            calendar = null,
            displayOrder = displayOrder(featureGate.current, task.memo, !task.notes.isNullOrBlank()),
            alarms = if (task.isNew && !task.memo) {
                task.getDefaultAlarms(runBlocking { preferences.isDefaultDueTimeEnabled() })
                    .filter { featureGate.current.schedules(it) }
            } else {
                emptyList()
            }.toPersistentSet(),
            location = null,
            tags = persistentSetOf(),
            list = CaldavFilter(calendar = CaldavCalendar(), account = CaldavAccount()),
            pinSeries = series?.let { runBlocking { taskPinner?.isSeriesPinned(it.uuid) } } == true,
        )
    )
    val originalState: StateFlow<TaskEditViewState> = _originalState

    private val _viewState = MutableStateFlow(originalState.value)
    val viewState: StateFlow<TaskEditViewState> = _viewState

    init {
        if (!preferences.shownBeastModeHint) {
            firebase?.logEvent(
                R.string.event_banner,
                R.string.param_type to "beast_mode",
                R.string.param_action to "shown",
            )
        }
    }

    val dueDate = MutableStateFlow(task.dueDate)

    fun setDueDate(value: Long) {
        val hadDueDate = dueDate.value > 0
        val hadDueTime = hasDueTime(dueDate.value)
        dueDate.value = when {
            value == 0L -> 0
            hasDueTime(value) -> createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, value)
            else -> createDueDate(Task.URGENCY_SPECIFIC_DAY, value)
        }
        val hasDueTimeNow = hasDueTime(dueDate.value)
        val addedDueDate = !hadDueDate && dueDate.value > 0
        val addedDueTime = hadDueDate && !hadDueTime && hasDueTimeNow
        val isDefaultDueTimeEnabled = runBlocking { preferences.isDefaultDueTimeEnabled() }
        val shouldAddReminders = when {
            addedDueDate -> hasDueTimeNow || isDefaultDueTimeEnabled
            addedDueTime -> !isDefaultDueTimeEnabled
            else -> false
        }
        if (shouldAddReminders) {
            runBlocking { preferences.defaultAlarms() }
                .filter { it.type == TYPE_REL_END && featureGate.current.schedules(it) }
                .filter { _viewState.value.alarms.acceptsDefault(it) }
                .forEach { alarm ->
                    _viewState.update { state ->
                        state.copy(alarms = state.alarms.plusAlarm(alarm.copy(task = task.id)))
                    }
                }
        }
    }

    val startDate = MutableStateFlow(task.hideUntil)

    fun setStartDate(value: Long) {
        val hadStartDate = startDate.value > 0
        val hadStartTime = hasDueTime(startDate.value)
        startDate.value = when {
            value == 0L -> 0
            hasDueTime(value) ->
                value.toDateTime().withSecondOfMinute(1).withMillisOfSecond(0).millis
            else -> value.startOfDay()
        }
        val hasStartTimeNow = hasDueTime(startDate.value)
        val addedStartDate = !hadStartDate && startDate.value > 0
        val addedStartTime = hadStartDate && !hadStartTime && hasStartTimeNow
        val isDefaultDueTimeEnabled = runBlocking { preferences.isDefaultDueTimeEnabled() }
        val shouldAddReminders = when {
            addedStartDate -> hasStartTimeNow || isDefaultDueTimeEnabled
            addedStartTime -> !isDefaultDueTimeEnabled
            else -> false
        }
        if (shouldAddReminders) {
            runBlocking { preferences.defaultAlarms() }
                .filter { it.type == TYPE_REL_START }
                .forEach { alarm ->
                    _viewState.update { state ->
                        state.copy(alarms = state.alarms.plusAlarm(alarm.copy(task = task.id)))
                    }
                }
        }
    }

    fun hasChanges(): Boolean = hasChanges(_viewState.value)

    /**
     * Saving this needs the user's [SeriesEditScope] (spec point 10): an occurrence of a running
     * series with changes besides its rule. A changed or cleared rule always goes to the schedule
     * and completing never asks.
     */
    fun asksSeriesScope(): Boolean {
        if (!featureGate.current.repeat) {
            return false
        }
        val series = series?.takeIf { it.finished == 0L } ?: return false
        val state = _viewState.value
        return !state.isReadOnly &&
                state.isCompleted == task.isCompleted &&
                !ruleChanged(series, state.task) &&
                // the same rule written another way is no change
                hasChanges(state.copy(task = state.task.copy(recurrence = series.recurrence)))
    }

    private fun ruleChanged(series: Series, edited: Task): Boolean {
        val rule = edited.recurrence?.takeIf { it.isNotBlank() } ?: return true
        return edited.repeatFrom != series.repeatFrom || rule != series.recurrence && try {
            Recur.parse(rule) != Recur.parse(series.recurrence)
        } catch (e: IllegalArgumentException) {
            true
        }
    }

    private fun hasChanges(viewState: TaskEditViewState): Boolean {
        return originalState.value != viewState ||
                (viewState.isNew && viewState.task.title?.isNotBlank() == true) || // text shared to tasks
                task.dueDate != dueDate.value ||
                task.hideUntil != startDate.value
    }

    /**
     * [scope] is the user's answer when [asksSeriesScope]; without one an occurrence edit stays
     * on the occurrence. After a schedule edit the row may be gone: the editor must close.
     */
    @MainThread
    suspend fun save(scope: SeriesEditScope? = null): Boolean = withContext(NonCancellable) {
        if (cleared) {
            return@withContext false
        }
        // The title is required: a new task without one is never created, back or save alike.
        if (viewState.value.isNew && viewState.value.task.title.isNullOrBlank()) {
            discard()
            return@withContext false
        }
        if (!hasChanges() || viewState.value.isReadOnly) {
            discard()
            return@withContext false
        }
        if (!task.isNew && taskDeleter.isDeleted(task.id)) {
            discard()
            return@withContext false
        }
        clear()
        val viewState = _viewState.value
        val isNew = viewState.isNew
        val original = if (isNew) null else task.copy()
        val rule = viewState.task.recurrence?.takeIf { it.isNotBlank() }
        val series = series
        // completing never goes to the schedule
        val toSchedule = series != null && rule != null && viewState.isCompleted == task.isCompleted &&
                (ruleChanged(series, viewState.task) || scope == SeriesEditScope.SCHEDULE)
        // An emptied title keeps the old one: the title is required.
        task.title = viewState.task.title?.takeIf { it.isNotBlank() }
            ?: task.title?.takeIf { it.isNotBlank() }
            ?: getString(Res.string.no_title)
        task.priority = viewState.task.priority
        task.pinned = viewState.task.pinned
        task.notes = Checklist.withoutBlankItems(viewState.task.notes)
        val savingMemo = viewState.task.memo
        if (savingMemo) {
            applyMemoSaveConstraints(task)
            dueDate.value = 0
            startDate.value = 0
        } else {
            task.dueDate = dueDate.value
            task.hideUntil = startDate.value
            // an occurrence never carries a rule: it lives in the series
            if (task.series == null) {
                task.recurrence = viewState.task.recurrence
                task.repeatFrom = Task.RepeatFrom.DUE_DATE
            } else {
                task.recurrence = null
            }
        }

        if (isNew) {
            taskDao.createNew(task)
        }
        val selectedLocation = _viewState.value.location
        if (!savingMemo && ((isNew && selectedLocation != null) || originalState.value.location != selectedLocation)) {
            originalState.value.location?.let { location ->
                if (location.geofence.id > 0) {
                    locationDao.delete(location.geofence)
                    locationService.updateGeofences(location.place)
                }
            }
            selectedLocation?.let { location ->
                val place = location.place
                locationDao.insert(
                    location.geofence.copy(
                        task = task.id,
                        place = place.uid,
                    )
                )
                locationService.updateGeofences(place)
            }
            task.putTransitory(SYNC_LOCATION, true)
        }
        val selectedTags = _viewState.value.tags
        if ((isNew && selectedTags.isNotEmpty()) || originalState.value.tags.toHashSet() != selectedTags.toHashSet()) {
            tagDao.applyTags(task, selectedTags)
            task.putTransitory(SYNC_TAGS, true)
        }

        if (!task.hasStartDate()) {
            _viewState.update { state ->
                state.copy(
                    alarms = state.alarms.filterNot { it.type == TYPE_REL_START }.toPersistentSet()
                )
            }
        }
        if (!task.hasDueDate()) {
            _viewState.update { state ->
                state.copy(
                    alarms = state.alarms.filterNot { it.type == TYPE_REL_END }.toPersistentSet()
                )
            }
        }

        if (
            !savingMemo &&
            ((isNew && _viewState.value.alarms.isNotEmpty()) ||
                originalState.value.alarms != _viewState.value.alarms)
        ) {
            alarmService.synchronizeAlarms(task.id, _viewState.value.alarms.toMutableSet())
            task.putTransitory(SYNC_ALARMS, true)
        }

        taskSaver.save(task, original)
        val selectedList = _viewState.value.list
        if (!savingMemo && (isNew || originalState.value.list != selectedList)) {
            task.parent = 0
            taskMover.move(listOf(task.id), selectedList)
        }

        if (originalState.value.attachments != _viewState.value.attachments) {
            originalState.value.attachments
                .minus(_viewState.value.attachments)
                .map { it.remoteId }
                .let { taskAttachmentDao.delete(task.id, it) }
            _viewState.value.attachments
                .minus(originalState.value.attachments)
                .map {
                    Attachment(
                        task = task.id,
                        fileId = it.id!!,
                        attachmentUid = it.remoteId,
                    )
                }
                .let { taskAttachmentDao.insert(it) }
        }

        if (!savingMemo && series != null && rule == null) {
            // "Don't repeat" on an occurrence: the series ends, this row stays a plain task
            seriesScheduler.detach(task.id)
            task.series = null
            task.scheduled = 0
        } else if (!savingMemo && toSchedule) {
            // after the subtasks: the new template is captured from the saved row
            seriesScheduler.editSchedule(
                taskId = task.id,
                newRecurrence = rule,
                previousDueDate = original!!.dueDate,
                newRepeatFrom = Task.RepeatFrom.DUE_DATE,
            )
        }

        if (!savingMemo && task.isCompleted != _viewState.value.isCompleted) {
            taskCompleter.setComplete(task, _viewState.value.isCompleted)
            if (_viewState.value.isCompleted) {
                firebase?.completeTask("edit_screen_v2")
            }
        }

        if (isNew) {
            taskListEvents.emit(TaskListEvent.TaskCreated(task.uuid))
        }
        true
    }

    suspend fun delete() {
        taskDeleter.markDeleted(task)
        discard()
    }

    suspend fun discard() {
        if (_viewState.value.isNew) {
            (originalState.value.attachments + _viewState.value.attachments)
                .onEach { attachment -> FileHelper.delete(context, attachment.uri.toUri()) }
                .let { taskAttachmentDao.delete(it.toList()) }
        }
        clear()
    }

    @MainThread
    fun clear() {
        if (cleared) {
            return
        }
        cleared = true
    }

    override fun onCleared() {
        if (!cleared) {
            runBlocking {
                save()
            }
        }
    }

    fun removeAlarm(alarm: Alarm) {
        _viewState.update { state ->
            state.copy(alarms = state.alarms.minus(alarm).toPersistentSet())
        }
    }

    /** The "In advance" row: replaces the one early ring, null removes it. */
    fun setAdvance(offset: Long?) {
        _viewState.update { state ->
            state.copy(alarms = state.alarms.withAdvance(offset).toPersistentSet())
        }
    }

    fun addAlarm(alarm: Alarm) {
        _viewState.update { state ->
            state.copy(alarms = state.alarms.plusAlarm(alarm))
        }
    }

    fun addComment(message: String?, picture: Uri?) = viewModelScope.launch(NonCancellable) {
        val userActivity = UserActivity()
        if (picture != null) {
            val output = FileHelper.copyToUri(context, preferences.attachmentsDirectory!!, picture)
            userActivity.setPicture(output)
        }
        userActivity.message = message
        userActivity.targetId = task.uuid
        userActivity.created = currentTimeMillis()
        userActivityDao.createNew(userActivity)
    }

    fun hideBeastModeHint(click: Boolean) {
        _viewState.update {
            it.copy(showBeastModeHint = false)
        }
        preferences.shownBeastModeHint = true
        firebase?.logEvent(
            R.string.event_banner,
            R.string.param_type to "beast_mode",
            R.string.param_action to if (click) "positive" else "negative",
            R.string.param_label to resources.getResourceEntryName(
                if (click) R.string.TLA_menu_settings else R.string.dismiss
            ),
        )
    }

    fun setPriority(priority: Int) {
        _viewState.update { state -> state.copy(task = state.task.copy(priority = priority)) }
    }

    fun setTitle(title: String) {
        _viewState.update { state -> state.copy(task = state.task.copy(title = title)) }
    }

    fun setRecurrence(recurrence: String?) {
        _viewState.update { state -> state.copy(task = state.task.copy(recurrence = recurrence)) }
        if (recurrence.isNullOrBlank() || hasDueTime(dueDate.value)) {
            return
        }
        // A repeat needs a due date, and through setDueDate so default reminders come with it
        // (the schedule copies them to every occurrence). A sub-day rule needs a time too: the
        // first occurrence is one period from now.
        val period = subDayPeriod(recurrence)
        when {
            period != null ->
                setDueDate(createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, currentTimeMillis() + period))
            dueDate.value == 0L ->
                setDueDate(createDueDate(Task.URGENCY_SPECIFIC_DAY, currentTimeMillis()))
        }
    }

    private fun subDayPeriod(rule: String): Long? {
        val recur = try {
            Recur.parse(rule)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val unit = when (recur.frequency) {
            Frequency.SECONDLY -> 1_000L
            Frequency.MINUTELY -> 60_000L
            Frequency.HOURLY -> 3_600_000L
            else -> return null
        }
        return unit * (recur.interval ?: 1).coerceAtLeast(1)
    }

    fun setDescription(description: String) {
        _viewState.update { state -> state.copy(task = state.task.copy(notes = description)) }
    }

    /** The description field without the checklist block that rides at the end of the notes. */
    fun setDescriptionText(text: String) {
        _viewState.update { state ->
            val items = Checklist.parse(state.task.notes).items
            state.copy(task = state.task.copy(notes = Checklist.join(text, items)))
        }
    }

    fun setChecklist(items: List<Checklist.Item>) {
        _viewState.update { state ->
            val text = Checklist.parse(state.task.notes).text
            state.copy(task = state.task.copy(notes = Checklist.join(text, items)))
        }
    }

    val memosEnabled: Boolean
        get() = featureGate.current.memos

    val pinningEnabled: Boolean
        get() = featureGate.current.pinning

    /** For «О задаче»: when the repeating schedule of this occurrence began. */
    val seriesCreated: Long?
        get() = series?.created

    /** Pinning an occurrence of a running series asks: this one or the whole series. */
    val pinAsksSeries: Boolean
        get() = series?.finished == 0L

    /**
     * A new task is pinned with its save. An existing one is pinned right away, both in the editor's
     * original and current state: it is not an edit to confirm and never asks the series scope.
     */
    fun setPinned(pinned: Boolean, wholeSeries: Boolean = false) {
        val value = if (pinned) currentTimeMillis() else 0L
        val pinSeries = pinned && wholeSeries
        if (task.isNew) {
            _viewState.update { it.copy(task = it.task.copy(pinned = value)) }
            return
        }
        task.pinned = value
        val apply: (TaskEditViewState) -> TaskEditViewState = {
            it.copy(task = it.task.copy(pinned = value), pinSeries = pinSeries)
        }
        _originalState.update(apply)
        _viewState.update(apply)
        viewModelScope.launch {
            if (pinned) {
                taskPinner?.pin(listOf(task.id), wholeSeries)
            } else {
                taskPinner?.unpin(listOf(task.id))
            }
        }
    }

    private fun showsComments(memo: Boolean): Boolean =
        !memo && preferences.getBoolean(R.string.p_show_task_edit_comments, false)

    suspend fun switchDraftType(memo: Boolean, filter: Filter) {
        val state = _viewState.value
        if (!state.isNew || state.task.memo == memo) {
            return
        }
        val title = state.task.title
        val notes = state.task.notes
        val features = featureGate.current
        val fresh = if (memo) {
            taskCreator.createMemo(title)
        } else {
            taskCreator.createWithValues(filter, title)
        }
        fresh.title = title
        fresh.notes = notes
        task = fresh
        savedStateHandle[TaskEditFragment.EXTRA_TASK] = fresh
        dueDate.value = fresh.dueDate
        startDate.value = fresh.hideUntil
        val order = displayOrder(features, memo, !fresh.notes.isNullOrBlank())
        val alarms = if (memo) {
            persistentSetOf()
        } else {
            fresh.getDefaultAlarms(preferences.isDefaultDueTimeEnabled())
                .filter { features.schedules(it) }
                .toPersistentSet()
        }
        val list = if (memo) {
            state.list
        } else {
            defaultFilterProvider.getList(fresh)
        }
        val tags = if (memo) persistentSetOf() else tagDataDao.getTags(fresh).toPersistentSet()
        val location = if (memo) null else locationDao.getLocation(fresh, preferences)
        val cleared = TaskEditViewState(
            task = fresh,
            showBeastModeHint = state.showBeastModeHint,
            showComments = showsComments(memo),
            showKeyboard = state.showKeyboard,
            backButtonSavesTask = state.backButtonSavesTask,
            isReadOnly = state.isReadOnly,
            linkify = state.linkify,
            alwaysDisplayFullDate = state.alwaysDisplayFullDate,
            showEditScreenWithoutUnlock = state.showEditScreenWithoutUnlock,
            calendar = state.calendar,
            displayOrder = order,
            alarms = alarms,
            location = location,
            tags = tags,
            list = list,
            attachments = persistentSetOf(),
        )
        _originalState.value = cleared
        _viewState.value = cleared
    }

    enum class MemoConvertBlock { NONE, CHILDREN, SERIES }

    suspend fun memoConvertBlock(): MemoConvertBlock {
        val fresh = taskDao.fetch(task.id) ?: task
        if (memoConverter.canConvertToMemo(fresh)) return MemoConvertBlock.NONE
        return if (fresh.series != null) MemoConvertBlock.SERIES else MemoConvertBlock.CHILDREN
    }

    suspend fun hasRemoteListForConvert(): Boolean = memoConverter.hasRemoteListRow(task.id)

    suspend fun convertToMemo(): MemoConvertBlock {
        val blocked = memoConvertBlock()
        if (blocked != MemoConvertBlock.NONE) return blocked
        persistConvertibleFields()
        return when (memoConverter.toMemo(task.id)) {
            ToMemoResult.SUCCESS -> {
                reloadAfterTypeChange()
                MemoConvertBlock.NONE
            }
            ToMemoResult.BLOCKED_CHILDREN -> MemoConvertBlock.CHILDREN
            ToMemoResult.BLOCKED_SERIES -> MemoConvertBlock.SERIES
        }
    }

    suspend fun convertToTask() {
        persistConvertibleFields()
        memoConverter.toTask(task.id)
        reloadAfterTypeChange()
    }

    private suspend fun persistConvertibleFields() {
        val state = _viewState.value
        if (state.isReadOnly) return
        val original = task.copy()
        task.title = state.task.title?.takeIf { it.isNotBlank() }
            ?: task.title?.takeIf { it.isNotBlank() }
            ?: getString(Res.string.no_title)
        task.priority = state.task.priority
        task.notes = state.task.notes
        taskSaver.save(task, original)

        val selectedTags = state.tags
        if (originalState.value.tags.toHashSet() != selectedTags.toHashSet()) {
            tagDao.applyTags(task, selectedTags)
        }

        if (originalState.value.attachments != state.attachments) {
            originalState.value.attachments
                .minus(state.attachments)
                .map { it.remoteId }
                .let { taskAttachmentDao.delete(task.id, it) }
            state.attachments
                .minus(originalState.value.attachments)
                .map {
                    Attachment(
                        task = task.id,
                        fileId = it.id!!,
                        attachmentUid = it.remoteId,
                    )
                }
                .let { taskAttachmentDao.insert(it) }
        }
    }

    private suspend fun reloadAfterTypeChange() {
        val fresh = taskDao.fetch(task.id) ?: return
        task = fresh
        savedStateHandle[TaskEditFragment.EXTRA_TASK] = fresh
        dueDate.value = fresh.dueDate
        startDate.value = fresh.hideUntil
        val features = featureGate.current
        val list = defaultFilterProvider.getList(fresh)
        val tags = tagDataDao.getTags(fresh).toPersistentSet()
        val alarms = alarmDao.getAlarms(fresh.id).toPersistentSet()
        val location = locationDao.getLocation(fresh, preferences)
        val attachments = taskAttachmentDao
            .getAttachments(fresh.id)
            .filter { FileHelper.fileExists(context, it.uri.toUri()) }
            .toPersistentSet()
        val cleared = TaskEditViewState(
            task = fresh,
            showBeastModeHint = _viewState.value.showBeastModeHint,
            showComments = showsComments(fresh.memo),
            showKeyboard = false,
            backButtonSavesTask = _viewState.value.backButtonSavesTask,
            isReadOnly = fresh.readOnly,
            linkify = _viewState.value.linkify,
            alwaysDisplayFullDate = _viewState.value.alwaysDisplayFullDate,
            showEditScreenWithoutUnlock = _viewState.value.showEditScreenWithoutUnlock,
            calendar = _viewState.value.calendar,
            displayOrder = displayOrder(features, fresh.memo, !fresh.notes.isNullOrBlank()),
            alarms = alarms,
            location = location,
            tags = tags,
            list = list,
            attachments = attachments,
        )
        _originalState.value = cleared
        _viewState.value = cleared
    }

    fun setList(list: CaldavFilter) {
        _viewState.update { it.copy(list = list) }
    }

    fun setTags(tags: Set<TagData>) {
        _viewState.update { it.copy(tags = tags.toPersistentSet()) }
    }

    fun setLocation(location: Location?) {
        _viewState.update { it.copy(location = location) }
    }

    fun setAttachments(attachments: Set<TaskAttachment>) {
        _viewState.update { it.copy(attachments = attachments.toPersistentSet()) }
    }


    fun setComplete(completed: Boolean) {
        _viewState.update { state ->
            state.copy(
                task = state.task.copy(
                    completionDate = when {
                        !completed -> 0
                        task.isCompleted -> task.completionDate
                        else -> currentTimeMillis()
                    }
                )
            )
        }
    }

    fun setRepeatFrom(repeatFrom: @Task.RepeatFrom Int) {
        _viewState.update { state ->
            state.copy(task = state.task.copy(repeatFrom = repeatFrom))
        }
    }

    fun onDueDateChanged() {
        if (series != null) {
            // a moved date is a postpone, or the schedule rebases the rule itself
            return
        }
        _viewState.value.task.recurrence?.takeIf { it.isNotBlank() }?.let { recurrence ->
            val anchored = recurrence.anchoredToDueDate(dueDate.value)
            if (anchored != recurrence) {
                setRecurrence(anchored)
            }
        }
    }

    init {
        runBlocking {
            val attachments = async {
                taskAttachmentDao
                    .getAttachments(task.id)
                    .filter { FileHelper.fileExists(context, it.uri.toUri()) }
                    .toPersistentSet()
            }
            val alarms = async {
                if (task.isNew) {
                    originalState.value.alarms
                } else {
                    alarmDao.getAlarms(task.id)
                }.toPersistentSet()
            }
            val list = async { defaultFilterProvider.getList(task) }
            val location = async { locationDao.getLocation(task, preferences) }
            val tags = async { tagDataDao.getTags(task).toPersistentSet() }
            _originalState.update {
                it.copy(
                    attachments = attachments.await(),
                    alarms = alarms.await(),
                    list = list.await(),
                    location = location.await(),
                    tags = tags.await(),
                )
            }
            _viewState.update {
                it.copy(
                    attachments = originalState.value.attachments,
                    alarms = originalState.value.alarms,
                    list = originalState.value.list,
                    location = originalState.value.location,
                    tags = originalState.value.tags,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_WAS_INIT = "extra_was_init"

        // one spark tasks for windows adds these
        fun String?.stripCarriageReturns(): String? = this?.replace("\\r\\n?".toRegex(), "\n")

        private fun ImmutableSet<Alarm>.plusAlarm(alarm: Alarm): ImmutableSet<Alarm> =
            if (any { it.same(alarm) }) this else this.plus(alarm).toPersistentSet()

        val TAG_TITLE = R.string.TEA_ctrl_title
        val TAG_DESCRIPTION = R.string.TEA_ctrl_notes_pref
        val TAG_CREATION = R.string.TEA_ctrl_creation_date
        val TAG_LIST = R.string.TEA_ctrl_google_task_list
        val TAG_PRIORITY = R.string.TEA_ctrl_importance_pref
        val TAG_DUE_DATE = R.string.TEA_ctrl_when_pref
        val TAG_ADVANCE = R.string.TEA_ctrl_advance_pref
        val TAG_CHECKLIST = R.string.checklist

        fun displayOrder(
            features: org.tasks.constructor.Constructor,
            memo: Boolean,
            /** Notes off: a description the task already has stays visible and editable. */
            hasNotes: Boolean = false,
        ) = if (memo) {
            listOf(
                TAG_TITLE to true,
                TAG_DESCRIPTION to true,
                TAG_CHECKLIST to features.checklist,
                com.todoroo.astrid.tags.TagsControlSet.TAG to features.tags,
                TAG_PRIORITY to features.priority,
                com.todoroo.astrid.files.FilesControlSet.TAG to features.files,
            )
        } else {
            listOf(
                TAG_TITLE to true,
                TAG_DUE_DATE to true,
                TAG_ADVANCE to features.advance,
                com.todoroo.astrid.repeats.RepeatControlSet.TAG to features.repeat,
                TAG_DESCRIPTION to (features.notes || hasNotes),
                TAG_CHECKLIST to features.checklist,
                TAG_LIST to features.lists,
                com.todoroo.astrid.tags.TagsControlSet.TAG to features.tags,
                TAG_PRIORITY to features.priority,
                com.todoroo.astrid.ui.StartDateControlSet.TAG to features.startDate,
                com.todoroo.astrid.ui.ReminderControlSet.TAG to features.extraReminders,
                org.tasks.ui.LocationControlSet.TAG to features.geo,
                com.todoroo.astrid.files.FilesControlSet.TAG to features.files,
            )
        }.filter { it.second }.map { it.first }.toPersistentList()

        private fun applyMemoSaveConstraints(task: Task) {
            task.dueDate = 0
            task.hideUntil = 0
            task.recurrence = null
            task.series = null
            task.scheduled = 0
            task.parent = 0
            task.completionDate = 0
        }

        val TASK_EDIT_CONTROL_SET_FRAGMENTS = intArrayOf(
            TAG_TITLE,
            TAG_DUE_DATE,
            TAG_ADVANCE,
            TAG_DESCRIPTION,
            TAG_PRIORITY,
            StartDateControlSet.TAG,
            ReminderControlSet.TAG,
            LocationControlSet.TAG,
            FilesControlSet.TAG,
            TagsControlSet.TAG,
            RepeatControlSet.TAG,
            TAG_CREATION,
            TAG_LIST,
            TAG_CHECKLIST
        )
    }
}
