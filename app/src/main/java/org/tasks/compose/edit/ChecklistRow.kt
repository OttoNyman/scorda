package org.tasks.compose.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.tasks.R
import org.tasks.checklist.Checklist
import org.tasks.compose.ClearButton
import org.tasks.compose.DisabledText
import org.tasks.compose.TaskEditRow
import org.tasks.compose.components.SymbolIcon
import org.tasks.themes.TasksIcons

/** A row while the editor is open; the id keeps focus and drag on the row, not on its index. */
private class Line(text: String, checked: Boolean, val id: Long) {
    var value by mutableStateOf(TextFieldValue(text))
    var checked by mutableStateOf(checked)
    var height = 0
    val focus = FocusRequester()
}

/**
 * The checklist block of the editor. Rows live here while the editor is open and every change
 * goes back to the notes through [onChange]; the notes are read once, when the block appears.
 */
@Composable
fun ChecklistRow(
    notes: String?,
    onChange: (List<Checklist.Item>) -> Unit,
) {
    val ids = remember { generateSequence(0L) { it + 1 }.iterator() }
    val lines = remember {
        Checklist.parse(notes).items.map { Line(it.text, it.checked, ids.next()) }.toMutableStateList()
    }
    var focused by remember { mutableStateOf<Long?>(null) }
    var dragged by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val commit = { onChange(lines.map { Checklist.Item(it.value.text, it.checked) }) }
    LaunchedEffect(focused) {
        lines.firstOrNull { it.id == focused }?.focus?.requestFocus()
        focused = null
    }
    TaskEditRow(
        icon = {
            SymbolIcon(
                name = TasksIcons.CHECKLIST,
                contentDescription = null,
                modifier = Modifier
                    .alpha(ContentAlpha.medium)
                    .padding(start = 4.dp, top = 8.dp, end = 20.dp, bottom = 8.dp),
            )
        },
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                lines.forEach { line ->
                    key(line.id) {
                        val isDragged = dragged == line.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                                .background(MaterialTheme.colorScheme.background)
                                .onSizeChanged { line.height = it.height },
                        ) {
                            SymbolIcon(
                                name = TasksIcons.DRAG_INDICATOR,
                                contentDescription = stringResource(R.string.checklist_move_item),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .pointerInput(line.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragged = line.id
                                                dragOffset = 0f
                                            },
                                            onDragEnd = {
                                                dragged = null
                                                dragOffset = 0f
                                                commit()
                                            },
                                            onDragCancel = {
                                                dragged = null
                                                dragOffset = 0f
                                                commit()
                                            },
                                        ) { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            // swap past half of a neighbour; the offset keeps the row under the finger
                                            val i = lines.indexOf(line)
                                            val next = lines.getOrNull(i + 1)
                                            val previous = lines.getOrNull(i - 1)
                                            if (next != null && dragOffset > next.height / 2f) {
                                                lines.add(i + 1, lines.removeAt(i))
                                                dragOffset -= next.height
                                            } else if (previous != null && -dragOffset > previous.height / 2f) {
                                                lines.add(i - 1, lines.removeAt(i))
                                                dragOffset += previous.height
                                            }
                                        }
                                    },
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .toggleable(value = line.checked, role = Role.Checkbox) {
                                        line.checked = it
                                        commit()
                                    },
                            ) {
                                SymbolIcon(
                                    name = if (line.checked) TasksIcons.CHECK_CIRCLE else TasksIcons.RADIO_BUTTON_UNCHECKED,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    filled = line.checked,
                                )
                            }
                            BasicTextField(
                                value = line.value,
                                onValueChange = { value ->
                                    // Enter (or a pasted line break) starts the next item below
                                    val parts = value.text.split('\n')
                                    if (parts.size == 1) {
                                        line.value = value
                                    } else {
                                        line.value = TextFieldValue(parts.first(), TextRange(parts.first().length))
                                        val added = parts.drop(1).map { Line(it, false, ids.next()) }
                                        lines.addAll(lines.indexOf(line) + 1, added)
                                        focused = added.last().id
                                    }
                                    commit()
                                },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (line.checked) TextDecoration.LineThrough else TextDecoration.None,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier
                                    .weight(1f)
                                    .alpha(if (line.checked) ContentAlpha.disabled else ContentAlpha.high)
                                    .focusRequester(line.focus)
                                    .onPreviewKeyEvent { event ->
                                        // Backspace in an empty item removes it and goes back to the one above
                                        if (event.type != KeyEventType.KeyDown || event.key != Key.Backspace || line.value.text.isNotEmpty()) {
                                            return@onPreviewKeyEvent false
                                        }
                                        val i = lines.indexOf(line)
                                        lines.removeAt(i)
                                        lines.getOrNull(i - 1)?.let { previous ->
                                            previous.value = previous.value.copy(selection = TextRange(previous.value.text.length))
                                            focused = previous.id
                                        }
                                        commit()
                                        true
                                    },
                            )
                            ClearButton {
                                lines.remove(line)
                                commit()
                            }
                        }
                    }
                }
                DisabledText(
                    text = stringResource(R.string.checklist_add_item),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val line = Line("", false, ids.next())
                            lines.add(line)
                            focused = line.id
                            commit()
                        }
                        .padding(start = 72.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
                )
            }
        },
    )
}
