package org.tasks.googleapis

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.tasks.data.createDueDate
import org.tasks.data.entity.Task
import org.tasks.data.entity.Task.Priority
import org.tasks.date.DateTimeUtils.newDateTime
import org.tasks.extensions.truncate

internal object GoogleTaskNotes {
    private const val HEAD = "§scorda "
    private const val MARKER = "\n$HEAD"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Serializable
    data class Extra(
        val dueMin: Int? = null,
        val priority: Int? = null,
        val start: Long? = null,
        val recur: String? = null,
    )

    fun from(task: Task): Extra? {
        val dueMin = if (task.hasDueTime()) {
            val due = newDateTime(task.dueDate)
            due.hourOfDay * 60 + due.minuteOfHour
        } else {
            null
        }
        val extra = Extra(
            dueMin = dueMin,
            priority = task.priority.takeUnless { it == Priority.NONE },
            start = task.hideUntil.takeIf { it > 0 },
            recur = task.recurrence?.takeIf { it.isNotBlank() },
        )
        return extra.takeUnless { it == Extra() }
    }

    fun unpack(notes: String?): Pair<String?, Extra?> {
        if (notes.isNullOrEmpty()) return notes to null
        val at = when {
            notes.startsWith(HEAD) -> 0
            else -> notes.lastIndexOf(MARKER).takeIf { it >= 0 } ?: return notes to null
        }
        val raw = notes.substring(at).removePrefix(MARKER).removePrefix(HEAD).trim()
        val extra = try {
            json.decodeFromString<Extra>(raw)
        } catch (_: Exception) {
            return notes to null
        }
        val human = notes.substring(0, at).trimEnd().ifEmpty { null }
        return human to extra
    }

    fun pack(notes: String?, task: Task, max: Int): String? {
        val human = unpack(notes).first
        val extra = from(task) ?: return clip(human, max)
        val suffix = (if (human.isNullOrEmpty()) "" else "\n") + HEAD + json.encodeToString(extra)
        val joined = if (human.isNullOrEmpty()) suffix else human.trimEnd() + suffix
        return if (joined.length <= max) joined else clip(human, max)
    }

    fun apply(task: Task, extra: Extra) {
        if (task.hasDueDate()) {
            val due = newDateTime(task.dueDate)
            val withClock = if (extra.dueMin != null) {
                val minute = extra.dueMin.coerceIn(0, 23 * 60 + 59)
                due.withHourOfDay(minute / 60)
                    .withMinuteOfHour(minute % 60)
                    .withSecondOfMinute(1)
                    .withMillisOfSecond(0)
                    .millis
            } else {
                due.millis
            }
            val urgency = if (extra.dueMin != null) {
                Task.URGENCY_SPECIFIC_DAY_TIME
            } else {
                Task.URGENCY_SPECIFIC_DAY
            }
            task.setDueDateAdjustingHideUntil(createDueDate(urgency, withClock))
        }
        task.priority = extra.priority ?: Priority.NONE
        task.recurrence = extra.recur
        task.hideUntil = extra.start ?: 0L
    }

    private fun clip(value: String?, max: Int): String? =
        value?.truncate(max)?.ifEmpty { null }
}
