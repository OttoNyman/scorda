package org.tasks.data

import co.touchlab.kermit.Logger
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.constructor.FeatureGate
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_GOOGLE_TASKS
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_LOCAL
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_MICROSOFT
import org.tasks.data.entity.SUPPRESS_SYNC
import org.tasks.data.entity.SYNC_ALARMS
import org.tasks.data.entity.SYNC_LOCATION
import org.tasks.data.entity.SYNC_TAGS
import org.tasks.data.entity.SyncTrait
import org.tasks.data.entity.Task
import org.tasks.filters.Filter
import org.tasks.jobs.BackgroundWork
import org.tasks.location.LocationService
import org.tasks.notifications.CancelReason
import org.tasks.notifications.Notifier
import org.tasks.preferences.QueryPreferences
import org.tasks.time.DateTimeUtils2.currentTimeMillis

class TaskSaver(
    private val taskDao: TaskDao,
    private val refreshBroadcaster: RefreshBroadcaster,
    private val notifier: Notifier,
    private val locationService: LocationService,
    private val backgroundWork: BackgroundWork,
    private val caldavDao: CaldavDao,
    private val locationDao: LocationDao,
    private val featureGate: FeatureGate = FeatureGate(),
) {
    suspend fun save(
        task: Task,
        original: Task?,
        dirty: Boolean = true,
        preserveHierarchy: Boolean = false,
    ) {
        val normalized = enforcePlaceOrSchedule(task, original)
        val changes = if (preserveHierarchy && original != null) {
            normalized.copy(parent = original.parent, order = original.order)
        } else {
            normalized
        }
        val markDirty = dirty && needsSync(changes, original)
        if (taskDao.update(
                changes,
                original,
                updateTimestamp = dirty,
                markDirty = markDirty,
                preserveHierarchy = preserveHierarchy,
            )
        ) {
            if (changes !== task) {
                task.modificationDate = changes.modificationDate
                task.parent = changes.parent
                task.order = changes.order
                task.reminderDismissed = changes.reminderDismissed
                task.series = changes.series
                task.scheduled = changes.scheduled
                task.recurrence = changes.recurrence
            }
            Logger.d("TaskSaver") { "Saved $changes" }
            afterSave(changes, original)
        }
    }

    /**
     * Place XOR schedule, date wins when this save sets/changes the schedule (including a new
     * task with a due date). An old dual whose due/start did not change is left alone.
     * Place wins only upstream (editor / ApiWriter clear due before save).
     * Holds with Places off too: a hidden place must not outlive a date set while it was hidden.
     */
    private suspend fun enforcePlaceOrSchedule(task: Task, original: Task?): Task {
        if (task.memo) return task
        val startEnabled = featureGate.current.startDate
        if (!task.blocksPlace(startEnabled)) return task
        val scheduleChanged = original == null ||
            task.dueDate != original.dueDate ||
            (startEnabled && task.hideUntil != original.hideUntil)
        if (!scheduleChanged) return task
        val geofences = locationDao.getGeofencesForTask(task.id)
        if (geofences.isEmpty()) return task
        geofences.forEach { geofence ->
            locationDao.delete(geofence)
            geofence.place?.let { uid -> locationService.updateGeofences(uid) }
        }
        task.putTransitory(SYNC_LOCATION, true)
        return task
    }

    private suspend fun needsSync(task: Task, original: Task?): Boolean {
        if (task.checkTransitory(SUPPRESS_SYNC)) {
            return false
        }
        val accountType = caldavDao.getAccountType(task.id) ?: return false
        val traits = CaldavAccount.syncTraits(accountType)
        val isMicrosoftSubtask = accountType == TYPE_MICROSOFT && task.parent > 0L
        val transitoryChanged = !isMicrosoftSubtask &&
                ((SyncTrait.TAGS in traits && task.checkTransitory(SYNC_TAGS)) ||
                        (SyncTrait.ALARMS in traits && task.checkTransitory(SYNC_ALARMS)) ||
                        (SyncTrait.LOCATION in traits && task.checkTransitory(SYNC_LOCATION)))
        if (transitoryChanged) {
            return true
        }
        if (original != null && SyncTrait.START_DATE in traits && task.hideUntil != original.hideUntil) {
            return true
        }
        return when (accountType) {
            TYPE_LOCAL -> false
            TYPE_GOOGLE_TASKS -> !task.googleTaskUpToDate(original)
            TYPE_MICROSOFT -> !task.microsoftUpToDate(original)
            else -> !task.caldavUpToDate(original)
        }
    }

    suspend fun afterSave(task: Task, original: Task?) {
        val completionDateModified = task.completionDate != (original?.completionDate ?: 0)
        val deletionDateModified = task.deletionDate != (original?.deletionDate ?: 0)
        if (task.calendarURI?.isNotBlank() == true) {
            backgroundWork.updateCalendar(task)
        }
        if (task.dueDate != original?.dueDate && task.dueDate > currentTimeMillis()) {
            notifier.cancel(task.id, CancelReason.DUE_DATE_CHANGE)
        }
        if (completionDateModified || deletionDateModified) {
            locationService.updateGeofences(task.id)
        }
        if (!task.isSuppressRefresh()) {
            refreshBroadcaster.broadcastRefresh()
        }
        val wasPinned = (original?.pinned ?: 0L) > 0
        if ((task.pinned > 0 || wasPinned) &&
            (original == null || task.pinned != original.pinned || completionDateModified || deletionDateModified)
        ) {
            notifier.refreshPins()
        }
        notifier.triggerNotifications()
        backgroundWork.scheduleRefresh()
    }

    suspend fun setCollapsed(id: Long, collapsed: Boolean) {
        taskDao.setCollapsed(listOf(id), collapsed)
        refreshBroadcaster.broadcastRefresh()
    }

    suspend fun setCollapsed(preferences: QueryPreferences, filter: Filter, collapsed: Boolean) {
        taskDao.setCollapsed(preferences, filter, collapsed)
        refreshBroadcaster.broadcastRefresh()
    }
}
