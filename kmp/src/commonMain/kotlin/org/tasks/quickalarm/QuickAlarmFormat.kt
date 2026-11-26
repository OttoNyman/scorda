package org.tasks.quickalarm

import org.tasks.time.ONE_MINUTE
import org.tasks.time.startOfMinute

val QUICK_ALARM_PRESETS_MINUTES = listOf(1, 5, 15, 30, 60)

const val QUICK_ALARM_MAX_MINUTES = 24 * 60

fun formatQuickAlarmOffset(totalMinutes: Int): String {
    val minutes = totalMinutes.coerceIn(0, QUICK_ALARM_MAX_MINUTES)
    val hours = minutes / 60
    val mins = minutes % 60
    return "+${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}"
}

fun addQuickAlarmPreset(currentMinutes: Int, presetMinutes: Int): Int =
    (currentMinutes.coerceAtLeast(0) + presetMinutes.coerceAtLeast(0))
        .coerceAtMost(QUICK_ALARM_MAX_MINUTES)

/**
 * Due = start of the current minute + N minutes (seconds dropped).
 * “+1” may fire in a few seconds — intentional; do not round the minute up.
 */
fun quickAlarmDueAtMillis(nowMillis: Long, intervalMinutes: Int): Long =
    nowMillis.startOfMinute() +
        intervalMinutes.coerceIn(0, QUICK_ALARM_MAX_MINUTES) * ONE_MINUTE
