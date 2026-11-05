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
const val SUPER_EPISODES = 3
const val SUPER_RING_MS = 60_000L
const val SUPER_PAUSE_MS = 5 * 60_000L

fun alertLevel(ringFlags: Int): AlertLevel = when (ringFlags) {
    Task.NOTIFY_MODE_SILENT -> AlertLevel.SILENT
    Task.NOTIFY_MODE_FIVE -> AlertLevel.PERSISTENT
    Task.NOTIFY_MODE_NONSTOP -> AlertLevel.SUPER
    else -> AlertLevel.ONCE
}

fun ringFlags(level: AlertLevel): Int = when (level) {
    AlertLevel.SILENT -> Task.NOTIFY_MODE_SILENT
    AlertLevel.ONCE -> 0
    AlertLevel.PERSISTENT -> Task.NOTIFY_MODE_FIVE
    AlertLevel.SUPER -> Task.NOTIFY_MODE_NONSTOP
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
    AlertLevel.SUPER ->
        if (index < SUPER_EPISODES - 1) SUPER_RING_MS + SUPER_PAUSE_MS else null
    else -> null
}

fun maxAlertLevel(levels: List<AlertLevel>): AlertLevel =
    levels.maxByOrNull { it.ordinal } ?: AlertLevel.ONCE
