package org.tasks.reminders

import org.jetbrains.compose.resources.StringResource
import org.tasks.compose.pickers.QuickPickTimes
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.time.ONE_HOUR
import org.tasks.time.ONE_MINUTE
import org.tasks.time.plusDays
import org.tasks.time.startOfMinute
import org.tasks.time.withMillisOfDay
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.date_shortcut_evening
import tasks.kmp.generated.resources.date_shortcut_hour
import tasks.kmp.generated.resources.date_shortcut_morning
import tasks.kmp.generated.resources.date_shortcut_tomorrow_evening
import tasks.kmp.generated.resources.date_shortcut_tomorrow_morning

data class SnoozeOption(
    val label: StringResource,
    val timestamp: Long,
)

private const val CUTOFF_MINUTES = 75

fun snoozeOptions(
    times: QuickPickTimes,
    now: Long = currentTimeMillis(),
): List<SnoozeOption> {
    val morning = now.withMillisOfDay(times.morning)
    val evening = now.withMillisOfDay(times.evening)
    val cutoff = now + CUTOFF_MINUTES * ONE_MINUTE
    return buildList {
        add(SnoozeOption(Res.string.date_shortcut_hour, (now + ONE_HOUR).startOfMinute()))
        when {
            morning > cutoff -> {
                add(SnoozeOption(Res.string.date_shortcut_morning, morning))
                add(SnoozeOption(Res.string.date_shortcut_evening, evening))
            }
            evening > cutoff -> {
                add(SnoozeOption(Res.string.date_shortcut_evening, evening))
                add(SnoozeOption(Res.string.date_shortcut_tomorrow_morning, morning.plusDays(1)))
            }
            else -> {
                add(SnoozeOption(Res.string.date_shortcut_tomorrow_morning, morning.plusDays(1)))
                add(SnoozeOption(Res.string.date_shortcut_tomorrow_evening, evening.plusDays(1)))
            }
        }
    }
}
