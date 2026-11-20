package org.tasks.preferences.fragments

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.tasks.PlatformConfiguration
import org.tasks.R
import org.tasks.compose.settings.ConstructorScreen
import org.tasks.compose.settings.ListPreferenceDialog
import org.tasks.compose.settings.LocationReminderOptions
import org.tasks.compose.settings.LocationUpdateIntervalOptions
import org.tasks.compose.settings.MoveTargetDialog
import org.tasks.constructor.Constructor
import org.tasks.constructor.ConstructorCounts
import org.tasks.constructor.ConstructorRepository
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.Place
import org.tasks.location.LocationPickerActivity
import org.tasks.location.LocationPickerActivity.Companion.EXTRA_PLACE
import org.tasks.preferences.BasePreferences
import org.tasks.preferences.Preferences
import org.tasks.reminders.advance
import org.tasks.repeats.BasicRecurrenceDialog
import org.tasks.viewmodel.TaskDefaultsViewModel.ListPickerTarget
import org.tasks.reminders.forAdvanceToggle
import org.tasks.reminders.withAdvance
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import tasks.kmp.generated.resources.EPr_default_location_reminder_title
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.location_update_interval_title
import javax.inject.Inject

@AndroidEntryPoint
class ConstructorPreferences : Fragment() {

    @Inject lateinit var theme: Theme
    @Inject lateinit var repository: ConstructorRepository
    @Inject lateinit var configuration: PlatformConfiguration
    @Inject lateinit var preferences: Preferences

    private val defaults: TaskDefaultsHiltViewModel by viewModels()

    private val locationLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            defaults.setDefaultLocation(result.data?.getParcelableExtra<Place>(EXTRA_PLACE))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentManager.setFragmentResultListener(
            BasicRecurrenceDialog.REQUEST_KEY, this
        ) { _, bundle ->
            defaults.setRecurrence(bundle.getString(BasicRecurrenceDialog.EXTRA_RRULE))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        val raw by repository.raw.collectAsState()
        val effective by repository.effective.collectAsState()
        var counts by remember { mutableStateOf(ConstructorCounts()) }
        var pending by remember { mutableStateOf<Constructor?>(null) }
        var targets by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var comments by remember {
            mutableStateOf(preferences.getBoolean(R.string.p_show_task_edit_comments, false))
        }
        var advanceDefault by remember { mutableStateOf<Long?>(null) }
        var picker by remember { mutableStateOf<ListPickerTarget?>(null) }
        val scope = rememberCoroutineScope()
        val settings = defaults.settings
        val reminderSummary = stringResource(
            LocationReminderOptions.firstOrNull { it.first == settings.defaultLocationReminder }?.second
                ?: LocationReminderOptions.first().second
        )
        val updateSummary = stringResource(
            LocationUpdateIntervalOptions.firstOrNull { it.first == settings.locationUpdateIntervalMinutes }?.second
                ?: LocationUpdateIntervalOptions.first().second
        )
        LaunchedEffect(raw) {
            counts = repository.counts(currentTimeMillis())
            advanceDefault = preferences.defaultAlarms().advance()?.time?.let { -it }
        }
        val setAdvanceDefault = { offset: Long? ->
            scope.launch {
                preferences.setDefaultAlarms(preferences.defaultAlarms().withAdvance(offset))
                advanceDefault = offset
            }
        }
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            val change = { next: Constructor ->
                if (raw.advance != next.advance) {
                    scope.launch {
                        preferences.defaultAlarms().forAdvanceToggle(raw.advance, next.advance)?.let {
                            preferences.setDefaultAlarms(it)
                            advanceDefault = it.advance()?.time?.let { time -> -time }
                        }
                    }
                }
                val sync = repository.sync
                if (counts.localLists > 0 && next.keepsEverythingInGoogle(sync) && !raw.keepsEverythingInGoogle(sync)) {
                    scope.launch {
                        targets = repository.googleLists()
                        pending = next
                    }
                } else {
                    repository.write(next)
                }
            }
            ConstructorScreen(
                raw = raw,
                effective = effective,
                geofences = configuration.supportsGeofences,
                counts = counts,
                comments = comments,
                onPreset = { change(raw.withPreset(it)) },
                onChange = { change(it) },
                onComments = {
                    preferences.setBoolean(R.string.p_show_task_edit_comments, it)
                    comments = it
                },
                advanceDefault = advanceDefault,
                onAdvanceDefault = { setAdvanceDefault(it) },
                recurrenceSummary = defaults.recurrenceSummary,
                onRecurrence = {
                    BasicRecurrenceDialog
                        .newBasicRecurrenceDialog(
                            rrule = defaults.settings.defaultRecurrence,
                            dueDate = 0,
                            accountType = CaldavAccount.TYPE_LOCAL,
                        )
                        .show(parentFragmentManager, FRAG_TAG_BASIC_RECURRENCE)
                },
                locationName = defaults.locationName,
                hasDefaultLocation = defaults.hasDefaultLocation,
                onLocation = {
                    locationLauncher.launch(Intent(context, LocationPickerActivity::class.java))
                },
                onDeleteLocation = { defaults.setDefaultLocation(null) },
                locationReminderSummary = reminderSummary,
                onLocationReminder = { picker = ListPickerTarget.LOCATION_REMINDER },
                locationUpdateSummary = updateSummary,
                onLocationUpdate = { picker = ListPickerTarget.LOCATION_UPDATE_INTERVAL },
            )
            picker?.let { target ->
                val options = when (target) {
                    ListPickerTarget.LOCATION_REMINDER -> LocationReminderOptions
                    ListPickerTarget.LOCATION_UPDATE_INTERVAL -> LocationUpdateIntervalOptions
                    else -> emptyList()
                }
                ListPreferenceDialog(
                    title = stringResource(
                        if (target == ListPickerTarget.LOCATION_REMINDER) {
                            Res.string.EPr_default_location_reminder_title
                        } else {
                            Res.string.location_update_interval_title
                        }
                    ),
                    options = options,
                    selected = defaults.listPickerValue(target),
                    onSelect = { defaults.setListPickerValue(target, it) },
                    onDismiss = { picker = null },
                )
            }
            pending?.let { next ->
                MoveTargetDialog(
                    lists = counts.localLists,
                    targets = targets,
                    onPick = { target ->
                        pending = null
                        repository.write(next.copy(localLists = false))
                        repository.moveLocalListsToGoogle(target)
                    },
                    onDismiss = { pending = null },
                    dismissText = getString(android.R.string.cancel),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        defaults.refreshState()
        val surfaceColor = theme.themeBase.getSettingsSurfaceColor(requireActivity())
        (activity as? BasePreferences)?.toolbar?.let { toolbar ->
            toolbar.setBackgroundColor(surfaceColor)
            (toolbar.parent as? View)?.setBackgroundColor(surfaceColor)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val defaultColor = ContextCompat.getColor(requireContext(), R.color.content_background)
        (activity as? BasePreferences)?.toolbar?.let { toolbar ->
            toolbar.setBackgroundColor(defaultColor)
            (toolbar.parent as? View)?.setBackgroundColor(defaultColor)
        }
    }

    companion object {
        private const val FRAG_TAG_BASIC_RECURRENCE = "frag_tag_basic_recurrence"
    }
}
