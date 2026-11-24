package org.tasks.compose.edit

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.tasks.R
import org.tasks.compose.TaskEditRow
import org.tasks.data.entity.Task
import org.tasks.kmp.org.tasks.themes.ColorProvider.priorityColor
import org.tasks.notifications.AlertLevel
import org.tasks.notifications.alertLevelOf
import org.tasks.notifications.effectiveAlertLevel
import org.tasks.notifications.enabledLevels
import org.tasks.notifications.prioritiesOf
import org.jetbrains.compose.resources.stringResource
import org.tasks.themes.TasksTheme
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.alert_caption_alarm
import tasks.kmp.generated.resources.alert_caption_once
import tasks.kmp.generated.resources.alert_caption_persistent
import tasks.kmp.generated.resources.alert_caption_silent
import tasks.kmp.generated.resources.priority_sort_day
import tasks.kmp.generated.resources.priority_sort_undated

@Composable
fun PriorityRow(
    priority: Int,
    onChangePriority: (Int) -> Unit,
    enabledPriorities: Set<Int> = allPriorities,
    caption: PriorityCaption = PriorityCaption.Alert,
) {
    TaskEditRow(
        iconRes = R.drawable.ic_outline_flag_24px,
        content = {
            PriorityLabeled(
                selected = priority,
                onClick = { onChangePriority(it) },
                enabledPriorities = enabledPriorities,
                caption = caption,
            )
        },
    )
}

@Composable
fun Priority(
    selected: Int,
    onClick: (Int) -> Unit = {},
    enabledPriorities: Set<Int> = allPriorities,
) {
    Row(horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in Task.Priority.NONE downTo Task.Priority.HIGH) {
            // Keep the current value visible even when its level is hidden.
            if (i !in enabledPriorities && i != selected) continue
            PriorityButton(
                priority = i,
                selected = selected,
                onClick = onClick,
            )
        }
    }
}

@Composable
fun PriorityLabeled(
    selected: Int,
    onClick: (Int) -> Unit = {},
    enabledPriorities: Set<Int> = allPriorities,
    caption: PriorityCaption = PriorityCaption.None,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(end = dimensionResource(id = R.dimen.keyline_first)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = R.string.TEA_importance_label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(end = 16.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            Priority(selected = selected, onClick = onClick, enabledPriorities = enabledPriorities)
        }
        if (caption != PriorityCaption.None) {
            Text(
                text = priorityCaption(caption, selected, enabledPriorities),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

enum class PriorityCaption { None, Day, Undated, Alert }

@Composable
private fun priorityCaption(
    caption: PriorityCaption,
    priority: Int,
    enabledPriorities: Set<Int>,
): String = when (caption) {
    PriorityCaption.None -> ""
    PriorityCaption.Day -> stringResource(Res.string.priority_sort_day)
    PriorityCaption.Undated -> stringResource(Res.string.priority_sort_undated)
    PriorityCaption.Alert -> alertCaption(priority, enabledPriorities)
}

@Composable
private fun alertCaption(priority: Int, enabledPriorities: Set<Int>): String {
    val enabledLevels = enabledPriorities.map { alertLevelOf(it) }.toSet()
    return when (effectiveAlertLevel(priority, enabledLevels)) {
        AlertLevel.SILENT -> stringResource(Res.string.alert_caption_silent)
        AlertLevel.ONCE -> stringResource(Res.string.alert_caption_once)
        AlertLevel.PERSISTENT -> stringResource(Res.string.alert_caption_persistent)
        AlertLevel.SUPER -> stringResource(Res.string.alert_caption_alarm)
    }
}

@Composable
fun RowScope.PriorityButton(
    @Task.Priority priority: Int,
    selected: Int,
    onClick: (Int) -> Unit,
) {
    val color = Color(
        priorityColor(
            priority = priority,
            isDarkMode = isSystemInDarkTheme(),
        )
    )
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        RadioButton(
            selected = priority == selected,
            onClick = { onClick(priority) },
            colors = RadioButtonDefaults.colors(
                selectedColor = color,
                unselectedColor = color,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 20.dp)
        )
    }
}

val allPriorities: Set<Int> =
    setOf(Task.Priority.HIGH, Task.Priority.MEDIUM, Task.Priority.LOW, Task.Priority.NONE)

@ExperimentalComposeUiApi
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PriorityPreview() {
    TasksTheme {
        PriorityRow(
            priority = Task.Priority.MEDIUM,
            onChangePriority = {},
            enabledPriorities = prioritiesOf(enabledLevels(alarm = true)),
        )
    }
}
