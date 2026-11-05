package org.tasks.location

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.tasks.analytics.Firebase
import org.tasks.data.dao.LocationDao
import org.tasks.Notifier
import org.tasks.injection.BaseWorker
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import timber.log.Timber

@HiltWorker
class AndroidGeofenceTransitionWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    firebase: Firebase,
    private val locationDao: LocationDao,
    private val notifier: Notifier,
    private val featureGate: org.tasks.constructor.FeatureGate,
) : BaseWorker(context, workerParams, firebase) {

    override suspend fun run(): Result {
        val arrival = inputData.getBoolean(EXTRA_ARRIVAL, false)
        val placeId = inputData.getLong(EXTRA_PLACE_ID, 0)
        Timber.d("geofence placeId=%d arrival=%s", placeId, arrival)
        val place = locationDao.getPlace(placeId) ?: run {
            Timber.e("Failed to find place %d", placeId)
            return Result.failure()
        }
        try {
            val now = currentTimeMillis()
            // Start date off: an old start date must not block the place reminder.
            val startedBefore = if (featureGate.current.startDate) now else Long.MAX_VALUE
            val geofences = if (arrival) {
                locationDao.getArrivalGeofences(place.uid!!, now, startedBefore)
            } else {
                locationDao.getDepartureGeofences(place.uid!!, now, startedBefore)
            }
            notifier.triggerNotifications(place.id, geofences, arrival)
        } catch (e: Exception) {
            firebase.reportException(e)
            Timber.e(e, "Error triggering geofence for place %d: %s", placeId, e.message)
        }
        return Result.success()
    }

    companion object {
        const val EXTRA_ARRIVAL = "extra_arrival"
        const val EXTRA_PLACE_ID = "extra_place_id"
    }
}
