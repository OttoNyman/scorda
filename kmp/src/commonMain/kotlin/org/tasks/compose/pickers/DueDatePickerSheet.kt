package org.tasks.compose.pickers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource
import org.tasks.data.entity.Task
import org.tasks.kmp.formatTime
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.time.millisOfDay
import org.tasks.time.startOfDay
import org.tasks.time.startOfMinute
import org.tasks.time.withMillisOfDay
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.date_picker_multiple
import tasks.kmp.generated.resources.no_time

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDatePickerSheet(
    initialDay: Long,
    initialTime: Int,
    is24Hour: Boolean,
    autoClose: Boolean = false,
    showNoDate: Boolean = true,
    showNoTime: Boolean = true,
    times: QuickPickTimes = QuickPickTimes(),
    onSelected: (day: Long, time: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { currentTimeMillis().startOfDay() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismiss = rememberDismissWithAnimation(sheetState)
    val state = rememberDateSheetState(
        initialDay = initialDay,
        initialTime = initialDueTime(initialTime),
        autoClose = autoClose,
        onSelected = { day, time -> dismiss { onSelected(day, time) } },
        onDismiss = { dismiss(onDismiss) },
    )
    // The day was derived from a picked time rather than tapped: dragging the dial re-derives it,
    // so passing through an hour that is already over does not pin the task to tomorrow.
    var dayFromTime by rememberSaveable { mutableStateOf(false) }

    fun applyTime(millisOfDay: Int) {
        val day = if (dayFromTime) NO_DAY else state.selectedDay
        dayFromTime = day == NO_DAY
        state.selectedDay = dayForAppliedTime(day, today, millisOfDay)
        state.selectedTime = millisOfDay
    }

    DatePickerBottomSheet(
        sheetState = sheetState,
        state = rememberSyncedDatePickerState(
            selectedDay = state.selectedDay,
            moveDisplayedMonth = true,
            onDaySelected = {
                dayFromTime = false
                state.setDay(it)
            },
        ),
        // the inline dial never auto-commits, so OK stays available even with autoClose
        showButtons = true,
        cancel = { dismiss(onDismiss) },
        accept = { state.commit() },
        noDate = if (showNoDate) {
            {
                state.selectedDay = NO_DAY
                state.selectedTime = NO_TIME
                state.commit()
            }
        } else {
            null
        },
    ) {
        DueTimePicker(
            selectedTime = { state.selectedTime },
            is24Hour = is24Hour,
            times = times,
            showNoTime = showNoTime,
            showMultiple = initialTime == MULTIPLE_TIMES,
            onDialTime = { applyTime(it) },
            onChipTime = { time ->
                if (time > NO_TIME) {
                    applyTime(time)
                } else {
                    state.selectedTime = time
                }
                state.autoCommit()
            },
        )
    }
}

/**
 * Inline clock dial plus one scrollable row of time chips. Chips report the TIME_MARKER-carrying
 * selection (or NO_TIME / MULTIPLE_TIMES) through [onChipTime] and move the dial; touching the dial
 * reports through [onDialTime]. With no time the dial rests on noon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueTimePicker(
    selectedTime: () -> Int,
    is24Hour: Boolean,
    times: QuickPickTimes,
    showNoTime: Boolean,
    showMultiple: Boolean,
    onDialTime: (Int) -> Unit,
    onChipTime: (Int) -> Unit,
) {
    val (initialHour, initialMinute) = remember { dialTime(selectedTime()) }
    val dial = rememberTimePickerState(initialHour, initialMinute, is24Hour)
    val currentOnDialTime by rememberUpdatedState(onDialTime)
    LaunchedEffect(dial) {
        snapshotFlow { dial.hour to dial.minute }
            .drop(1)
            // a chip already moved the dial to the selection; only a touch makes them differ
            // ponytail: re-touching the position the dial already shows (e.g. noon on "No time") emits nothing
            .filter { it != dialTime(selectedTime()) }
            .collect { (hour, minute) -> currentOnDialTime(dialSelection(hour, minute)) }
    }

    fun pick(time: Int) {
        val (hour, minute) = dialTime(time)
        dial.hour = hour
        dial.minute = minute
        onChipTime(time)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TimePicker(
            state = dial,
            colors = timePickerColors(),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val selected = selectedTime()
        val now = remember { currentTimeMillis() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showMultiple) {
                TimeChip(
                    text = stringResource(Res.string.date_picker_multiple),
                    selected = selected == MULTIPLE_TIMES,
                    onClick = { pick(MULTIPLE_TIMES) },
                )
            }
            listOf(times.morning, times.afternoon, times.evening, times.night).forEach {
                val time = it + TIME_MARKER
                TimeChip(
                    text = remember(time, is24Hour) { formatTime(now.withMillisOfDay(time), is24Hour) },
                    selected = selected == time,
                    onClick = { pick(time) },
                )
            }
            if (showNoTime) {
                TimeChip(
                    text = stringResource(Res.string.no_time),
                    selected = selected == NO_TIME,
                    onClick = { pick(NO_TIME) },
                )
            }
        }
    }
}

@Composable
private fun TimeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = text, maxLines = 1) },
    )
}

internal fun initialDueTime(time: Int): Int =
    time.takeIf { it == MULTIPLE_TIMES || Task.hasDueTime(it.toLong()) } ?: NO_TIME

internal fun dayForAppliedTime(selectedDay: Long, today: Long, millisOfDay: Int): Long =
    if (selectedDay == NO_DAY && millisOfDay > NO_TIME) dayForNewTime(today, millisOfDay) else selectedDay

fun dueDateFromSelection(day: Long, time: Int): Long = when {
    day == NO_DAY -> 0L
    time <= NO_TIME -> day
    else -> day.withMillisOfDay(time)
}

fun dueDateToSelection(dueDate: Long): Pair<Long, Int> =
    (if (dueDate > 0) dueDate.startOfDay() else NO_DAY) to dueDate.millisOfDay

fun alarmToSelection(timestamp: Long): Pair<Long, Int> =
    if (timestamp > 0) {
        timestamp.startOfDay() to timestamp.startOfMinute().millisOfDay + TIME_MARKER
    } else {
        NO_DAY to NO_TIME
    }

fun alarmFromSelection(day: Long, time: Int): Long =
    dueDateFromSelection(day, time).takeIf { it > 0 }?.startOfMinute() ?: 0L
