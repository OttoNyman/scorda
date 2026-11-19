package org.tasks.compose.settings

import org.tasks.constructor.flags
import org.tasks.themes.TasksIcons
import org.tasks.compose.components.SymbolIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.tasks.data.entity.Task
import org.tasks.preferences.TaskDefaultSettings
import tasks.kmp.generated.resources.EPr_default_importance_title
import tasks.kmp.generated.resources.EPr_default_location_reminder_title
import tasks.kmp.generated.resources.EPr_default_reminders_mode_title
import tasks.kmp.generated.resources.EPr_default_reminders_title
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.TEA_control_location
import tasks.kmp.generated.resources.TEA_control_reminders
import tasks.kmp.generated.resources.TEA_control_repeat
import tasks.kmp.generated.resources.day_before_due
import tasks.kmp.generated.resources.default_list
import tasks.kmp.generated.resources.default_location
import tasks.kmp.generated.resources.default_location_reminder_on_arrival
import tasks.kmp.generated.resources.default_location_reminder_on_arrival_or_departure
import tasks.kmp.generated.resources.default_location_reminder_on_departure
import tasks.kmp.generated.resources.default_recurrence
import tasks.kmp.generated.resources.default_start_date
import tasks.kmp.generated.resources.default_tags
import tasks.kmp.generated.resources.due_date
import tasks.kmp.generated.resources.due_time
import tasks.kmp.generated.resources.location_update_interval_15_minutes
import tasks.kmp.generated.resources.location_update_interval_1_hour
import tasks.kmp.generated.resources.location_update_interval_2_hours
import tasks.kmp.generated.resources.location_update_interval_30_minutes
import tasks.kmp.generated.resources.location_update_interval_disabled
import tasks.kmp.generated.resources.location_update_interval_title
import tasks.kmp.generated.resources.no_reminders
import tasks.kmp.generated.resources.no_start_date
import tasks.kmp.generated.resources.none
import tasks.kmp.generated.resources.priority_high
import tasks.kmp.generated.resources.priority_low
import tasks.kmp.generated.resources.priority_medium
import tasks.kmp.generated.resources.repeat_type_completion_capitalized
import tasks.kmp.generated.resources.repeats_from
import tasks.kmp.generated.resources.week_before_due

val PriorityOptions: List<Pair<Int, StringResource>> = listOf(
    Task.Priority.HIGH to Res.string.priority_high,
    Task.Priority.MEDIUM to Res.string.priority_medium,
    Task.Priority.LOW to Res.string.priority_low,
    Task.Priority.NONE to Res.string.none,
)

val StartDateOptions: List<Pair<Int, StringResource>> = listOf(
    Task.HIDE_UNTIL_NONE to Res.string.no_start_date,
    Task.HIDE_UNTIL_DUE to Res.string.due_date,
    Task.HIDE_UNTIL_DUE_TIME to Res.string.due_time,
    Task.HIDE_UNTIL_DAY_BEFORE to Res.string.day_before_due,
    Task.HIDE_UNTIL_WEEK_BEFORE to Res.string.week_before_due,
)

val RepeatFromOptions: List<Pair<Int, StringResource>> = listOf(
    Task.RepeatFrom.DUE_DATE to Res.string.due_date,
    Task.RepeatFrom.COMPLETION_DATE to Res.string.repeat_type_completion_capitalized,
)

val LocationReminderOptions: List<Pair<Int, StringResource>> = listOf(
    0 to Res.string.no_reminders,
    1 to Res.string.default_location_reminder_on_arrival,
    2 to Res.string.default_location_reminder_on_departure,
    3 to Res.string.default_location_reminder_on_arrival_or_departure,
)

val LocationUpdateIntervalOptions: List<Pair<Int, StringResource>> = listOf(
    0 to Res.string.location_update_interval_disabled,
    15 to Res.string.location_update_interval_15_minutes,
    30 to Res.string.location_update_interval_30_minutes,
    60 to Res.string.location_update_interval_1_hour,
    120 to Res.string.location_update_interval_2_hours,
)

@Composable
private fun List<Pair<Int, StringResource>>.label(value: Int): String =
    stringResource(firstOrNull { it.first == value }?.second ?: first().second)

@Composable
fun TaskDefaultsScreen(
    settings: TaskDefaultSettings,
    defaultListName: String,
    features: org.tasks.constructor.Constructor = org.tasks.constructor.Preset.Full.flags,
    onDefaultList: () -> Unit,
    onStartDate: () -> Unit,
    bottomInsets: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))

        CardGroup(
            listOfNotNull(
                DefaultRow.LIST.takeIf { features.lists },
                DefaultRow.START_DATE.takeIf { features.startDate },
            )
        ) { row ->
            when (row) {
                DefaultRow.LIST ->
                    PreferenceRow(
                        title = stringResource(Res.string.default_list),
                        icon = TasksIcons.LIST,
                        summary = defaultListName,
                        onClick = onDefaultList,
                    )
                DefaultRow.START_DATE ->
                    PreferenceRow(
                        title = stringResource(Res.string.default_start_date),
                        icon = TasksIcons.PENDING_ACTIONS,
                        summary = StartDateOptions.label(settings.defaultHideUntil),
                        onClick = onStartDate,
                    )
                else -> Unit
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        bottomInsets()
    }
}

private enum class DefaultRow {
    LIST,
    START_DATE,
}

@Composable
private fun <T : Any> CardGroup(rows: List<T>, row: @Composable (T) -> Unit) {
    if (rows.isEmpty()) return
    Column(
        modifier = Modifier.padding(horizontal = SettingsContentPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
    ) {
        rows.forEachIndexed { index, item ->
            key(item) {
                SettingsItemCard(position = CardPosition.forIndex(index, rows.size)) {
                    row(item)
                }
            }
        }
    }
}

@Composable
fun ListPreferenceDialog(
    title: String,
    options: List<Pair<Int, StringResource>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = value == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(value)
                                    onDismiss()
                                },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = null,
                        )
                        Text(
                            text = stringResource(label),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}
