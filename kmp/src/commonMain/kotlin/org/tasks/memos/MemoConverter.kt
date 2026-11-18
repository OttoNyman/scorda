package org.tasks.memos

import org.tasks.constructor.Constructor
import org.tasks.data.TaskSaver
import org.tasks.data.createDueDate
import org.tasks.data.dao.AlarmDao
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.Task
import org.tasks.data.getDefaultAlarms
import org.tasks.data.setDefaultReminders
import com.todoroo.astrid.alarms.AlarmService
import org.tasks.data.TaskMover
import org.tasks.filters.CaldavFilter
import org.tasks.location.LocationService
import org.tasks.preferences.AppPreferences
import org.tasks.service.TaskDeleter

class MemoConverter(
    private val taskDao: TaskDao,
    private val caldavDao: CaldavDao,
    private val alarmDao: AlarmDao,
    private val locationDao: LocationDao,
    private val taskDeleter: TaskDeleter,
    private val taskMover: TaskMover,
    private val taskSaver: TaskSaver,
    private val alarmService: AlarmService,
    private val appPreferences: AppPreferences,
    private val locationService: LocationService,
    private val features: () -> Constructor,
    private val defaultList: suspend () -> CaldavFilter,
) {
    suspend fun canConvertToMemo(task: Task): Boolean {
        if (task.series != null) return false
        if (taskDao.getChildren(task.id).isNotEmpty()) return false
        return true
    }

    suspend fun toMemo(taskId: Long): ToMemoResult {
        val task = taskDao.fetch(taskId) ?: return ToMemoResult.SUCCESS
        if (!canConvertToMemo(task)) {
            return if (task.series != null) {
                ToMemoResult.BLOCKED_SERIES
            } else {
                ToMemoResult.BLOCKED_CHILDREN
            }
        }
        val original = task.copy()
        clearGeofences(taskId)
        taskDao.detachDeletedChildren(taskId)
        alarmDao.getAlarms(taskId).takeIf { it.isNotEmpty() }?.let { alarmDao.delete(it) }
        removeListRow(taskId)
        applyMemoFields(task)
        task.memo = true
        taskSaver.save(task, original)
        return ToMemoResult.SUCCESS
    }

    suspend fun toTask(taskId: Long) {
        val task = taskDao.fetch(taskId) ?: return
        val original = task.copy()
        task.memo = false
        task.order = null
        task.dueDate = createDueDate(Task.URGENCY_TODAY, 0)
        task.setDefaultReminders(appPreferences)
        task.hideUntil = 0
        task.recurrence = null
        task.series = null
        task.scheduled = 0
        task.parent = 0
        task.completionDate = 0
        taskSaver.save(task, original)
        taskMover.move(listOf(taskId), defaultList())
        val alarms = task.getDefaultAlarms(appPreferences.isDefaultDueTimeEnabled())
            .filter { features().schedules(it) }
        alarmService.synchronizeAlarms(taskId, alarms.toMutableSet())
    }

    suspend fun hasRemoteListRow(taskId: Long): Boolean {
        val caldavTask = caldavDao.getTask(taskId) ?: return false
        val account = caldavTask.calendar
            ?.let { caldavDao.getCalendarByUuid(it) }
            ?.account
            ?.let { caldavDao.getAccountByUuid(it) }
        return account != null && !account.isLocalList
    }

    private fun applyMemoFields(task: Task) {
        task.dueDate = 0
        task.hideUntil = 0
        task.completionDate = 0
        task.recurrence = null
        task.series = null
        task.scheduled = 0
        task.parent = 0
        task.order = null
        task.ringFlags = 0
    }

    private suspend fun removeListRow(taskId: Long) {
        val caldavTask = caldavDao.getTask(taskId) ?: return
        val account = caldavTask.calendar
            ?.let { caldavDao.getCalendarByUuid(it) }
            ?.account
            ?.let { caldavDao.getAccountByUuid(it) }
        if (account?.isLocalList == true) {
            caldavDao.delete(caldavTask)
        } else {
            taskDeleter.markMoved(listOf(taskId))
        }
    }

    private suspend fun clearGeofences(taskId: Long) {
        locationDao.getGeofencesForTask(taskId).forEach { geofence ->
            val place = geofence.place
            locationDao.delete(geofence)
            place?.let { locationService.updateGeofences(it) }
        }
    }
}