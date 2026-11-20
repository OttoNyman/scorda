package org.tasks.preferences.fragments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.R
import org.tasks.compose.settings.TaskListScreen
import org.tasks.preferences.BasePreferences
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import javax.inject.Inject

@AndroidEntryPoint
class TaskListPreferences : Fragment() {

    @Inject lateinit var theme: Theme

    private val viewModel: TaskListPreferencesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?
    ) = content {
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            TaskListScreen(
                fontSize = viewModel.fontSize,
                rowSpacing = viewModel.rowSpacing,
                showFullTitle = viewModel.showFullTitle,
                showDescription = viewModel.showDescription,
                showFullDescription = viewModel.showFullDescription,
                showLinks = viewModel.showLinks,
                groupByDay = viewModel.groupByDay,
                showCompleted = viewModel.showCompleted,
                completedAtBottom = viewModel.completedAtBottom,
                noTimeAtEndOfDay = viewModel.noTimeAtEndOfDay,
                undatedAtBottom = viewModel.undatedAtBottom,
                chipAppearanceSummary = viewModel.chipAppearanceSummary,
                placeChips = viewModel.placeChips,
                tagChips = viewModel.tagChips,
                onFontSize = { viewModel.updateFontSize(it) },
                onRowSpacing = { viewModel.updateRowSpacing(it) },
                onShowFullTitle = { viewModel.updateShowFullTitle(it) },
                onShowDescription = { viewModel.updateShowDescription(it) },
                onShowFullDescription = { viewModel.updateShowFullDescription(it) },
                onShowLinks = { viewModel.updateShowLinks(it) },
                onGroupByDay = { viewModel.updateGroupByDay(it) },
                onShowCompleted = { viewModel.updateShowCompleted(it) },
                onCompletedAtBottom = { viewModel.updateCompletedAtBottom(it) },
                onNoTimePlacement = { viewModel.openNoTimeDialog() },
                onUndatedPlacement = { viewModel.openUndatedDialog() },
                onChipAppearance = { viewModel.openChipAppearanceDialog() },
                onPlaceChips = { viewModel.updatePlaceChips(it) },
                onTagChips = { viewModel.updateTagChips(it) },
            )

            if (viewModel.showChipAppearanceDialog) {
                PlacementDialog(
                    title = stringResource(R.string.chip_appearance),
                    options = viewModel.chipEntries.mapIndexed { index, entry ->
                        entry to (viewModel.chipValues[index] == viewModel.getChipAppearanceCurrentValue())
                    },
                    onSelect = { index ->
                        viewModel.setChipAppearance(viewModel.chipValues[index])
                        viewModel.dismissChipAppearanceDialog()
                    },
                    onDismiss = { viewModel.dismissChipAppearanceDialog() },
                )
            }

            if (viewModel.showNoTimeDialog) {
                PlacementDialog(
                    title = stringResource(R.string.no_time_placement),
                    options = listOf(
                        stringResource(R.string.no_time_at_top_of_day) to !viewModel.noTimeAtEndOfDay,
                        stringResource(R.string.no_time_at_end_of_day) to viewModel.noTimeAtEndOfDay,
                    ),
                    onSelect = { index -> viewModel.updateNoTimeAtEndOfDay(index == 1) },
                    onDismiss = { viewModel.dismissNoTimeDialog() },
                )
            }

            if (viewModel.showUndatedDialog) {
                PlacementDialog(
                    title = stringResource(R.string.undated_placement),
                    options = listOf(
                        stringResource(R.string.undated_at_top) to !viewModel.undatedAtBottom,
                        stringResource(R.string.undated_at_bottom) to viewModel.undatedAtBottom,
                    ),
                    onSelect = { index -> viewModel.updateUndatedAtBottom(index == 1) },
                    onDismiss = { viewModel.dismissUndatedDialog() },
                )
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

@androidx.compose.runtime.Composable
private fun PlacementDialog(
    title: String,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, (label, selected) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}
