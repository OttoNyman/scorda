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

const val PERSISTENT_EPISODES = 3
const val PERSISTENT_INTERVAL_MS = 2 * 60_000L
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
    AlertLevel.PERSISTENT ->
        if (index < PERSISTENT_EPISODES - 1) PERSISTENT_INTERVAL_MS else null
    AlertLevel.SUPER ->
        if (index < SUPER_EPISODES - 1) SUPER_RING_MS + SUPER_PAUSE_MS else null
    else -> null
}

fun maxAlertLevel(levels: List<AlertLevel>): AlertLevel =
    levels.maxByOrNull { it.ordinal } ?: AlertLevel.ONCE

fun nextBatchDelay(levels: List<AlertLevel>, index: Int): Long? =
    levels.mapNotNull { level -> nextEpisodeDelay(level, index)?.let { level to it } }
        .maxByOrNull { it.first.ordinal }
        ?.second
