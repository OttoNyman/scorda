package org.tasks.preferences.fragments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.R
import org.tasks.compose.settings.DateAndTimeScreen
import org.tasks.compose.settings.DateFormatDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.tasks.dialogs.MyTimePickerDialog
import org.tasks.dialogs.MyTimePickerDialog.Companion.newTimePicker
import org.tasks.extensions.Context.toast
import org.tasks.preferences.BasePreferences
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import org.tasks.time.DateTime
import org.tasks.time.withMillisOfDay
import javax.inject.Inject

@AndroidEntryPoint
class DateAndTime : Fragment() {

    @Inject lateinit var theme: Theme

    private val viewModel: DateAndTimeViewModel by viewModels()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        parentFragmentManager.setFragmentResultListener(
            REQUEST_KEY_MORNING, this
        ) { _, bundle ->
            handleTimePickerResult(bundle) { viewModel.handleMorningResult(it) }
        }
        parentFragmentManager.setFragmentResultListener(
            REQUEST_KEY_EVENING, this
        ) { _, bundle ->
            handleTimePickerResult(bundle) { viewModel.handleEveningResult(it) }
        }
    }

    private fun handleTimePickerResult(
        bundle: android.os.Bundle,
        handler: (Int) -> DateAndTimeViewModel.TimePickerResult
    ) {
        val timestamp = bundle.getLong(MyTimePickerDialog.EXTRA_TIMESTAMP, 0L)
        val millisOfDay = DateTime(timestamp).millisOfDay
        val result = handler(millisOfDay)
        if (result is DateAndTimeViewModel.TimePickerResult.Error) {
            context?.toast(
                result.messageResId,
                getString(result.setting),
                getString(result.relative),
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?
    ) = content {
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            var pickFormat by remember { mutableStateOf(false) }
            if (pickFormat) {
                DateFormatDialog(
                    options = viewModel.dateFormatOptions,
                    selected = viewModel.dateFormat,
                    onSelect = {
                        viewModel.updateDateFormat(it)
                        pickFormat = false
                    },
                    onDismiss = { pickFormat = false },
                )
            }
            DateAndTimeScreen(
                dateFormatLabel = viewModel.dateFormatLabel,
                morningSummary = viewModel.morningSummary,
                eveningSummary = viewModel.eveningSummary,
                onDateFormat = { pickFormat = true },
                onMorning = {
                    showTimePicker(
                        R.string.p_date_shortcut_morning,
                        R.integer.default_morning,
                        REQUEST_KEY_MORNING,
                    )
                },
                onEvening = {
                    showTimePicker(
                        R.string.p_date_shortcut_evening,
                        R.integer.default_evening,
                        REQUEST_KEY_EVENING,
                    )
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

    private fun showTimePicker(prefKey: Int, defaultRes: Int, requestKey: String) {
        val millisOfDay = viewModel.getMillisOfDay(prefKey, defaultRes)
        val current = DateTime().withMillisOfDay(millisOfDay)
        newTimePicker(requestKey, current.millis)
            .show(parentFragmentManager, FRAG_TAG_TIME_PICKER)
    }

    companion object {
        private const val FRAG_TAG_TIME_PICKER = "frag_tag_time_picker"
        private const val REQUEST_KEY_MORNING = "time_picker_morning"
        private const val REQUEST_KEY_EVENING = "time_picker_evening"
    }
}
