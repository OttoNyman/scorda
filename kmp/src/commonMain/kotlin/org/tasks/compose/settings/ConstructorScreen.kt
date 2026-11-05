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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.tasks.constructor.Constructor
import org.tasks.constructor.ConstructorCounts
import org.tasks.constructor.Preset
import org.tasks.constructor.flags
import org.tasks.constructor.matchingPreset
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.constructor_calendar
import tasks.kmp.generated.resources.constructor_calendar_count
import tasks.kmp.generated.resources.constructor_extra
import tasks.kmp.generated.resources.constructor_extra_count
import tasks.kmp.generated.resources.constructor_files
import tasks.kmp.generated.resources.constructor_filters
import tasks.kmp.generated.resources.constructor_full
import tasks.kmp.generated.resources.constructor_geo
import tasks.kmp.generated.resources.constructor_geo_count
import tasks.kmp.generated.resources.constructor_group_content
import tasks.kmp.generated.resources.constructor_group_links
import tasks.kmp.generated.resources.constructor_group_order
import tasks.kmp.generated.resources.constructor_group_time
import tasks.kmp.generated.resources.constructor_lists
import tasks.kmp.generated.resources.constructor_lists_locked
import tasks.kmp.generated.resources.constructor_notes
import tasks.kmp.generated.resources.constructor_overdue
import tasks.kmp.generated.resources.constructor_overdue_count
import tasks.kmp.generated.resources.constructor_priority
import tasks.kmp.generated.resources.constructor_reminders
import tasks.kmp.generated.resources.constructor_repeat
import tasks.kmp.generated.resources.constructor_repeat_count
import tasks.kmp.generated.resources.constructor_simple
import tasks.kmp.generated.resources.constructor_start
import tasks.kmp.generated.resources.constructor_start_count
import tasks.kmp.generated.resources.constructor_subtasks
import tasks.kmp.generated.resources.constructor_tags
import tasks.kmp.generated.resources.constructor_tasks
import tasks.kmp.generated.resources.constructor_timer

@Composable
fun ConstructorScreen(
    raw: Constructor,
    effective: Constructor,
    hasSyncAccount: Boolean,
    geofences: Boolean,
    calendarEvents: Boolean,
    counts: ConstructorCounts,
    onPreset: (Preset) -> Unit,
    onChange: (Constructor) -> Unit,
) {
    val selected = matchingPreset(raw, hasSyncAccount, geofences, calendarEvents)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SettingsContentPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))
        PresetRow(Preset.Simple, selected, onPreset)
        PresetRow(Preset.Reminders, selected, onPreset)
        PresetRow(Preset.Tasks, selected, onPreset)
        PresetRow(Preset.Full, selected, onPreset)
        Group(Res.string.constructor_group_time)
        Toggle(
            title = Res.string.constructor_repeat,
            checked = effective.repeat,
            summary = count(counts.repeat, Res.plurals.constructor_repeat_count),
        ) { onChange(raw.copy(repeat = it)) }
        Toggle(
            title = Res.string.constructor_overdue,
            checked = effective.overdue,
            summary = count(counts.overdue, Res.plurals.constructor_overdue_count),
        ) { onChange(raw.copy(overdue = it)) }
        Toggle(
            title = Res.string.constructor_extra,
            checked = effective.extraReminders,
            summary = count(counts.extraReminders, Res.plurals.constructor_extra_count),
        ) { onChange(raw.copy(extraReminders = it)) }
        Toggle(
            title = Res.string.constructor_start,
            checked = effective.startDate,
            summary = count(counts.startDate, Res.plurals.constructor_start_count),
        ) { onChange(raw.copy(startDate = it)) }
        Group(Res.string.constructor_group_order)
        Toggle(
            title = Res.string.constructor_lists,
            checked = effective.lists,
            enabled = !hasSyncAccount,
            summary = if (hasSyncAccount && !raw.lists) {
                stringResource(Res.string.constructor_lists_locked)
            } else {
                null
            },
        ) { onChange(raw.copy(lists = it)) }
        Toggle(Res.string.constructor_tags, effective.tags) { onChange(raw.copy(tags = it)) }
        Toggle(Res.string.constructor_filters, effective.filters) { onChange(raw.copy(filters = it)) }
        Toggle(Res.string.constructor_subtasks, effective.subtasks) { onChange(raw.copy(subtasks = it)) }
        Toggle(Res.string.constructor_priority, effective.priority) { onChange(raw.copy(priority = it)) }
        Group(Res.string.constructor_group_content)
        Toggle(Res.string.constructor_notes, effective.notes) { onChange(raw.copy(notes = it)) }
        Toggle(Res.string.constructor_files, effective.files) { onChange(raw.copy(files = it)) }
        Group(Res.string.constructor_group_links)
        if (geofences) {
            Toggle(
                title = Res.string.constructor_geo,
                checked = effective.geo,
                summary = count(counts.geo, Res.plurals.constructor_geo_count),
            ) { onChange(raw.copy(geo = it)) }
        }
        if (calendarEvents) {
            Toggle(
                title = Res.string.constructor_calendar,
                checked = effective.calendar,
                summary = count(counts.calendar, Res.plurals.constructor_calendar_count),
            ) { onChange(raw.copy(calendar = it)) }
        }
        Toggle(Res.string.constructor_timer, effective.timer) { onChange(raw.copy(timer = it)) }
        Spacer(modifier = Modifier.height(SettingsContentPadding))
    }
}

@Composable
private fun PresetRow(
    preset: Preset,
    selected: Preset?,
    onPreset: (Preset) -> Unit,
) {
    SettingsItemCard {
        SwitchPreferenceRow(
            title = stringResource(preset.label),
            checked = selected == preset,
            onCheckedChange = { if (it) onPreset(preset) },
        )
    }
}

private val Preset.label: StringResource
    get() = when (this) {
        Preset.Simple -> Res.string.constructor_simple
        Preset.Reminders -> Res.string.constructor_reminders
        Preset.Tasks -> Res.string.constructor_tasks
        Preset.Full -> Res.string.constructor_full
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
    onChange: (Boolean) -> Unit,
) {
    SettingsItemCard {
        SwitchPreferenceRow(
            title = stringResource(title),
            checked = checked,
            enabled = enabled,
            summary = summary.takeIf { checked },
            onCheckedChange = onChange,
        )
    }
}

@Composable
private fun count(value: Int, resource: org.jetbrains.compose.resources.PluralStringResource): String? =
    if (value > 0) pluralStringResource(resource, value, value) else null
