package com.todoroo.astrid.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.UserActivityDao
import org.tasks.data.pictureUri
import org.tasks.files.FileHelper
import org.tasks.location.LocationService
import org.tasks.notifications.CancelReason
import org.tasks.notifications.NotificationManager
import org.tasks.service.TaskCleanup
import javax.inject.Inject

class AndroidCleanup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val locationService: LocationService,
    private val userActivityDao: UserActivityDao,
    private val locationDao: LocationDao,
) : TaskCleanup {

    override suspend fun cleanup(tasks: List<Long>) {
        if (tasks.isEmpty()) {
            return
        }
        notificationManager.cancel(tasks, CancelReason.CLEANUP)
        tasks.forEach { task ->
            locationDao.getGeofencesForTask(task).forEach {
                locationDao.delete(it)
                locationService.updateGeofences(it.place!!)
            }
            userActivityDao.getComments(task).forEach {
                FileHelper.delete(context, it.pictureUri)
                userActivityDao.delete(it)
            }
        }
    }

    // The rows stay in the trash with their comments and geofences, so a restore is complete.
    override suspend fun trashed(tasks: List<Long>) {
        if (tasks.isEmpty()) {
            return
        }
        notificationManager.cancel(tasks, CancelReason.CLEANUP)
        tasks.forEach { locationService.updateGeofences(it) }
    }
}
