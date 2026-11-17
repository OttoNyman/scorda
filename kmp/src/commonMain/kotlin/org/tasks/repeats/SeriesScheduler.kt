package org.tasks.repeats

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.constructor.FeatureGate
import org.tasks.data.TaskSaver
import org.tasks.data.UUIDHelper
import org.tasks.data.createDueDate
import org.tasks.data.dao.DeletionDao
import org.tasks.data.dao.DirtyDao
import org.tasks.data.dao.SeriesDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.Series
import org.tasks.data.entity.Task
import org.tasks.data.entity.Task.RepeatFrom
import org.tasks.notifications.CancelReason
import org.tasks.notifications.Notifier
import org.tasks.repeats.SeriesClock.dueMoment
import org.tasks.service.TaskDeleter
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min

private const val TAG = "SeriesScheduler"

/** What an edit of an occurrence changes (spec point 10). */
enum class SeriesEditScope { OCCURRENCE, SCHEDULE }

/**
 * Keeps each [Series] emitting its occurrences on time (spec: .notes/repeat-series.md). Every
 * entry point runs under one lock; together with [SeriesDao.findOccurrence] before each emit this
 * keeps one live occurrence per (series, scheduled).
 */
class SeriesScheduler(
    private val seriesDao: SeriesDao,
    private val taskDao: TaskDao,
    private val taskSaver: TaskSaver,
    private val taskDeleter: TaskDeleter,
    private val templates: SeriesTemplates,
    private val featureGate: FeatureGate?,
    private val refreshBroadcaster: RefreshBroadcaster,
    private val dirtyDao: DirtyDao? = null,
    private val notifier: Notifier? = null,
) {
    private val mutex = Mutex()

    /** Brings every series up to [now]. Returns the next time it has work to do, 0 for none. */
    suspend fun reconcile(now: Long = currentTimeMillis()): Long = mutex.withLock { reconcileLocked(now) }

    /**
     * Snoozing an occurrence postpones it to [until], so its successor no longer absorbs it. Only
     * forward: a reminder before the due date snoozed to a time still before it changes nothing.
     */
    suspend fun postpone(taskIds: List<Long>, until: Long) = mutex.withLock {
        val due = createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, until)
        taskDao.fetch(taskIds)
            .filter { it.series != null && !it.isCompleted && !it.isDeleted && due > it.dueDate }
            .forEach { taskSaver.save(it.copy().apply { setDueDateAdjustingHideUntil(due) }, it) }
    }

    /** Stops the series; its latest occurrence stays as the completed marker, the rest go. */
    suspend fun finish(seriesUuid: String, now: Long = currentTimeMillis()) = mutex.withLock {
        val series = seriesDao.getByUuid(seriesUuid) ?: return@withLock
        seriesDao.update(series.copy(finished = now, modified = now))
        val visible = seriesDao.getOccurrences(seriesUuid)
        val marker = visible.maxByOrNull { it.scheduled } ?: return@withLock
        discard(visible.filter { it.id != marker.id })
        val rows = (listOf(marker) + taskDao.fetch(taskDao.getChildren(marker.id)))
            .filter { !it.isCompleted && !it.readOnly }
        // as TaskCompleter does: a reminder still showing for the marker goes
        notifier?.cancel(rows.map { it.id }, CancelReason.COMPLETE)
        rows.forEach { taskSaver.save(it.copy(completionDate = now), it) }
    }

    /**
     * The completed marker of a finished series becomes its current occurrence again, at the first
     * rule time that has not come yet. False when the row is not such a marker or the rule is over.
     */
    suspend fun revive(taskId: Long, now: Long = currentTimeMillis()): Boolean = mutex.withLock {
        val row = taskDao.fetch(taskId) ?: return@withLock false
        val series = row.series?.let { seriesDao.getByUuid(it) }
        if (series == null || series.finished == 0L) {
            return@withLock false
        }
        val next = restartPoint(series, now)
        if (next == null) {
            Logger.w(TAG) { "Cannot revive ${series.uuid}: rule '${series.recurrence}' is over" }
            return@withLock false
        }
        seriesDao.update(
            series.copy(finished = 0, anchor = next.scheduled, recurrence = next.recurrence, modified = now)
        )
        val revived = row.copy(completionDate = 0, scheduled = next.scheduled)
        revived.setDueDateAdjustingHideUntil(next.scheduled)
        taskSaver.save(revived, row)
        reconcileLocked(now)
        true
    }

    /**
     * "Schedule" scope edit of an occurrence (spec point 10): the edited row is the new template,
     * not-yet-due occurrences are emitted again from it. Only a moved date ([previousDueDate] is
     * the row's due date before this edit) moves the rule and the anchor: a postponed row keeps
     * its own `dueDate != scheduled` through a plain edit.
     *
     * [newRecurrence] is null, or equal to the stored rule, for "rule not changed". Its COUNT means
     * the same as in the stored `series.recurrence`: occurrences remaining after the current
     * anchor. So an editor passes the stored COUNT back, not the total the user once typed. Slots
     * discarded here are refunded to it before they are emitted again.
     *
     * A changed rule may discard the edited row itself when it is not yet due: it comes back
     * under a new id, so do not write to [taskId] afterwards.
     *
     * [newRepeatFrom] null keeps the stored one.
     */
    suspend fun editSchedule(
        taskId: Long,
        newRecurrence: String?,
        previousDueDate: Long,
        now: Long = currentTimeMillis(),
        newRepeatFrom: Int? = null,
    ) = mutex.withLock {
        val original = taskDao.fetch(taskId) ?: return@withLock
        val series = original.series?.let { seriesDao.getByUuid(it) }
            ?.let { if (newRepeatFrom != null) it.copy(repeatFrom = newRepeatFrom) else it }
            ?: return@withLock
        val changedRule = newRecurrence?.takeUnless { sameRule(it, series.recurrence) }
        var recurrence = changedRule ?: series.recurrence
        var anchor = series.anchor
        val timed = series.repeatFrom == RepeatFrom.DUE_DATE
        val row = original.copy(recurrence = null)
        val moved = row.hasDueDate() && row.dueDate != previousDueDate
        if (moved) {
            if (changedRule == null) {
                recurrence = SeriesClock.rebase(recurrence, previousDueDate, row.dueDate)
            }
            var slot = SeriesClock.Next(row.dueDate, recurrence)
            if (timed && !row.isCompleted) {
                // moved into the past: as in adoption, the row must not be absorbed right away
                slot = catchUp(slot, now)
            }
            row.scheduled = slot.scheduled
            anchor = slot.scheduled
            recurrence = slot.recurrence
        }
        taskSaver.save(row, original)
        val template = templates.encode(templates.capture(taskId))
        if (timed) {
            val updated = series.copy(template = template)
            val visible = seriesDao.getOccurrences(series.uuid)
            val notYetDue = visible.filter {
                !it.isCompleted && it.dueDate == it.scheduled && dueMoment(it.scheduled) > now
            }
            val stale = notYetDue.filter { it.id != taskId }
            // latest slot that has come: a changed rule goes on from there
            val base = visible.filter { dueMoment(it.scheduled) <= now }.maxOfOrNull { it.scheduled }
            // without such a slot: the first one of the changed rule still to come, at the anchor's
            // time. Counted from one period back, so a slot later today is kept — but never a slot
            // before the first one still out there (or the anchor): that one was already completed.
            val restart = if (changedRule != null && base == null) {
                val rule = withCountRefund(changedRule, notYetDue.size)
                val floor = notYetDue.minOfOrNull { it.scheduled } ?: (anchor + 1)
                val period = SeriesClock.nextAfterCompletion(rule, anchor, anchor)?.let { it.scheduled - anchor }
                period
                    ?.let { SeriesClock.nextAfterCompletion(rule, anchor, now - it) }
                    ?.takeIf { dueMoment(it.scheduled) > now && it.scheduled >= floor }
                    ?: SeriesClock.nextAfterCompletion(rule, anchor, now)
            } else {
                null
            }
            // Every path that moves the anchor back refunds the discarded slots: their emission
            // was charged to COUNT, and emission from the new anchor pays again.
            when {
                moved -> {
                    discard(stale)
                    recurrence = withCountRefund(recurrence, stale.size)
                }
                changedRule != null && base != null -> {
                    // the edited row goes too when not yet due: its edits live on in the template
                    discard(notYetDue)
                    recurrence = withCountRefund(recurrence, notYetDue.size)
                    anchor = base
                }
                restart != null -> {
                    discard(notYetDue)
                    emitIfAbsent(updated, restart.scheduled)
                    recurrence = restart.recurrence
                    anchor = restart.scheduled
                }
                else -> {
                    // same slots from the new template; anchor and COUNT stay as they are. A
                    // changed rule lands here only when it has nothing left after now.
                    discard(stale)
                    stale.forEach { emitIfAbsent(updated, it.scheduled) }
                }
            }
        }
        seriesDao.update(
            series.copy(recurrence = recurrence, anchor = anchor, template = template, modified = now)
        )
        // their `§scorda` trailers carry the rule and `sm`: push them even when no row changed
        dirtyDao?.setDirty(seriesDao.getOccurrences(series.uuid).map { it.id })
        reconcileLocked(now)
    }

    /**
     * Snackbar undo of a completion. Discarded rows ([taskIds] may include subtasks) come back; the
     * occurrences among them are un-completed, and the series goes back to where it was: a
     * successor the completion emitted early goes again, a rule that ended with it is open again.
     * Rows that are not occurrences are left to the caller.
     */
    suspend fun undoCompletion(taskIds: List<Long>, now: Long = currentTimeMillis()) = mutex.withLock {
        val discarded = taskDao.fetch(taskIds).filter { it.deletionDate == DeletionDao.DISCARDED }
        if (discarded.isNotEmpty()) {
            taskDeleter.restore(discarded.map { it.id })
        }
        for (id in taskIds) {
            val row = taskDao.fetch(id)?.takeIf { it.series != null && it.isCompleted && !it.readOnly } ?: continue
            var series = seriesDao.getByUuid(row.series!!) ?: continue
            taskSaver.save(row.copy(completionDate = 0), row)
            series = series.copy(finished = 0)
            val early = dueMoment(row.scheduled) > now || series.repeatFrom == RepeatFrom.COMPLETION_DATE
            if (early && series.anchor != row.scheduled) {
                val successors = seriesDao.getOccurrences(series.uuid).filter {
                    it.id != row.id && !it.isCompleted && it.scheduled == series.anchor && it.dueDate == it.scheduled
                }
                if (successors.isNotEmpty()) {
                    discard(successors)
                    series = series.copy(
                        anchor = row.scheduled,
                        recurrence = withCountRefund(series.recurrence, successors.size),
                    )
                }
            }
            seriesDao.update(series.copy(modified = now))
        }
    }

    /**
     * The schedule of an occurrence that came in from Google (spec point 9), after [task] is saved
     * with its `series` and `scheduled`. A series not here yet is created from it, with the saved
     * row as its template; one here takes the rule only when [modified] is newer than its own.
     * Occurrences are never touched here: rows sharing a slot stay as they are.
     */
    suspend fun restore(
        task: Task,
        recurrence: String?,
        repeatFrom: Int?,
        created: Long?,
        modified: Long?,
        now: Long = currentTimeMillis(),
    ) = mutex.withLock {
        val uuid = task.series?.takeIf { task.scheduled > 0 } ?: return@withLock
        recurrence ?: return@withLock
        val local = seriesDao.getByUuid(uuid)
        if (local == null) {
            // could be the marker of a finished series; a live occurrence brings the series back
            if (task.isCompleted) {
                return@withLock
            }
            seriesDao.insert(
                Series(
                    uuid = uuid,
                    recurrence = recurrence,
                    repeatFrom = repeatFrom ?: RepeatFrom.DUE_DATE,
                    anchor = task.scheduled,
                    template = templates.encode(templates.capture(task.id)),
                    created = created ?: now,
                    modified = modified ?: now,
                )
            )
        } else if (modified != null && modified > local.modified) {
            seriesDao.update(
                local.copy(
                    recurrence = recurrence,
                    repeatFrom = repeatFrom ?: local.repeatFrom,
                    anchor = max(local.anchor, task.scheduled),
                    created = created ?: local.created,
                    modified = modified,
                )
            )
        } else if (task.scheduled > local.anchor) {
            // an occurrence emitted elsewhere: this series must not emit its slot again
            seriesDao.update(local.copy(anchor = task.scheduled))
        }
    }

    /** Recurrence cleared on an occurrence: the series ends and this row becomes a plain task. */
    suspend fun detach(taskId: Long, now: Long = currentTimeMillis()) = mutex.withLock {
        val original = taskDao.fetch(taskId) ?: return@withLock
        val uuid = original.series ?: return@withLock
        seriesDao.getByUuid(uuid)?.let { seriesDao.update(it.copy(finished = now, modified = now)) }
        discard(seriesDao.getOccurrences(uuid).filter { it.id != taskId })
        taskSaver.save(original.copy(series = null, scheduled = 0, recurrence = null), original)
    }

    private suspend fun reconcileLocked(now: Long): Long {
        if (featureGate?.current?.repeat == false) {
            return 0
        }
        var changed = adopt(now)
        var wake = Long.MAX_VALUE
        for (start in seriesDao.getActive()) {
            // one broken series must not stop the others
            try {
                var series = start

                // the template's list moved or went: emit where the series lives now, or nowhere
                val template = templates.decode(series.template)
                val listed = templates.inLiveList(template, seriesDao.getOccurrences(series.uuid))
                if (listed == null) {
                    seriesDao.update(series.copy(finished = now))
                    changed = true
                    continue
                }
                if (listed != template) {
                    series = series.copy(template = templates.encode(listed))
                }

                // completed occurrences go; completing the latest one early emits the next now
                for (done in seriesDao.getOccurrences(series.uuid).filter { it.isCompleted }) {
                    if (done.scheduled == series.anchor) {
                        val next = if (series.repeatFrom == RepeatFrom.COMPLETION_DATE) {
                            SeriesClock.nextAfterCompletion(series.recurrence, series.anchor, done.completionDate)
                        } else {
                            SeriesClock.next(series.recurrence, series.anchor)
                        }
                        if (next == null) {
                            // the rule is over: this row stays as the completed marker
                            series = series.copy(finished = now)
                            continue
                        }
                        emitIfAbsent(series, next.scheduled)
                        series = series.copy(anchor = next.scheduled, recurrence = next.recurrence)
                    }
                    discard(listOf(done))
                    changed = true
                }

                if (series.repeatFrom == RepeatFrom.DUE_DATE) {
                    while (series.finished == 0L && dueMoment(series.anchor) <= now) {
                        val next = SeriesClock.next(series.recurrence, series.anchor)
                        if (next == null) {
                            series = series.copy(finished = now)
                            break
                        }
                        // one whose successor has come already would be absorbed right away
                        val after = SeriesClock.next(next.recurrence, next.scheduled)
                        if ((after == null || dueMoment(after.scheduled) > now) && emitIfAbsent(series, next.scheduled)) {
                            changed = true
                        }
                        series = series.copy(anchor = next.scheduled, recurrence = next.recurrence)
                    }

                    // missed (not postponed) ones go when their successor comes
                    val missed = seriesDao.getOccurrences(series.uuid).filter {
                        !it.isCompleted && it.dueDate == it.scheduled && it.scheduled < series.anchor
                    }
                    for (occurrence in missed) {
                        val moment = successor(series.recurrence, occurrence.scheduled)?.let(::dueMoment) ?: continue
                        if (moment <= now) {
                            discard(listOf(occurrence))
                            changed = true
                        } else if (series.finished == 0L) {
                            wake = min(wake, moment)
                        }
                    }
                    if (series.finished == 0L) {
                        wake = min(wake, dueMoment(series.anchor))
                    }
                }

                if (series != start) {
                    seriesDao.update(series)
                    changed = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(tag = TAG, throwable = e) { "Series ${start.uuid} failed" }
            }
        }
        if (changed) {
            // emit saves broadcast before the subtasks exist
            refreshBroadcaster.broadcastRefresh()
        }
        return if (wake == Long.MAX_VALUE) 0 else wake
    }

    /** Legacy repeating rows become a series plus that row as its occurrence. */
    private suspend fun adopt(now: Long): Boolean {
        var adopted = false
        for (legacy in seriesDao.getLegacyRepeating()) {
            // one broken row must not block the others on every run
            try {
                if (adopt(legacy, now)) {
                    adopted = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(tag = TAG, throwable = e) { "Adopting task ${legacy.id} failed" }
            }
        }
        return adopted
    }

    private suspend fun adopt(legacy: Task, now: Long): Boolean {
        if (legacy.readOnly) {
            return false
        }
        val recurrence = legacy.recurrence!!
        val frequency = try {
            Recur.parse(recurrence).frequency
        } catch (e: IllegalArgumentException) {
            Logger.w(TAG) { "Not adopting task ${legacy.id}: cannot parse rule '$recurrence'" }
            return false
        }
        val subDay = frequency == Frequency.SECONDLY || frequency == Frequency.MINUTELY ||
            frequency == Frequency.HOURLY
        val due = when {
            // a sub-day rule needs a time: from a bare date its slots run from noon, and the
            // catch-up below would leave the row behind as a postponed occurrence
            subDay && !legacy.hasDueTime() -> createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, now)
            legacy.hasDueDate() -> legacy.dueDate
            else -> createDueDate(Task.URGENCY_SPECIFIC_DAY, now)
        }
        var row = legacy
        if (due != legacy.dueDate) {
            // before capture: absolute alarms are stored relative to the due date
            row = legacy.copy(dueDate = due)
            taskSaver.save(row, legacy)
        }
        // An overdue row whose successor has come already would be absorbed in this pass and
        // take the user's edits with it: it takes the latest slot that has come instead, and
        // keeps its own due date, so it counts as postponed.
        var slot = SeriesClock.Next(due, recurrence)
        if (!legacy.isCompleted && legacy.repeatFrom == RepeatFrom.DUE_DATE) {
            slot = catchUp(slot, now)
        }
        val template = templates.encode(templates.capture(legacy.id))
        val saved = taskDao.fetch(legacy.id) ?: row
        val uuid = UUIDHelper.newUUID()
        // a series must never exist without its row: it would emit duplicates of it
        taskDao.inTransaction {
            seriesDao.insert(
                Series(
                    uuid = uuid,
                    recurrence = slot.recurrence,
                    repeatFrom = legacy.repeatFrom,
                    anchor = slot.scheduled,
                    template = template,
                    created = now,
                    modified = now,
                    finished = if (legacy.isCompleted) now else 0,
                )
            )
            taskSaver.save(saved.copy(series = uuid, scheduled = slot.scheduled, recurrence = null), saved)
        }
        return true
    }

    /** Emits unless a live occurrence already sits at [scheduled]. */
    private suspend fun emitIfAbsent(series: Series, scheduled: Long): Boolean {
        if (seriesDao.findOccurrence(series.uuid, scheduled) != null) {
            return false
        }
        templates.emit(series, scheduled, templates.decode(series.template))
        return true
    }

    private suspend fun discard(tasks: List<Task>) {
        if (tasks.isNotEmpty()) {
            taskDeleter.discard(tasks.map { it.id })
        }
    }

    /**
     * Rule time after [scheduled], COUNT ignored: an occurrence before the anchor always had a
     * successor emitted, even once the stored COUNT has run down.
     */
    private fun successor(recurrence: String, scheduled: Long): Long? =
        SeriesClock.next(withoutCount(recurrence), scheduled)?.scheduled

    /** First occurrence of a revived series: the first rule time whose moment has not come. */
    private fun restartPoint(series: Series, now: Long): SeriesClock.Next? {
        if (series.repeatFrom == RepeatFrom.COMPLETION_DATE) {
            return SeriesClock.nextAfterCompletion(series.recurrence, series.anchor, now)
        }
        if (dueMoment(series.anchor) > now) {
            return SeriesClock.Next(series.anchor, series.recurrence)
        }
        // ponytail: walks the rule from the anchor, one step per period; fine for days, slow for a
        // minutely rule finished long ago. Jump ahead by whole periods if that ever matters.
        val free = withoutCount(series.recurrence)
        var last = series.anchor
        while (true) {
            val next = SeriesClock.next(free, last) ?: return null
            if (dueMoment(next.scheduled) > now) {
                break
            }
            last = next.scheduled
        }
        // skipped times were never emitted, so COUNT only pays for this one
        return SeriesClock.next(series.recurrence, last)
    }

    /** Latest slot from [start] on whose moment has come; COUNT pays for every slot passed. */
    private fun catchUp(start: SeriesClock.Next, now: Long): SeriesClock.Next {
        var slot = start
        while (true) {
            val next = SeriesClock.next(slot.recurrence, slot.scheduled) ?: break
            if (dueMoment(next.scheduled) > now) {
                break
            }
            slot = next
        }
        return slot
    }

    private fun sameRule(a: String, b: String): Boolean =
        a == b || try {
            Recur.parse(a) == Recur.parse(b)
        } catch (e: IllegalArgumentException) {
            false
        }

    private fun withCountRefund(recurrence: String, n: Int): String =
        try {
            val recur = Recur.parse(recurrence)
            recur.count?.let { recur.copy(count = it + n).toString() } ?: recurrence
        } catch (e: IllegalArgumentException) {
            recurrence
        }

    private fun withoutCount(recurrence: String): String =
        try {
            Recur.parse(recurrence).copy(count = null).toString()
        } catch (e: IllegalArgumentException) {
            recurrence
        }
}
