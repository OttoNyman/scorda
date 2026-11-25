package org.tasks.compose.pickers

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.tasks.data.entity.Task
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.time.plusDays
import org.tasks.time.toLocalDateMillis
import org.tasks.time.toUtcDateMillis
import org.tasks.time.withMillisOfDay

internal fun dayForNewTime(today: Long, millisOfDay: Int): Long =
    if (today.withMillisOfDay(millisOfDay) > currentTimeMillis()) today else today.plusDays(1)

internal fun demoteDueTime(day: Long): Long = if (day == DUE_TIME) DUE_DATE else day

private const val MINUTE = 60_000
private const val HOUR = 60 * MINUTE

/** Hour and minute the clock dial shows for [selectedTime]; noon when there is no time. */
fun dialTime(selectedTime: Int): Pair<Int, Int> =
    if (Task.hasDueTime(selectedTime.toLong())) {
        selectedTime / HOUR to selectedTime / MINUTE % 60
    } else {
        12 to 0
    }

/** Selection value for a time picked on the dial, carrying the [TIME_MARKER]. */
fun dialSelection(hour: Int, minute: Int): Int = hour * HOUR + minute * MINUTE + TIME_MARKER

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerSheet(
    sheetState: SheetState,
    selectedDay: Long,
    moveDisplayedMonth: Boolean,
    onDaySelected: (Long) -> Unit,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    dateShortcuts: @Composable ColumnScope.() -> Unit,
    timeShortcuts: @Composable ColumnScope.() -> Unit,
    autoClose: Boolean = false,
) {
    val datePickerState = rememberSyncedDatePickerState(
        selectedDay = selectedDay,
        moveDisplayedMonth = moveDisplayedMonth,
        onDaySelected = onDaySelected,
    )
    DatePickerBottomSheet(
        sheetState = sheetState,
        state = datePickerState,
        showButtons = !autoClose,
        cancel = onDismiss,
        accept = onAccept,
        headline = {
            DatePickerShortcuts(
                dateShortcuts = dateShortcuts,
                timeShortcuts = timeShortcuts,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberDismissWithAnimation(sheetState: SheetState): (action: () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(sheetState) {
        { action ->
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) action()
            }
        }
    }
}

@Stable
class DateSheetState internal constructor(
    initialDay: Long,
    initialTime: Int,
    private val autoClose: Boolean,
    private val onSelected: (day: Long, time: Int) -> Unit,
    private val onDismiss: () -> Unit,
) {
    var selectedDay by mutableLongStateOf(initialDay)
    var selectedTime by mutableIntStateOf(initialTime)
    private val baselineDay = initialDay
    private val baselineTime = initialTime

    fun commit() {
        if (selectedDay != baselineDay || selectedTime != baselineTime) {
            onSelected(selectedDay, selectedTime)
        } else {
            onDismiss()
        }
    }

    fun autoCommit() {
        if (autoClose) commit()
    }

    fun setDay(day: Long) {
        selectedDay = day
        autoCommit()
    }

    fun setDayTime(day: Long, time: Int) {
        selectedDay = day
        selectedTime = time
        autoCommit()
    }

    fun clearDate() {
        selectedDay = NO_DAY
        selectedTime = NO_TIME
        autoCommit()
    }
}

@Composable
internal fun rememberDateSheetState(
    initialDay: Long,
    initialTime: Int,
    autoClose: Boolean,
    onSelected: (day: Long, time: Int) -> Unit,
    onDismiss: () -> Unit,
): DateSheetState {
    val currentOnSelected by rememberUpdatedState(onSelected)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    fun build() = DateSheetState(
        initialDay = initialDay,
        initialTime = initialTime,
        autoClose = autoClose,
        onSelected = { day, time -> currentOnSelected(day, time) },
        onDismiss = { currentOnDismiss() },
    )
    return rememberSaveable(
        saver = listSaver(
            save = { listOf(it.selectedDay, it.selectedTime.toLong()) },
            restore = { saved ->
                build().apply {
                    selectedDay = saved[0]
                    selectedTime = saved[1].toInt()
                }
            },
        ),
    ) {
        build()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetTimeShortcuts(
    day: Long,
    selectedTime: Int,
    is24Hour: Boolean,
    times: QuickPickTimes,
    applyTime: (Int) -> Unit,
    clearTime: () -> Unit,
    showNoTime: Boolean = true,
) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    QuickTimePicker(
        visible = showTimePicker,
        selectedTime = selectedTime,
        is24Hour = is24Hour,
        onSelected = { applyTime(it + TIME_MARKER) },
        onDismiss = { showTimePicker = false },
    )
    TimeShortcuts(
        day = day,
        selected = selectedTime,
        morning = times.morning + TIME_MARKER,
        afternoon = times.afternoon + TIME_MARKER,
        evening = times.evening + TIME_MARKER,
        night = times.night + TIME_MARKER,
        is24HourFormat = is24Hour,
        selectedMillisOfDay = { applyTime(it) },
        pickTime = { showTimePicker = true },
        clearTime = clearTime,
        showNoTime = showNoTime,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSyncedDatePickerState(
    selectedDay: Long,
    moveDisplayedMonth: Boolean,
    onDaySelected: (Long) -> Unit,
): DatePickerState {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDay.takeIf { it > 0 }?.toUtcDateMillis(),
    )
    val onDaySelectedState by rememberUpdatedState(onDaySelected)
    // Ref, not state: flipping a MutableState here re-entered composition and fought the picker.
    val suppressOut = remember { booleanArrayOf(false) }
    LaunchedEffect(selectedDay) {
        if (selectedDay > 0) {
            val utc = selectedDay.toUtcDateMillis()
            if (datePickerState.selectedDateMillis != utc) {
                suppressOut[0] = true
                if (moveDisplayedMonth) datePickerState.displayedMonthMillis = utc
                datePickerState.selectedDateMillis = utc
            }
        } else if (datePickerState.selectedDateMillis != null) {
            suppressOut[0] = true
            datePickerState.selectedDateMillis = null
        }
    }
    LaunchedEffect(datePickerState.selectedDateMillis) {
        if (suppressOut[0]) {
            suppressOut[0] = false
            return@LaunchedEffect
        }
        val utc = datePickerState.selectedDateMillis
        if (selectedDay > 0 && utc == selectedDay.toUtcDateMillis()) return@LaunchedEffect
        utc?.let { onDaySelectedState(it.toLocalDateMillis()) }
    }
    return datePickerState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickTimePicker(
    visible: Boolean,
    selectedTime: Int,
    is24Hour: Boolean,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val (hour, minute) = dialTime(selectedTime)
    TimePickerDialog(
        state = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = is24Hour,
        ),
        selected = onSelected,
        dismiss = onDismiss,
    )
}
