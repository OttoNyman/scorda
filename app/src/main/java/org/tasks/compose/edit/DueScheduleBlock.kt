package org.tasks.compose.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource as kmpStringResource
import org.tasks.R
import org.tasks.compose.components.SymbolIcon
import org.tasks.compose.pickers.TIME_MARKER
import org.tasks.compose.pickers.dayForInlineTimeChange
import org.tasks.compose.pickers.dialSelection
import org.tasks.compose.pickers.dialTime
import org.tasks.compose.pickers.dueDateFromSelection
import org.tasks.compose.pickers.dueDateToSelection
import org.tasks.data.entity.Task
import org.tasks.extensions.Context.findActivity
import org.tasks.extensions.hideKeyboard
import org.tasks.themes.TasksIcons
import org.tasks.time.DateTime
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.time.startOfDay
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.no_time
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueScheduleBlock(
    dueDate: Long,
    isRecurring: Boolean,
    is24Hour: Boolean,
    dayPristineDefaultToday: Boolean,
    defaultDueTimeMillisOfDay: Int,
    onDueDateChange: (Long) -> Unit,
    onDayTouched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (dueDate <= 0L) return

    val context = LocalContext.current
    val hideKeyboard = { context.findActivity()?.hideKeyboard() }
    val (day, time) = dueDateToSelection(dueDate)
    val hasTime = Task.hasDueTime(dueDate)
    val todayState by rememberUpdatedState(currentTimeMillis().startOfDay())
    val onDueDateChangeState by rememberUpdatedState(onDueDateChange)
    val onDayTouchedState by rememberUpdatedState(onDayTouched)
    val pristine by rememberUpdatedState(dayPristineDefaultToday)
    val dayState by rememberUpdatedState(day)
    val (committedHour, committedMinute) = dialTime(time)
    val committedHourState by rememberUpdatedState(committedHour)
    val committedMinuteState by rememberUpdatedState(committedMinute)

    Column(modifier = modifier.fillMaxWidth()) {
        // Pure Compose month: CalendarView blanked on first open inside nested scroll;
        // Material3 DatePicker ANR'd.
        MonthCalendar(
            selectedDay = day,
            onDayClick = { picked ->
                hideKeyboard()
                onDayTouchedState()
                onDueDateChangeState(
                    dueDateFromSelection(picked, if (hasTime) time else 0),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!isRecurring) {
                FilterChip(
                    selected = false,
                    onClick = {
                        hideKeyboard()
                        onDueDateChangeState(0L)
                    },
                    label = { Text(stringResource(R.string.no_date)) },
                )
            }
            if (!hasTime) {
                FilterChip(
                    selected = false,
                    onClick = {
                        hideKeyboard()
                        val millisOfDay = timedMillisOfDay(defaultDueTimeMillisOfDay)
                        val targetDay = dayForInlineTimeChange(
                            selectedDay = day,
                            today = todayState,
                            millisOfDay = millisOfDay,
                            dayPristineDefaultToday = pristine,
                        )
                        onDueDateChangeState(dueDateFromSelection(targetDay, millisOfDay))
                    },
                    label = { Text(stringResource(R.string.add_due_time)) },
                )
            } else {
                FilterChip(
                    selected = false,
                    onClick = {
                        hideKeyboard()
                        onDueDateChangeState(dueDateFromSelection(day, 0))
                    },
                    label = { Text(kmpStringResource(Res.string.no_time)) },
                )
            }
        }
        if (hasTime) {
            key(hasTime) {
                val (hour, minute) = dialTime(time)
                val timePickerState = rememberTimePickerState(
                    initialHour = hour,
                    initialMinute = minute,
                    is24Hour = is24Hour,
                )
                LaunchedEffect(timePickerState) {
                    snapshotFlow { timePickerState.hour to timePickerState.minute }
                        .distinctUntilChanged()
                        .collect { (h, m) ->
                            if (h == committedHourState && m == committedMinuteState) return@collect
                            val millisOfDay = dialSelection(h, m)
                            val targetDay = dayForInlineTimeChange(
                                selectedDay = dayState,
                                today = todayState,
                                millisOfDay = millisOfDay,
                                dayPristineDefaultToday = pristine,
                            )
                            onDueDateChangeState(dueDateFromSelection(targetDay, millisOfDay))
                        }
                }
                TimePicker(
                    state = timePickerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    selectedDay: Long,
    onDayClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = remember(selectedDay) { DateTime(selectedDay) }
    var visibleMonth by remember {
        mutableLongStateOf(selected.withDayOfMonth(1).millis)
    }
    LaunchedEffect(selectedDay) {
        val sel = DateTime(selectedDay)
        val vis = DateTime(visibleMonth)
        if (sel.year != vis.year || sel.monthOfYear != vis.monthOfYear) {
            visibleMonth = sel.withDayOfMonth(1).millis
        }
    }
    val month = remember(visibleMonth) { DateTime(visibleMonth) }
    val title = remember(visibleMonth) {
        SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(visibleMonth))
    }
    val weekdayLabels = remember {
        val cal = Calendar.getInstance()
        val first = cal.firstDayOfWeek
        (0..6).map { i ->
            cal.set(Calendar.DAY_OF_WEEK, (first - 1 + i) % 7 + 1)
            SimpleDateFormat("EEEEE", Locale.getDefault()).format(cal.time)
        }
    }
    val cells = remember(visibleMonth) { monthCells(visibleMonth) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { visibleMonth = month.plusMonths(-1).millis }) {
                SymbolIcon(name = "keyboard_arrow_left", contentDescription = null)
            }
            Text(
                text = title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { visibleMonth = month.plusMonths(1).millis }) {
                SymbolIcon(name = TasksIcons.KEYBOARD_ARROW_RIGHT, contentDescription = null)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cell != null) {
                            val cellDay = DateTime(cell)
                            val isSelected =
                                cellDay.year == selected.year &&
                                    cellDay.monthOfYear == selected.monthOfYear &&
                                    cellDay.dayOfMonth == selected.dayOfMonth
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                modifier = Modifier
                                    .padding(2.dp)
                                    .clickable { onDayClick(cell.startOfDay()) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = cellDay.dayOfMonth.toString(),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 42 cells (6 weeks), null = padding outside the month. */
private fun monthCells(monthStartMillis: Long): List<Long?> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = monthStartMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val firstDow = cal.get(Calendar.DAY_OF_WEEK)
    val pad = (firstDow - cal.firstDayOfWeek + 7) % 7
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = ArrayList<Long?>(42)
    repeat(pad) { cells.add(null) }
    repeat(daysInMonth) { d ->
        cal.set(Calendar.DAY_OF_MONTH, d + 1)
        cells.add(cal.timeInMillis)
    }
    while (cells.size < 42) cells.add(null)
    return cells
}

private fun timedMillisOfDay(raw: Int): Int =
    if (Task.hasDueTime(raw.toLong())) raw else raw + TIME_MARKER
