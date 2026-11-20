package org.tasks.pins

import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.data.dao.SeriesDao
import org.tasks.data.dao.TaskDao
import org.tasks.notifications.Notifier
import org.tasks.repeats.SeriesTemplates
import org.tasks.time.DateTimeUtils2.currentTimeMillis

/** Pins and unpins tasks and memos; a pin on a series makes every new occurrence come pinned. */
class TaskPinner(
    private val taskDao: TaskDao,
    private val seriesDao: SeriesDao,
    private val templates: SeriesTemplates,
    private val refreshBroadcaster: RefreshBroadcaster,
    private val notifier: Notifier,
) {
    suspend fun pin(ids: List<Long>, wholeSeries: Boolean = false, now: Long = currentTimeMillis()) {
        taskDao.setPinned(ids, now)
        if (wholeSeries) {
            seriesOf(ids).forEach { uuid ->
                setSeriesPinned(uuid, true)
                // every open occurrence of the series, not just the one it was chosen on
                taskDao.setPinned(seriesDao.getOccurrences(uuid).filterNot { it.isCompleted }.map { it.id }, now)
            }
        }
        changed()
    }

    /** Unpinning a task of a pinned series unpins the series too: otherwise tomorrow comes pinned again. */
    suspend fun unpin(ids: List<Long>) {
        taskDao.setPinned(ids, 0L)
        seriesOf(ids).filter { isSeriesPinned(it) }.forEach { uuid ->
            setSeriesPinned(uuid, false)
            // the pin came from the series: it leaves every occurrence the series pinned
            taskDao.setPinned(seriesDao.getOccurrences(uuid).map { it.id }, 0L)
        }
        changed()
    }

    suspend fun isSeriesPinned(uuid: String): Boolean =
        seriesDao.getByUuid(uuid)?.let { templates.decode(it.template).pinned } == true

    private suspend fun seriesOf(ids: List<Long>): Set<String> =
        ids.mapNotNull { taskDao.fetch(it)?.series }.toSet()

    private suspend fun setSeriesPinned(uuid: String, pinned: Boolean) {
        val series = seriesDao.getByUuid(uuid) ?: return
        val template = templates.decode(series.template)
        if (template.pinned == pinned) return
        seriesDao.update(series.copy(template = templates.encode(template.copy(pinned = pinned))))
    }

    private suspend fun changed() {
        refreshBroadcaster.broadcastRefresh()
        notifier.refreshPins()
    }
}
