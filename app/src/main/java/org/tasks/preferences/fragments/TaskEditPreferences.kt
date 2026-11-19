package org.tasks.preferences.fragments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import com.todoroo.andlib.utility.AndroidUtilities.atLeastOreoMR1
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.R
import org.tasks.compose.settings.TaskEditScreen
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
class TaskEditPreferences : Fragment() {

    @Inject lateinit var theme: Theme

    private val viewModel: TaskEditPreferencesViewModel by viewModels()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentManager.setFragmentResultListener(REQUEST_KEY_MORNING, this) { _, bundle ->
            handleTimePickerResult(bundle) { viewModel.handleMorningResult(it) }
        }
        parentFragmentManager.setFragmentResultListener(REQUEST_KEY_AFTERNOON, this) { _, bundle ->
            handleTimePickerResult(bundle) { viewModel.handleAfternoonResult(it) }
        }
        parentFragmentManager.setFragmentResultListener(REQUEST_KEY_EVENING, this) { _, bundle ->
            handleTimePickerResult(bundle) { viewModel.handleEveningResult(it) }
        }
        parentFragmentManager.setFragmentResultListener(REQUEST_KEY_NIGHT, this) { _, bundle ->
            handleTimePickerResult(bundle) { viewModel.handleNightResult(it) }
        }
    }

    private fun handleTimePickerResult(
        bundle: android.os.Bundle,
        handler: (Int) -> TaskEditPreferencesViewModel.TimePickerResult,
    ) {
        val timestamp = bundle.getLong(MyTimePickerDialog.EXTRA_TIMESTAMP, 0L)
        val millisOfDay = DateTime(timestamp).millisOfDay
        val result = handler(millisOfDay)
        if (result is TaskEditPreferencesViewModel.TimePickerResult.Error) {
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
            TaskEditScreen(
                showWithoutUnlockVisible = atLeastOreoMR1(),
                showLinks = viewModel.showLinks,
                backButtonSaves = viewModel.backButtonSaves,
                showComments = viewModel.showComments,
                showCommentsRow = false,
                showWithoutUnlock = viewModel.showWithoutUnlock,
                morningSummary = viewModel.morningSummary,
                afternoonSummary = viewModel.afternoonSummary,
                eveningSummary = viewModel.eveningSummary,
                nightSummary = viewModel.nightSummary,
                onShowLinks = { viewModel.updateShowLinks(it) },
                onBackButtonSaves = { viewModel.updateBackButtonSaves(it) },
                onShowComments = { viewModel.updateShowComments(it) },
                onShowWithoutUnlock = { viewModel.updateShowWithoutUnlock(it) },
                onMorning = {
                    showTimePicker(
                        R.string.p_date_shortcut_morning,
                        R.integer.default_morning,
                        REQUEST_KEY_MORNING,
                    )
                },
                onAfternoon = {
                    showTimePicker(
                        R.string.p_date_shortcut_afternoon,
                        R.integer.default_afternoon,
                        REQUEST_KEY_AFTERNOON,
                    )
                },
                onEvening = {
                    showTimePicker(
                        R.string.p_date_shortcut_evening,
                        R.integer.default_evening,
                        REQUEST_KEY_EVENING,
                    )
                },
                onNight = {
                    showTimePicker(
                        R.string.p_date_shortcut_night,
                        R.integer.default_night,
                        REQUEST_KEY_NIGHT,
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
        private const val REQUEST_KEY_AFTERNOON = "time_picker_afternoon"
        private const val REQUEST_KEY_EVENING = "time_picker_evening"
        private const val REQUEST_KEY_NIGHT = "time_picker_night"
    }
}
