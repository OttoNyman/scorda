package org.tasks.compose.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tasks.R
import org.tasks.Strings.isNullOrEmpty
import org.tasks.compose.TaskEditRow
import org.tasks.compose.rememberRelativeDateTime
import org.tasks.data.Location
import org.tasks.data.displayName
import org.tasks.data.open
import org.tasks.extensions.Context.openUri
import org.tasks.kmp.org.tasks.time.DateStyle
import org.tasks.time.dueDateOverdue

@Composable
fun WhenRow(
    dueDate: Long,
    hasDueDateAlarm: Boolean,
    is24HourFormat: Boolean,
    alwaysDisplayFullDate: Boolean,
    location: Location?,
    onSwitchToPlace: () -> Unit,
    onSwitchToTime: () -> Unit,
    onEditDueDate: () -> Unit,
    onChangePlace: () -> Unit,
    onGeofence: () -> Unit,
) {
    val place = location != null
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val overdue = remember(dueDate) { dueDateOverdue(dueDate) }
    val dueText = if (dueDate == 0L) {
        stringResource(R.string.no_due_date)
    } else {
        rememberRelativeDateTime(
            dueDate,
            is24HourFormat,
            DateStyle.FULL,
            alwaysDisplayFullDate = alwaysDisplayFullDate,
        )
    }
    val dueColor = when {
        overdue -> MaterialTheme.colorScheme.error
        dueDate == 0L && hasDueDateAlarm -> MaterialTheme.colorScheme.error
        dueDate == 0L -> MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.disabled)
        else -> MaterialTheme.colorScheme.onSurface
    }
    TaskEditRow(
        icon = {
            Row(
                modifier = Modifier.padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeIcon(
                    icon = R.drawable.ic_outline_schedule_24px,
                    selected = !place,
                    description = stringResource(R.string.when_switch_to_time),
                    onClick = { if (place) onSwitchToTime() },
                )
                ModeIcon(
                    icon = R.drawable.ic_outline_place_24px,
                    selected = place,
                    description = stringResource(R.string.when_switch_to_place),
                    onClick = { if (!place) onSwitchToPlace() },
                )
            }
        },
        content = {
            Box {
                if (location == null) {
                    DueDate(dueDate = dueText, color = dueColor)
                } else {
                    Location(
                        name = location.displayName,
                        address = location.displayAddress,
                        geofenceOn = location.geofence.isArrival || location.geofence.isDeparture,
                        openGeofenceOptions = onGeofence,
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (location != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open_map)) },
                            onClick = {
                                menu = false
                                location.open(context)
                            },
                        )
                        if (!isNullOrEmpty(location.phone)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_call)) },
                                onClick = {
                                    menu = false
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_DIAL,
                                            android.net.Uri.parse("tel:" + location.phone),
                                        )
                                    )
                                },
                            )
                        }
                        if (!isNullOrEmpty(location.url)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.visit_website)) },
                                onClick = {
                                    menu = false
                                    context.openUri(location.url)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.choose_new_location)) },
                            onClick = {
                                menu = false
                                onChangePlace()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                menu = false
                                onSwitchToTime()
                            },
                        )
                    }
                }
            }
        },
        onClick = {
            if (location == null) onEditDueDate() else menu = true
        },
    )
}

@Composable
private fun ModeIcon(
    icon: Int,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .background(background, CircleShape),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = tint,
        )
    }
}
