package org.tasks.compose.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import org.tasks.compose.pickers.advanceLabel
import org.tasks.reminders.ADVANCE_CHOICES
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.advance

@Composable
fun AdvanceRow(
    offset: Long?,
    onPick: (Long?) -> Unit,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    TaskEditRow(
        iconRes = R.drawable.ic_outline_notifications_24px,
        content = {
            Box {
                Text(
                    text = "${stringResource(Res.string.advance)}: ${advanceLabel(offset)}",
                    color = if (offset == null) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.disabled)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(top = 20.dp, bottom = 20.dp, end = 16.dp),
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    (listOf<Long?>(null) + ADVANCE_CHOICES).forEach { choice ->
                        val selected = offset == choice || (offset == null && choice == null)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    buildString {
                                        if (selected) append("✓ ")
                                        append(advanceLabel(choice))
                                    },
                                )
                            },
                            onClick = {
                                menu = false
                                onPick(choice)
                            },
                        )
                    }
                }
            }
        },
        onClick = { menu = true },
    )
}
