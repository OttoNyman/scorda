package org.tasks.preferences.fragments

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.analytics.Firebase
import org.tasks.compose.settings.WidgetsScreen
import org.tasks.compose.settings.canPinWidget
import org.tasks.compose.settings.createWidget
import org.tasks.filters.MyTasksFilter
import org.tasks.preferences.BasePreferences
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import org.tasks.widget.WidgetConfigActivity
import javax.inject.Inject

@AndroidEntryPoint
class Widgets : Fragment() {

    @Inject lateinit var theme: Theme
    @Inject lateinit var defaultFilterProvider: DefaultFilterProvider
    @Inject lateinit var firebase: Firebase

    private val viewModel: WidgetsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?
    ) = content {
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            val context = requireContext()
            val activity = requireActivity() as AppCompatActivity
            val canPin = activity.canPinWidget()
            WidgetsScreen(
                widgets = viewModel.widgets,
                onWidgetClick = { widgetId ->
                    val intent = Intent(context, WidgetConfigActivity::class.java)
                    intent.putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        widgetId,
                    )
                    intent.action = "widget_settings"
                    startActivity(intent)
                },
                onAddWidget = if (canPin) {
                    {
                        viewLifecycleOwner.lifecycleScope.launch {
                            activity.createWidget(
                                filter = MyTasksFilter.create(),
                                color = 0,
                                defaultFilterProvider = defaultFilterProvider,
                                firebase = firebase,
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshState()
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
