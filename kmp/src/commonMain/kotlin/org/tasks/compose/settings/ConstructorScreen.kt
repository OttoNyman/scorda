package org.tasks.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.tasks.constructor.Constructor
import org.tasks.constructor.ConstructorCounts
import org.tasks.constructor.Preset
import org.tasks.compose.pickers.AdvancePicker
import org.tasks.compose.pickers.advanceLabel
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.advance
import tasks.kmp.generated.resources.advance_default
import tasks.kmp.generated.resources.cancel
import tasks.kmp.generated.resources.constructor_all_off
import tasks.kmp.generated.resources.constructor_all_off_confirm
import tasks.kmp.generated.resources.constructor_all_on
import tasks.kmp.generated.resources.constructor_all_on_confirm
import tasks.kmp.generated.resources.constructor_comments
import tasks.kmp.generated.resources.constructor_files
import tasks.kmp.generated.resources.constructor_filters
import tasks.kmp.generated.resources.constructor_geo
import tasks.kmp.generated.resources.constructor_geo_count
import tasks.kmp.generated.resources.constructor_group_content
import tasks.kmp.generated.resources.constructor_group_memos
import tasks.kmp.generated.resources.constructor_group_order
import tasks.kmp.generated.resources.constructor_group_time
import tasks.kmp.generated.resources.constructor_memos
import tasks.kmp.generated.resources.constructor_lists
import tasks.kmp.generated.resources.constructor_local_lists
import tasks.kmp.generated.resources.constructor_local_lists_summary
import tasks.kmp.generated.resources.constructor_markdown
import tasks.kmp.generated.resources.constructor_markdown_summary
import tasks.kmp.generated.resources.constructor_notes
import tasks.kmp.generated.resources.constructor_overdue
import tasks.kmp.generated.resources.constructor_overdue_count
import tasks.kmp.generated.resources.constructor_overdue_summary
import tasks.kmp.generated.resources.constructor_priority
import tasks.kmp.generated.resources.constructor_repeat
import tasks.kmp.generated.resources.constructor_repeat_count
import tasks.kmp.generated.resources.constructor_subtasks
import tasks.kmp.generated.resources.constructor_tags
import tasks.kmp.generated.resources.move_lists_keep
import tasks.kmp.generated.resources.move_lists_move
import tasks.kmp.generated.resources.move_lists_text
import tasks.kmp.generated.resources.move_lists_title
import tasks.kmp.generated.resources.move_target_same
import tasks.kmp.generated.resources.move_target_same_summary
import tasks.kmp.generated.resources.move_target_title
import tasks.kmp.generated.resources.ok

