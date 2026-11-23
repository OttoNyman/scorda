package org.tasks.compose.edit

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tasks.R
import org.tasks.data.entity.Task

/** «О задаче»: only what the database already keeps about the task, no change history. */
@Composable
fun TaskInfoDialog(
    task: Task,
    seriesCreated: Long?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val format: (Long) -> String = {
        DateUtils.formatDateTime(
            context,
            it,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME,
        )
    }
    val rows = listOfNotNull(
        task.creationDate.takeIf { it > 0 }?.let { R.string.task_info_created to it },
        task.modificationDate.takeIf { it > 0 }?.let { R.string.task_info_modified to it },
        task.completionDate.takeIf { it > 0 }?.let { R.string.task_info_completed to it },
        task.deletionDate.takeIf { it > 0 }?.let { R.string.task_info_deleted to it },
        task.pinned.takeIf { it > 0 }?.let { R.string.task_info_pinned to it },
        task.reminderLast.takeIf { it > 0 }?.let { R.string.task_info_reminded to it },
        seriesCreated?.takeIf { it > 0 }?.let { R.string.task_info_series to it },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { (label, time) ->
                    Column {
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = format(time), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}
