package org.tasks.compose.edit

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.tasks.compose.PlatformBackHandler
import org.tasks.compose.settings.ConfirmDialog
import tasks.kmp.generated.resources.DLG_delete_this_task_question
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.cancel
import tasks.kmp.generated.resources.convert_to_memo_google
import tasks.kmp.generated.resources.convert_to_memo_message
import tasks.kmp.generated.resources.convert_to_memo_title
import tasks.kmp.generated.resources.convert_to_task_message
import tasks.kmp.generated.resources.convert_to_task_title
import tasks.kmp.generated.resources.discard
import tasks.kmp.generated.resources.discard_confirmation
import tasks.kmp.generated.resources.keep_editing
import tasks.kmp.generated.resources.ok

@Composable
fun DiscardChangesDialog(
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onDismiss)
    ConfirmDialog(
        text = stringResource(Res.string.discard_confirmation),
        confirmText = stringResource(Res.string.discard),
        dismissText = stringResource(Res.string.keep_editing),
        onConfirm = onDiscard,
        onDismiss = onDismiss,
    )
}

@Composable
fun DeleteTaskDialog(
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onDismiss)
    ConfirmDialog(
        text = stringResource(Res.string.DLG_delete_this_task_question),
        confirmText = stringResource(Res.string.ok),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = onDelete,
        onDismiss = onDismiss,
    )
}

@Composable
fun ConvertToMemoDialog(
    hasGoogleList: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val body = buildString {
        append(stringResource(Res.string.convert_to_memo_message))
        if (hasGoogleList) {
            append("\n\n")
            append(stringResource(Res.string.convert_to_memo_google))
        }
    }
    PlatformBackHandler(enabled = true, onBack = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.convert_to_memo_title)) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
fun ConvertToTaskDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.convert_to_task_title)) },
        text = { Text(stringResource(Res.string.convert_to_task_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
