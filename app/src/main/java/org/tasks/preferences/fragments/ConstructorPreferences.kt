package org.tasks.preferences.fragments

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.app.NotificationManagerCompat
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
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.jetbrains.compose.resources.stringResource
import org.tasks.extensions.Context.openAppNotificationSettings
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
import org.tasks.preferences.FragmentPermissionRequestor
import org.tasks.preferences.PermissionChecker
import org.tasks.preferences.PermissionRequestor
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
import tasks.kmp.generated.resources.notes_off_delete
import tasks.kmp.generated.resources.notes_off_keep
import tasks.kmp.generated.resources.notes_off_message
import tasks.kmp.generated.resources.notes_off_title
import tasks.kmp.generated.resources.alarm_on_lock_screen_allow
import tasks.kmp.generated.resources.alarm_on_lock_screen_decline
import tasks.kmp.generated.resources.geo_need
import tasks.kmp.generated.resources.geo_need_title
import tasks.kmp.generated.resources.need_open
import tasks.kmp.generated.resources.pin_shade_need
import tasks.kmp.generated.resources.pin_shade_need_title
import javax.inject.Inject

@AndroidEntryPoint
class ConstructorPreferences : Fragment() {

    @Inject lateinit var theme: Theme
    @Inject lateinit var repository: ConstructorRepository
    @Inject lateinit var configuration: PlatformConfiguration
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var permissionChecker: PermissionChecker
    @Inject lateinit var permissionRequestor: FragmentPermissionRequestor

    private val defaults: TaskDefaultsHiltViewModel by viewModels()

    private var awaitingPlace = false

