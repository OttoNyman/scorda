package org.tasks.reminders

import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Alarm.Companion.TYPE_REL_END
import org.tasks.time.ONE_DAY
import org.tasks.time.ONE_HOUR
import org.tasks.time.ONE_MINUTE

/** Offsets offered for the advance reminder, in milliseconds before the reminder time. */
val ADVANCE_CHOICES: List<Long> =
    listOf(5 * ONE_MINUTE, 15 * ONE_MINUTE, 30 * ONE_MINUTE, ONE_HOUR, 2 * ONE_HOUR, ONE_DAY)

/** The advance reminder: one ring before the reminder time, not repeating. */
fun Alarm.isAdvance(): Boolean = type == TYPE_REL_END && time < 0 && repeat == 0

/** The advance the "In advance" row owns: the one closest to the reminder time. */
fun Collection<Alarm>.advance(): Alarm? = filter { it.isAdvance() }.maxByOrNull { it.time }

/**
 * Default reminders after the "In advance" toggle flips, or null when they stay as they are.
 * Turning it on adds nothing (the default is "None" until chosen); turning it off drops the
 * default, otherwise "More reminders" would keep putting it on every new task.
 */
fun List<Alarm>.forAdvanceToggle(wasOn: Boolean, isOn: Boolean): List<Alarm>? =
    if (wasOn && !isOn && advance() != null) withAdvance(null) else null

/** A default early ring is not added next to the one the task already has. */
fun Collection<Alarm>.acceptsDefault(alarm: Alarm): Boolean = !alarm.isAdvance() || advance() == null

/** Replaces [advance] with one [offset] before the time; null or 0 removes it. */
fun Collection<Alarm>.withAdvance(offset: Long?): List<Alarm> {
    val current = advance()
    val rest = filterNot { it === current }
    return if (offset != null && offset > 0) rest + Alarm(type = TYPE_REL_END, time = -offset) else rest
}
