package org.tasks.preferences.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.tasks.R
import org.tasks.compose.settings.CardPosition
import org.tasks.compose.settings.PreferenceRow
import org.tasks.compose.settings.SettingsCardGap
import org.tasks.compose.settings.SettingsContentPadding
import org.tasks.compose.settings.SettingsItemCard
import org.tasks.preferences.BasePreferences
import org.tasks.preferences.MainSettingsViewModel
import org.tasks.resources.getString
import org.tasks.themes.TasksIcons
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.EPr_edit_screen_options
import tasks.kmp.generated.resources.navigation_drawer
import tasks.kmp.generated.resources.task_list_options
import tasks.kmp.generated.resources.widget_settings
import javax.inject.Inject

@AndroidEntryPoint
class ScreenSettings : Fragment() {

    @Inject lateinit var theme: Theme
    @Inject lateinit var featureGate: org.tasks.constructor.FeatureGate

    private val settings: MainSettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            val features = featureGate.current
            val showNavigationDrawer =
                features.filters || features.tags || features.geo || features.lists
            ScreenSettingsList(
                showWidgets = settings.supportsWidgets,
                showNavigationDrawer = showNavigationDrawer,
                onTaskList = { open(TaskListPreferences(), Res.string.task_list_options) },
                onTaskEdit = { open(TaskEditPreferences(), Res.string.EPr_edit_screen_options) },
                onNavigationDrawer = { open(NavigationDrawer(), Res.string.navigation_drawer) },
                onWidgets = { open(Widgets(), Res.string.widget_settings) },
            )
        }
    }

    private fun open(fragment: Fragment, title: StringResource) {
        val host = activity as? BasePreferences ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            host.startPreference(fragment, getString(title))
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

@Composable
private fun ScreenSettingsList(
    showWidgets: Boolean,
    showNavigationDrawer: Boolean,
    onTaskList: () -> Unit,
    onTaskEdit: () -> Unit,
    onNavigationDrawer: () -> Unit,
    onWidgets: () -> Unit,
) {
    val rows = buildList {
        add(ScreenRow(stringResource(Res.string.task_list_options), TasksIcons.LIST, onTaskList))
        add(ScreenRow(stringResource(Res.string.EPr_edit_screen_options), TasksIcons.EDIT, onTaskEdit))
        if (showNavigationDrawer) {
            add(ScreenRow(stringResource(Res.string.navigation_drawer), TasksIcons.MENU, onNavigationDrawer))
        }
        if (showWidgets) {
            add(ScreenRow(stringResource(Res.string.widget_settings), TasksIcons.WIDGETS, onWidgets))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            rows.forEachIndexed { index, row ->
                SettingsItemCard(position = CardPosition.forIndex(index, rows.size)) {
                    PreferenceRow(
                        title = row.title,
                        icon = row.icon,
                        onClick = row.onClick,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

private data class ScreenRow(
    val title: String,
    val icon: String,
    val onClick: () -> Unit,
)
