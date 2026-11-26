package org.tasks.compose.pickers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.platform.testTag
import org.tasks.themes.TestTags
import org.tasks.themes.exposeTestTags
import org.tasks.compose.PlatformBackHandler
import org.tasks.previews.PREVIEW_NIGHT_MODE
import org.tasks.themes.TasksTheme
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.cancel
import tasks.kmp.generated.resources.no_date
import tasks.kmp.generated.resources.ok

/**
 * Calendar-only date sheet. [headline] goes above the calendar, [content] below it; the bottom bar
 * holds "No date" on the left when [noDate] is set and Cancel/OK on the right when [showButtons].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerBottomSheet(
    sheetState: SheetState,
    state: DatePickerState,
    showButtons: Boolean,
    cancel: () -> Unit,
    accept: () -> Unit,
    headline: (@Composable () -> Unit)? = null,
    noDate: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    PlatformBackHandler(enabled = true, onBack = accept)
    val showBar = showButtons || noDate != null
    ModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        sheetState = sheetState,
        onDismissRequest = { accept() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .exposeTestTags()
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
            ) {
                DatePicker(
                    state = state,
                    showModeToggle = false,
                    title = if (headline != null) { {} } else null,
                    colors = DatePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    headline = headline,
                )
                content()
                if (showBar) {
                    Spacer(modifier = Modifier.height(56.dp))
                }
            }
            if (showBar) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        noDate?.let {
                            TextButton(onClick = it) {
                                Text(stringResource(Res.string.no_date))
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (showButtons) {
                            TextButton(
                                onClick = { cancel() }
                            ) {
                                Text(stringResource(Res.string.cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { accept() },
                                modifier = Modifier.testTag(TestTags.DATE_PICKER_OK),
                            ) {
                                Text(stringResource(Res.string.ok))
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MORNING = DEFAULT_MORNING
private const val EVENING = DEFAULT_EVENING

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Due date picker - Light")
@Preview(name = "Due date picker - Dark", uiMode = PREVIEW_NIGHT_MODE)
@Composable
private fun DueDatePickerPreview() {
    TasksTheme {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true).apply {
            runBlocking { show() }
        }
        DatePickerBottomSheet(
            sheetState = sheetState,
            state = rememberDatePickerState(),
            showButtons = true,
            cancel = {},
            accept = {},
            noDate = {},
        ) {
            DueTimePicker(
                selectedTime = { NO_TIME },
                is24Hour = true,
                times = QuickPickTimes(),
                showNoTime = true,
                showMultiple = false,
                onDialTime = {},
                onChipTime = {},
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Start date picker - Light")
@Preview(name = "Start date picker - Dark", uiMode = PREVIEW_NIGHT_MODE)
@Composable
private fun StartDatePickerPreview() {
    TasksTheme {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true).apply {
            runBlocking { show() }
        }
        DatePickerBottomSheet(
            sheetState = sheetState,
            state = rememberDatePickerState(),
            showButtons = true,
            cancel = {},
            accept = {},
            headline = {
                DatePickerShortcuts(
                    dateShortcuts = {
                        StartDateShortcuts(
                            selected = DUE_DATE,
                            selectedDay = {},
                            selectedDayTime = { _, _ -> },
                            showDueDate = true,
                            clearDate = {},
                        )
                    },
                    timeShortcuts = {
                        TimeShortcuts(
                            day = DUE_DATE,
                            selected = NO_TIME,
                            morning = MORNING,
                            evening = EVENING,
                            is24HourFormat = true,
                            selectedMillisOfDay = {},
                            pickTime = {},
                            clearTime = {},
                        )
                    },
                )
            },
        )
    }
}
