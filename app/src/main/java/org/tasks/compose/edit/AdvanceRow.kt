package org.tasks.compose.edit

import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tasks.R
import org.tasks.compose.TaskEditRow
import org.tasks.compose.pickers.AdvancePicker
import org.tasks.compose.pickers.advanceLabel
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.advance

/** "In advance: 15 min before" — the one extra ring before the reminder time. */
@Composable
fun AdvanceRow(
    offset: Long?,
    onPick: (Long?) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    TaskEditRow(
        iconRes = R.drawable.ic_outline_notifications_24px,
        content = {
            Text(
                text = "${stringResource(Res.string.advance)}: ${advanceLabel(offset)}",
                color = if (offset == null) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.disabled)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(top = 20.dp, bottom = 20.dp, end = 16.dp),
            )
        },
        onClick = { picking = true },
    )
    if (picking) {
        AdvancePicker(
            selected = offset,
            onPick = {
                picking = false
                onPick(it)
            },
            onDismiss = { picking = false },
        )
    }
}
