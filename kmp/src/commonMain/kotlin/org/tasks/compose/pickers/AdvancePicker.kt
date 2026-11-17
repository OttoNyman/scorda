package org.tasks.compose.pickers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tasks.reminders.ADVANCE_CHOICES
import org.tasks.reminders.durationString
import org.tasks.time.ONE_DAY
import org.tasks.time.ONE_HOUR
import org.tasks.time.ONE_MINUTE
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.advance
import tasks.kmp.generated.resources.advance_15m
import tasks.kmp.generated.resources.advance_1d
import tasks.kmp.generated.resources.advance_1h
import tasks.kmp.generated.resources.advance_2h
import tasks.kmp.generated.resources.advance_30m
import tasks.kmp.generated.resources.advance_5m
import tasks.kmp.generated.resources.advance_custom
import tasks.kmp.generated.resources.advance_none
import tasks.kmp.generated.resources.cancel

/** "15 min before" for one of [ADVANCE_CHOICES]; "None" for null. */
@Composable
fun advanceLabel(offset: Long?): String = when (offset) {
    null, 0L -> stringResource(Res.string.advance_none)
    5 * ONE_MINUTE -> stringResource(Res.string.advance_5m)
    15 * ONE_MINUTE -> stringResource(Res.string.advance_15m)
    30 * ONE_MINUTE -> stringResource(Res.string.advance_30m)
    ONE_HOUR -> stringResource(Res.string.advance_1h)
    2 * ONE_HOUR -> stringResource(Res.string.advance_2h)
    ONE_DAY -> stringResource(Res.string.advance_1d)
    // an offset made in "More reminders"
    else -> stringResource(Res.string.advance_custom, durationString(offset))
}

/** None plus [ADVANCE_CHOICES]; picking one closes the dialog through [onPick]. */
@Composable
fun AdvancePicker(
    selected: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.advance)) },
        text = {
            Column {
                (listOf<Long?>(null) + ADVANCE_CHOICES).forEach { offset ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(offset) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = selected == offset, onClick = { onPick(offset) })
                        Text(advanceLabel(offset))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
