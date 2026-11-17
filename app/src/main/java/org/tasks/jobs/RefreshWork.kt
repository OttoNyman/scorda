package org.tasks.jobs

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.analytics.Firebase
import org.tasks.data.dao.TaskDao
import org.tasks.date.DateTimeUtils
import org.tasks.repeats.SeriesScheduler
import org.tasks.service.TaskDeleter
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

@HiltWorker
class RefreshWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    firebase: Firebase,
    private val refreshBroadcaster: RefreshBroadcaster,
    private val workManager: WorkManager,
    private val taskDao: TaskDao,
    private val taskDeleter: TaskDeleter,
    private val seriesScheduler: SeriesScheduler,
) : RepeatingWorker(context, workerParams, firebase) {

    override suspend fun run(): Result {
        // runs at least once a day (midnight), which is all the trash retention needs
        // each on its own: one failing must not skip the other or the broadcast
        guarded { seriesScheduler.reconcile() }
        guarded { taskDeleter.purge() }
        refreshBroadcaster.broadcastRefresh()
        return Result.success()
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    override suspend fun scheduleNext() =
        workManager.scheduleRefresh(min(taskDao.nextRefresh(), DateTimeUtils.midnight()))
}