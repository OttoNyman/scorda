package org.tasks.preferences.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.tasks.PlatformConfiguration
import org.tasks.R
import org.tasks.compose.settings.ConstructorScreen
import org.tasks.compose.settings.MoveTargetDialog
import org.tasks.constructor.Constructor
import org.tasks.constructor.ConstructorCounts
import org.tasks.constructor.ConstructorRepository
import org.tasks.preferences.BasePreferences
import org.tasks.preferences.Preferences
import org.tasks.reminders.advance
import org.tasks.reminders.forAdvanceToggle
import org.tasks.reminders.withAdvance
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import javax.inject.Inject

@AndroidEntryPoint
class ConstructorPreferences : Fragment() {

    @Inject lateinit var theme: Theme
    @Inject lateinit var repository: ConstructorRepository
    @Inject lateinit var configuration: PlatformConfiguration
    @Inject lateinit var preferences: Preferences

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
        var markdown by remember { mutableStateOf(preferences.getBoolean(R.string.p_markdown, false)) }
        var comments by remember {
            mutableStateOf(preferences.getBoolean(R.string.p_show_task_edit_comments, false))
        }
        var advanceDefault by remember { mutableStateOf<Long?>(null) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(raw) {
            counts = repository.counts(currentTimeMillis())
            advanceDefault = preferences.defaultAlarms().advance()?.time?.let { -it }
        }
        // the default advance is one early entry in the default reminders every new task gets
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
                        // read fresh: the screen's copy may not be loaded yet
                        preferences.defaultAlarms().forAdvanceToggle(raw.advance, next.advance)?.let {
                            preferences.setDefaultAlarms(it)
                            advanceDefault = it.advance()?.time?.let { time -> -time }
                        }
                    }
                }
                val sync = repository.sync
                // Leaving the local block while syncing moves the local lists: ask where.
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
                sync = repository.sync,
                geofences = configuration.supportsGeofences,
                counts = counts,
                markdown = markdown,
                comments = comments,
                onPreset = { change(raw.withPreset(it)) },
                onChange = { change(it) },
                onMarkdown = {
                    preferences.setBoolean(R.string.p_markdown, it)
                    markdown = it
                },
                onComments = {
                    preferences.setBoolean(R.string.p_show_task_edit_comments, it)
                    comments = it
                },
                advanceDefault = advanceDefault,
                onAdvanceDefault = { setAdvanceDefault(it) },
            )
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
}
