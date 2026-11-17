package org.tasks.repeats

import com.todoroo.astrid.repeats.RepeatTaskHelper
import org.tasks.data.entity.Task
import org.tasks.time.DateTime

/** Pure schedule math for repeating series. */
object SeriesClock {
    data class Next(val scheduled: Long, val recurrence: String)

    /**
     * When an occurrence "comes due" and its successor appears: a due time is itself, a date-only
     * value comes due at the start of the next local day.
     */
    fun dueMoment(scheduled: Long): Long =
        if (Task.hasDueTime(scheduled)) {
            scheduled
        } else {
            DateTime(scheduled).startOfDay().plusDays(1).millis
        }

    /**
     * Next rule time strictly after [after] plus the rule to store afterwards (COUNT decremented as
     * in [RepeatTaskHelper.handleRepeat]). Null when the rule is over or unusable.
     */
    fun next(recurrence: String, after: Long): Next? =
        advance(recurrence, after, Task(dueDate = after, recurrence = recurrence), afterCompletion = false)

    /**
     * Like [next] for a [Task.RepeatFrom.COMPLETION_DATE] series: counted from [completedAt], keeping
     * the time of day of [anchor]. Strictly after [completedAt].
     */
    fun nextAfterCompletion(recurrence: String, anchor: Long, completedAt: Long): Next? =
        advance(
            recurrence,
            completedAt,
            Task(dueDate = anchor, completionDate = completedAt, recurrence = recurrence),
            afterCompletion = true,
        )

    private fun advance(recurrence: String, after: Long, task: Task, afterCompletion: Boolean): Next? {
        val recur = try {
            Recur.parse(recurrence)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val count = recur.count
        if (count == 1) {
            return null
        }
        val scheduled = try {
            RepeatTaskHelper.computeNextDueDate(task, recurrence, repeatAfterCompletion = afterCompletion)
        } catch (e: IllegalArgumentException) {
            return null
        }
        // -1 is the end of the rule; <= after would not advance, so treat it as the end too.
        if (scheduled <= after) {
            return null
        }
        val stored = if (count != null && count > 1) {
            recur.copy(count = count - 1).toString()
        } else {
            recurrence
        }
        return Next(scheduled, stored)
    }

    /**
     * Moves a single BYDAY weekday / single positive BYMONTHDAY to the day of [to]. Any other rule
     * comes back unchanged. The time of day lives in the anchor, not in the rule.
     */
    fun rebase(recurrence: String, from: Long, to: Long): String {
        val recur = try {
            Recur.parse(recurrence)
        } catch (e: IllegalArgumentException) {
            return recurrence
        }
        val target = DateTime(to)
        return when {
            recur.byDay.size == 1 ->
                recur.copy(byDay = listOf(recur.byDay[0].copy(day = target.weekday)))
            recur.byMonthDay.size == 1 && recur.byMonthDay[0] > 0 ->
                recur.copy(byMonthDay = listOf(target.dayOfMonth))
            else -> return recurrence
        }.toString()
    }
}
