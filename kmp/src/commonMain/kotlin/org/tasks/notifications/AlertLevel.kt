package org.tasks.notifications

import org.tasks.data.entity.Task

enum class AlertLevel { SILENT, ONCE, PERSISTENT, SUPER }

enum class Route { NONE, NOTIFICATION, ALARM, CALL_TONE }

data class SoundState(
    val inCall: Boolean,
    val ringerNormal: Boolean,
    val superBypassesSilent: Boolean,
    val vibrateAllowed: Boolean = true,
)

val PERSISTENT_PAUSES_MS: List<Long> = listOf(1L, 2, 4, 8, 15, 30).map { it * 60_000L }
const val SUPER_INTERVAL_MS = 5 * 60_000L
const val SUPER_EPISODE_COUNT = 7
const val DEFAULT_ALARM_RING_MS = 60_000L
const val ALARM_RING_MIN_MS = 20_000L
const val ALARM_RING_MAX_MS = 60_000L
const val ALARM_RING_STEP_MS = 5_000L
const val ALARM_FADE_MS = 5_000L
const val TONE_CAP_MS = 5_000L
const val PERSISTENT_GAP_MS = 500L

fun alertLevelOf(priority: Int): AlertLevel = when (priority) {
    Task.Priority.HIGH -> AlertLevel.SUPER
    Task.Priority.MEDIUM -> AlertLevel.PERSISTENT
    Task.Priority.NONE -> AlertLevel.SILENT
    else -> AlertLevel.ONCE
}

fun priorityOf(level: AlertLevel): Int = when (level) {
    AlertLevel.SUPER -> Task.Priority.HIGH
    AlertLevel.PERSISTENT -> Task.Priority.MEDIUM
    AlertLevel.SILENT -> Task.Priority.NONE
    AlertLevel.ONCE -> Task.Priority.LOW
}

fun prioritiesOf(levels: Set<AlertLevel>): Set<Int> =
    (levels + AlertLevel.ONCE).map(::priorityOf).toSet()

fun effectiveAlertLevel(priority: Int, enabled: Set<AlertLevel>): AlertLevel {
    val level = alertLevelOf(priority)
    return if (level == AlertLevel.ONCE || level in enabled) level else AlertLevel.ONCE
}

fun alertLevel(task: Task, enabled: Set<AlertLevel>): AlertLevel? {
    if (task.memo) return null
    // A day with no time shares one quiet reminder. Priority only orders those tasks.
    if (task.hasDueDate() && !task.hasDueTime()) return AlertLevel.SILENT
    return effectiveAlertLevel(task.priority, enabled)
}

fun enabledLevels(
    silent: Boolean = false,
    persistent: Boolean = false,
    alarm: Boolean = true,
): Set<AlertLevel> = buildSet {
    add(AlertLevel.ONCE)
    if (silent) add(AlertLevel.SILENT)
    if (persistent) add(AlertLevel.PERSISTENT)
    if (alarm) add(AlertLevel.SUPER)
}

fun route(level: AlertLevel, state: SoundState): Route = when {
    level == AlertLevel.SILENT -> Route.NONE
    state.inCall -> Route.CALL_TONE
    level == AlertLevel.SUPER && (state.ringerNormal || state.superBypassesSilent) -> Route.ALARM
    level == AlertLevel.SUPER -> Route.NONE
    else -> Route.NOTIFICATION
}

fun vibrate(level: AlertLevel, state: SoundState): Boolean =
    level != AlertLevel.SILENT && !state.inCall && state.vibrateAllowed

fun nextEpisodeDelay(level: AlertLevel, index: Int): Long? = when (level) {
    AlertLevel.PERSISTENT -> PERSISTENT_PAUSES_MS.getOrNull(index)
    AlertLevel.SUPER -> if (index < SUPER_EPISODE_COUNT - 1) SUPER_INTERVAL_MS else null
    else -> null
}

fun maxAlertLevel(levels: List<AlertLevel>): AlertLevel =
    levels.maxByOrNull { it.ordinal } ?: AlertLevel.ONCE

fun clampAlarmRingMs(ms: Long): Long {
    val stepped = ((ms - ALARM_RING_MIN_MS) / ALARM_RING_STEP_MS) * ALARM_RING_STEP_MS + ALARM_RING_MIN_MS
    return stepped.coerceIn(ALARM_RING_MIN_MS, ALARM_RING_MAX_MS)
}