    private val locationLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            defaults.setDefaultLocation(result.data?.getParcelableExtra<Place>(EXTRA_PLACE))
        }
    }

    private fun grantPin() {
        preferences.setString(R.string.p_capability_gate, "")
        repository.write(repository.raw.value.copy(pinShade = true))
    }

    private fun grantGeo() {
        preferences.setString(R.string.p_capability_gate, "")
        repository.write(repository.raw.value.copy(geo = true))
    }

    private fun notificationsOn() =
        NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()

    private fun settleCapabilityGate() {
        when (preferences.getStringValue(R.string.p_capability_gate)) {
            "pin" -> {
                preferences.setString(R.string.p_capability_gate, "")
                if (notificationsOn()) grantPin()
            }
            "geo" -> {
                preferences.setString(R.string.p_capability_gate, "")
                if (permissionChecker.canAccessBackgroundLocation()) grantGeo()
            }
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
        // «Описание» going off while tasks have one: delete them, or keep them shown with no new ones
        var notesOff by remember { mutableStateOf<Constructor?>(null) }
        // a preset's comments value, held while a dialog decides whether the preset is written at all
        var deferredComments by remember { mutableStateOf<Boolean?>(null) }
        var targets by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var comments by remember {
            mutableStateOf(preferences.getBoolean(R.string.p_show_task_edit_comments, false))
        }
        var advanceDefault by remember { mutableStateOf<Long?>(null) }
        var picker by remember { mutableStateOf<ListPickerTarget?>(null) }
        var gate by remember { mutableStateOf<String?>(null) }
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
            // comments live in their own preference, not in Constructor: a preset sets them, but only
            // when the preset itself is written (a dialog in between may still be cancelled)
            val writeWith = { next: Constructor, presetComments: Boolean? ->
                repository.write(next)
                presetComments?.let {
                    preferences.setBoolean(R.string.p_show_task_edit_comments, it)
                    comments = it
                }
            }
            val apply: (Constructor, Boolean?) -> Unit = { next, presetComments ->
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
                        deferredComments = presetComments
                    }
                } else {
                    writeWith(next, presetComments)
                }
            }
            val change: (Constructor, Boolean?) -> Unit = { next, presetComments ->
                if (raw.notes && !next.notes && counts.notes > 0) {
                    notesOff = next
                    deferredComments = presetComments
                } else {
                    apply(next, presetComments)
                }
            }
            val openChange: (Constructor, Boolean?) -> Unit = { next, presetComments ->
                when {
                    !raw.pinShade && next.pinShade && !notificationsOn() -> gate = "pin"
                    !raw.geo && next.geo && !permissionChecker.canAccessBackgroundLocation() -> {
                        val rest = next.copy(geo = false)
                        if (rest != raw) change(rest, presetComments)
                        gate = "geo"
                    }
                    else -> change(next, presetComments)
                }
            }
            ConstructorScreen(
                raw = raw,
                effective = effective,
                geofences = configuration.supportsGeofences,
                counts = counts,
                comments = comments,
                onPreset = { preset ->
                    openChange(raw.withPreset(preset), preset == org.tasks.constructor.Preset.Full)
                },
                onChange = { openChange(it, null) },
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
            notesOff?.let { next ->
                val decide = { clear: Boolean ->
                    val presetComments = deferredComments
                    notesOff = null
                    deferredComments = null
                    apply(next, presetComments)
                    if (clear) repository.clearDescriptions()
                }
                val cancel = {
                    notesOff = null
                    deferredComments = null
                }
                AlertDialog(
                    onDismissRequest = cancel,
                    title = { Text(stringResource(Res.string.notes_off_title)) },
                    text = { Text(stringResource(Res.string.notes_off_message, counts.notes)) },
                    confirmButton = {
                        Row {
                            TextButton(onClick = { decide(true) }) {
                                Text(stringResource(Res.string.notes_off_delete))
                            }
                            TextButton(onClick = { decide(false) }) {
                                Text(stringResource(Res.string.notes_off_keep))
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = cancel) {
                            Text(getString(android.R.string.cancel))
                        }
                    },
                )
            }
            gate?.let { which ->
                val place = which == "geo"
                AlertDialog(
                    onDismissRequest = { gate = null },
                    title = {
                        Text(stringResource(if (place) Res.string.geo_need_title else Res.string.pin_shade_need_title))
                    },
                    text = {
                        Text(stringResource(if (place) Res.string.geo_need else Res.string.pin_shade_need))
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            gate = null
                            if (place) {
                                awaitingPlace = true
                                preferences.setString(R.string.p_capability_gate, "geo")
                                if (permissionRequestor.requestBackgroundLocation()) {
                                    awaitingPlace = false
                                    grantGeo()
                                }
                            } else {
                                preferences.setString(R.string.p_capability_gate, "pin")
                                requireContext().openAppNotificationSettings()
                            }
                        }) {
                            Text(stringResource(if (place) Res.string.need_open else Res.string.alarm_on_lock_screen_allow))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { gate = null }) {
                            Text(stringResource(Res.string.alarm_on_lock_screen_decline))
                        }
                    },
                )
            }
            pending?.let { next ->
                MoveTargetDialog(
                    lists = counts.localLists,
                    targets = targets,
                    onPick = { target ->
                        val presetComments = deferredComments
                        pending = null
                        deferredComments = null
                        writeWith(next.copy(localLists = false), presetComments)
                        repository.moveLocalListsToGoogle(target)
                    },
                    onDismiss = {
                        pending = null
                        deferredComments = null
                    },
                    dismissText = getString(android.R.string.cancel),
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!awaitingPlace) return
        if (permissionChecker.canAccessBackgroundLocation()) {
            awaitingPlace = false
            grantGeo()
        } else if (
            requestCode == PermissionRequestor.REQUEST_FOREGROUND_LOCATION &&
            permissionChecker.canAccessForegroundLocation()
        ) {
            permissionRequestor.requestBackgroundLocation()
        } else {
            awaitingPlace = false
            if (preferences.getStringValue(R.string.p_capability_gate) == "geo") {
                preferences.setString(R.string.p_capability_gate, "")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        settleCapabilityGate()
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