@Composable
fun ConstructorScreen(
    raw: Constructor,
    effective: Constructor,
    sync: Boolean,
    geofences: Boolean,
    counts: ConstructorCounts,
    markdown: Boolean,
    comments: Boolean,
    onPreset: (Preset) -> Unit,
    onChange: (Constructor) -> Unit,
    onMarkdown: (Boolean) -> Unit,
    onComments: (Boolean) -> Unit,
    /** The advance new tasks get; null for none. */
    advanceDefault: Long?,
    onAdvanceDefault: (Long?) -> Unit,
) {
    var pending by remember { mutableStateOf<Preset?>(null) }
    var pickAdvance by remember { mutableStateOf(false) }
    if (pickAdvance) {
        AdvancePicker(
            selected = advanceDefault,
            onPick = {
                pickAdvance = false
                onAdvanceDefault(it)
            },
            onDismiss = { pickAdvance = false },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SettingsContentPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Preset.entries.forEachIndexed { index, preset ->
            SettingsItemCard(position = CardPosition.forIndex(index, Preset.entries.size)) {
                PreferenceRow(
                    title = stringResource(preset.label),
                    onClick = { pending = preset },
                )
            }
        }
        // What a task can carry: everything below lives inside one task.
        Group(Res.string.constructor_group_content)
        Toggle(Res.string.constructor_notes, effective.notes) { onChange(raw.copy(notes = it)) }
        if (effective.notes) {
            Toggle(
                title = Res.string.constructor_markdown,
                checked = markdown,
                summary = stringResource(Res.string.constructor_markdown_summary),
                summaryWhenOff = true,
                nested = true,
                onChange = onMarkdown,
            )
            Toggle(
                title = Res.string.constructor_comments,
                checked = comments,
                nested = true,
                onChange = onComments,
            )
        }
        Toggle(Res.string.constructor_subtasks, effective.subtasks) { onChange(raw.copy(subtasks = it)) }
        // a repeat is part of the task (its schedule), not a reminder setting
        Toggle(
            title = Res.string.constructor_repeat,
            checked = effective.repeat,
            summary = count(counts.repeat, Res.plurals.constructor_repeat_count),
        ) { onChange(raw.copy(repeat = it)) }
        Toggle(Res.string.constructor_priority, effective.priority) { onChange(raw.copy(priority = it)) }
        Toggle(Res.string.constructor_tags, effective.tags) { onChange(raw.copy(tags = it)) }
        Toggle(Res.string.constructor_files, effective.files) { onChange(raw.copy(files = it)) }
        if (geofences) {
            Toggle(
                title = Res.string.constructor_geo,
                checked = effective.geo,
                summary = count(counts.geo, Res.plurals.constructor_geo_count),
            ) { onChange(raw.copy(geo = it)) }
        }
        Group(Res.string.constructor_group_time)
        Toggle(
            title = Res.string.constructor_overdue,
            checked = effective.overdue,
            // what it does, always; how many tasks have it, while on
            summary = listOfNotNull(
                stringResource(Res.string.constructor_overdue_summary),
                count(counts.overdue, Res.plurals.constructor_overdue_count).takeIf { effective.overdue },
            ).joinToString("\n"),
            summaryWhenOff = true,
        ) { onChange(raw.copy(overdue = it)) }
        // no start date and no "more reminders": one time per task, plus "in advance"
        Toggle(Res.string.advance, effective.advance) { onChange(raw.copy(advance = it)) }
        if (effective.advance) {
            SettingsItemCard(modifier = Modifier.padding(start = SettingsContentPadding)) {
                PreferenceRow(
                    title = stringResource(Res.string.advance_default),
                    summary = advanceLabel(advanceDefault),
                    onClick = { pickAdvance = true },
                )
            }
        }
        // How tasks are organised. Later: notes, Eisenhower matrix — each as its own group.
        Group(Res.string.constructor_group_order)
        Toggle(Res.string.constructor_lists, effective.lists) { onChange(raw.copy(lists = it)) }
        if (sync && raw.lists) {
            Toggle(
                title = Res.string.constructor_local_lists,
                checked = raw.localLists,
                summary = stringResource(Res.string.constructor_local_lists_summary),
                summaryWhenOff = true,
                nested = true,
            ) { onChange(raw.copy(localLists = it)) }
        }
        Toggle(Res.string.constructor_filters, effective.filters) { onChange(raw.copy(filters = it)) }
        Group(Res.string.constructor_group_memos)
        Toggle(Res.string.constructor_memos, effective.memos) { onChange(raw.copy(memos = it)) }
        Spacer(modifier = Modifier.height(SettingsContentPadding))
    }
    pending?.let { preset ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(preset.label)) },
            text = { Text(stringResource(preset.confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        onPreset(preset)
                    },
                ) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

private val Preset.label: StringResource
    get() = when (this) {
        Preset.Simple -> Res.string.constructor_all_off
        Preset.Full -> Res.string.constructor_all_on
    }

private val Preset.confirm: StringResource
    get() = when (this) {
        Preset.Simple -> Res.string.constructor_all_off_confirm
        Preset.Full -> Res.string.constructor_all_on_confirm
    }

@Composable
private fun Group(title: StringResource) {
    SectionHeader(stringResource(title))
}

@Composable
private fun Toggle(
    title: StringResource,
    checked: Boolean,
    enabled: Boolean = true,
    summary: String? = null,
    summaryWhenOff: Boolean = false,
    nested: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    SettingsItemCard(modifier = if (nested) Modifier.padding(start = SettingsContentPadding) else Modifier) {
        SwitchPreferenceRow(
            title = stringResource(title),
            checked = checked,
            enabled = enabled,
            summary = summary.takeIf { checked || summaryWhenOff },
            onCheckedChange = onChange,
        )
    }
}

@Composable
private fun count(value: Int, resource: org.jetbrains.compose.resources.PluralStringResource): String? =
    if (value > 0) pluralStringResource(resource, value, value) else null

/**
 * Asks where local lists go when the local block is turned off while syncing.
 * [targets] are Google lists as uuid to name; picking null keeps each list under its own name.
 */
@Composable
fun MoveTargetDialog(
    lists: Int,
    targets: List<Pair<String, String>>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    dismissText: String,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.move_target_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(Res.string.move_lists_text, lists))
                Spacer(modifier = Modifier.height(SettingsContentPadding))
                targets.forEach { (uuid, name) ->
                    PreferenceRow(title = name, onClick = { onPick(uuid) })
                }
                PreferenceRow(
                    title = stringResource(Res.string.move_target_same),
                    summary = stringResource(Res.string.move_target_same_summary),
                    onClick = { onPick(null) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        },
    )
}

/** Warns that local lists move to Google. [onKeep] adds a "keep on this phone" choice. */
@Composable
fun MoveListsDialog(
    lists: Int,
    onMove: () -> Unit,
    onDismiss: () -> Unit,
    onKeep: (() -> Unit)? = null,
    dismissText: String,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.move_lists_title)) },
        text = { Text(stringResource(Res.string.move_lists_text, lists)) },
        confirmButton = {
            TextButton(onClick = onMove) { Text(stringResource(Res.string.move_lists_move)) }
        },
        dismissButton = {
            if (onKeep != null) {
                TextButton(onClick = onKeep) { Text(stringResource(Res.string.move_lists_keep)) }
            } else {
                TextButton(onClick = onDismiss) { Text(dismissText) }
            }
        },
    )
}
