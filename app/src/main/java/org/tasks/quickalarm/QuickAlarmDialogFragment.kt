package org.tasks.quickalarm

import android.app.Dialog
import android.os.Bundle
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.compose.QuickAlarmScreen
import org.tasks.notifications.AndroidReminderHealth
import org.tasks.preferences.Preferences
import org.tasks.themes.TasksTheme
import org.tasks.themes.Theme
import javax.inject.Inject

@AndroidEntryPoint
class QuickAlarmDialogFragment : DialogFragment() {
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var theme: Theme

    private val viewModel: QuickAlarmViewModel by viewModels()
    private val reminderHealth by lazy { AndroidReminderHealth(requireContext()) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(
            ComposeView(requireContext()).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    TasksTheme(
                        theme = theme.themeBase.index,
                        primary = theme.themeColor.primaryColor,
                    ) {
                        QuickAlarmScreen(
                            preferences = preferences,
                            viewModel = viewModel,
                            reminderHealth = reminderHealth,
                            onFinish = { dismissAllowingStateLoss() },
                        )
                    }
                }
            },
        )
        return dialog
    }

    companion object {
        const val TAG = "quick_alarm"
        fun newInstance() = QuickAlarmDialogFragment()
    }
}
