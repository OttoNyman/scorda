package org.tasks.data

import org.tasks.data.entity.Task
import org.tasks.data.dao.LocationDao
import org.tasks.data.entity.Geofence
import org.tasks.data.entity.Place
import org.tasks.preferences.AppPreferences

suspend fun LocationDao.getLocation(task: Task, preferences: AppPreferences): Location? {
    if (task.isNew) {
        if (task.hasTransitory(Place.KEY)) {
            getPlace(task.getTransitory<String>(Place.KEY)!!)?.let {
                return Location(createGeofence(it.uid, preferences), it)
            }
        }
    } else {
        return getGeofences(task.id)?.normalizedPlaceTrigger()
    }
    return null
}

fun Geofence.normalizedPlaceTrigger(): Geofence =
    if (isDeparture && !isArrival) {
        copy(isArrival = false, isDeparture = true)
    } else {
        copy(isArrival = true, isDeparture = false)
    }

fun Location.normalizedPlaceTrigger(): Location =
    copy(geofence = geofence.normalizedPlaceTrigger())

suspend fun createGeofence(
    place: String?,
    preferences: AppPreferences,
): Geofence {
    val onDeparture = preferences.defaultLocationReminder() == 2
    return Geofence(
        place = place,
        isArrival = !onDeparture,
        isDeparture = onDeparture,
    )
}
