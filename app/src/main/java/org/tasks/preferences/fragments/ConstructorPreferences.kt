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
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.PlatformConfiguration
import org.tasks.R
import org.tasks.compose.settings.ConstructorScreen
import org.tasks.compose.settings.MoveListsDialog
import org.tasks.constructor.Constructor
import org.tasks.constructor.ConstructorCounts
import org.tasks.constructor.ConstructorRepository
import org.tasks.preferences.BasePreferences
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import javax.inject.Inject

@AndroidEntryPoint
class ConstructorPreferences : Fragment() {

    @Inject lateinit var theme: Theme
    @Inject lateinit var repository: ConstructorRepository
    @Inject lateinit var configuration: PlatformConfiguration

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        val raw by repository.raw.collectAsState()
        val effective by repository.effective.collectAsState()
        var counts by remember { mutableStateOf(ConstructorCounts()) }
        var pending by remember { mutableStateOf<Constructor?>(null) }
        LaunchedEffect(raw) {
            counts = repository.counts(currentTimeMillis())
        }
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            val change = { next: Constructor ->
                val sync = repository.sync
                // Leaving the local block while syncing moves the local lists: ask first.
                if (counts.localLists > 0 && next.keepsEverythingInGoogle(sync) && !raw.keepsEverythingInGoogle(sync)) {
                    pending = next
                } else {
                    repository.write(next)
                }
            }
            ConstructorScreen(
                raw = raw,
                effective = effective,
                sync = repository.sync,
                geofences = configuration.supportsGeofences,
                calendarEvents = configuration.supportsCalendarEvents,
                counts = counts,
                onPreset = { change(raw.withPreset(it)) },
                onChange = { change(it) },
            )
            pending?.let { next ->
                MoveListsDialog(
                    lists = counts.localLists,
                    onMove = {
                        pending = null
                        repository.write(next.copy(localLists = false))
                        repository.moveLocalListsToGoogle()
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
