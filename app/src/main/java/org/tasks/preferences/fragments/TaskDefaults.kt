package org.tasks.preferences.fragments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.R
import org.tasks.compose.FilterSelectionActivity.Companion.launch
import org.tasks.compose.FilterSelectionActivity.Companion.registerForListPickerResult
import org.tasks.compose.settings.TaskDefaultsContent
import org.tasks.extensions.Context.is24HourFormat
import org.tasks.preferences.BasePreferences
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import javax.inject.Inject

@AndroidEntryPoint
class TaskDefaults : Fragment() {

    @Inject lateinit var theme: Theme
    @Inject lateinit var featureGate: org.tasks.constructor.FeatureGate

    private val viewModel: TaskDefaultsHiltViewModel by viewModels()

    private val listPickerLauncher = registerForListPickerResult {
        viewModel.setDefaultList(it)
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
            val is24HourFormat = LocalContext.current.is24HourFormat

            TaskDefaultsContent(
                viewModel = viewModel,
                is24HourFormat = is24HourFormat,
                onDefaultList = {
                    listPickerLauncher.launch(
                        context = requireContext(),
                        selectedFilter = viewModel.defaultListFilter,
                        listsOnly = true,
                    )
                },
                features = featureGate.current,
                bottomInsets = {
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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
