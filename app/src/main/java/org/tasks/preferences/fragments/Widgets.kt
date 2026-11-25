package org.tasks.preferences.fragments

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.analytics.Firebase
import org.tasks.compose.settings.WidgetsScreen
import org.tasks.compose.settings.canPinWidget
import org.tasks.compose.settings.createWidget
import org.tasks.dialogs.DialogBuilder
import org.tasks.filters.MyTasksFilter
import org.tasks.notifications.oemCandidates
import org.tasks.notifications.ReminderCheckId
import org.tasks.preferences.BasePreferences
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import org.tasks.widget.WidgetConfigActivity
import timber.log.Timber
import javax.inject.Inject
import org.tasks.widget.AppWidgetManager as TasksAppWidgetManager

@AndroidEntryPoint
class Widgets : Fragment() {

    @Inject lateinit var theme: Theme
    @Inject lateinit var defaultFilterProvider: DefaultFilterProvider
    @Inject lateinit var firebase: Firebase
    @Inject lateinit var dialogBuilder: DialogBuilder
    @Inject lateinit var tasksAppWidgetManager: TasksAppWidgetManager

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
                    { pinWidget(activity) }
                } else {
                    null
                },
            )
        }
    }

    private fun pinWidget(activity: AppCompatActivity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val before = tasksAppWidgetManager.widgetIds.size
            activity.createWidget(
                filter = MyTasksFilter.create(),
                color = 0,
                defaultFilterProvider = defaultFilterProvider,
                firebase = firebase,
            )
            delay(800)
            if (!isAdded) return@launch
            val stillResumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            val after = tasksAppWidgetManager.widgetIds.size
            Timber.d("pinWidget before=$before after=$after resumed=$stillResumed")
            if (stillResumed && after <= before) {
                showPinBlockedDialog()
            } else {
                viewModel.refreshState()
            }
        }
    }

    private fun showPinBlockedDialog() {
        dialogBuilder
            .newDialog(R.string.widget_pin_blocked_title)
            .setMessage(R.string.widget_pin_blocked_message)
            .setPositiveButton(R.string.widget_pin_open_settings) { _, _ ->
                openShortcutPermissionSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openShortcutPermissionSettings() {
        val packageName = requireContext().packageName
        val candidates = oemCandidates(ReminderCheckId.POPUPS, packageName, getString(R.string.app_name)) +
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", packageName, null))
        for (intent in candidates) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                Timber.d("skip intent $intent")
            } catch (e: Exception) {
                Timber.w(e, "skip intent $intent")
            }
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
