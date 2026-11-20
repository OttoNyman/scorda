package org.tasks.preferences.fragments

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.R
import org.tasks.activities.NavigationDrawerCustomization
import org.tasks.compose.settings.NavigationDrawerContent
import org.tasks.preferences.BasePreferences
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import javax.inject.Inject

@AndroidEntryPoint
class NavigationDrawer : Fragment() {

    @Inject lateinit var theme: Theme
    @Inject lateinit var constructorRepository: org.tasks.constructor.ConstructorRepository

    private val viewModel: NavigationDrawerHiltViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?
    ) = content {
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            // live: the «Фильтры» switch here changes the constructor too
            val features by constructorRepository.effective.collectAsState()
            val canCustomize =
                features.filters || features.tags || features.geo || features.lists
            NavigationDrawerContent(
                viewModel = viewModel,
                features = features,
                onCustomizeDrawer = if (canCustomize) {
                    {
                        startActivity(
                            Intent(requireContext(), NavigationDrawerCustomization::class.java)
                        )
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
