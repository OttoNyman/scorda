package org.tasks.preferences.fragments

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.todoroo.astrid.core.SortHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tasks.R
import org.tasks.preferences.Preferences
import javax.inject.Inject

@HiltViewModel
class TaskListPreferencesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: Preferences,
) : ViewModel() {

    var fontSize by mutableIntStateOf(16)
        private set
    var rowSpacing by mutableIntStateOf(16)
        private set
    var showFullTitle by mutableStateOf(false)
        private set
    var showDescription by mutableStateOf(true)
        private set
    var showFullDescription by mutableStateOf(false)
        private set
    var showLinks by mutableStateOf(false)
        private set
    var groupByDay by mutableStateOf(true)
        private set
    var showCompleted by mutableStateOf(true)
        private set
    var completedAtBottom by mutableStateOf(true)
        private set
    var noTimeAtEndOfDay by mutableStateOf(true)
        private set
    var undatedAtBottom by mutableStateOf(true)
        private set
    var chipAppearanceSummary by mutableStateOf("")
        private set
    var placeChips by mutableStateOf(true)
        private set
    var tagChips by mutableStateOf(true)
        private set
    var showChipAppearanceDialog by mutableStateOf(false)
        private set
    var showNoTimeDialog by mutableStateOf(false)
        private set
    var showUndatedDialog by mutableStateOf(false)
        private set

    val chipEntries: Array<String> = context.resources.getStringArray(R.array.chip_appearance)
    val chipValues: Array<String> = context.resources.getStringArray(R.array.chip_appearance_values)

    init {
        refreshState()
    }

    fun refreshState() {
        fontSize = preferences.getInt(R.string.p_fontSize, 16)
        rowSpacing = preferences.getInt(R.string.p_rowPadding, 16)
        showFullTitle = preferences.getBoolean(R.string.p_fullTaskTitle, false)
        showDescription = preferences.getBoolean(R.string.p_show_description, true)
        showFullDescription = preferences.getBoolean(R.string.p_show_full_description, false)
        showLinks = preferences.getBoolean(R.string.p_linkify_task_list, false)
        groupByDay = preferences.groupMode != SortHelper.GROUP_NONE
        showCompleted = preferences.showCompleted
        completedAtBottom = preferences.completedTasksAtBottom
        noTimeAtEndOfDay = preferences.noTimeAtEndOfDay
        undatedAtBottom = preferences.undatedAtBottom
        refreshChipAppearance()
        placeChips = preferences.getBoolean(R.string.p_place_chips, true)
        tagChips = preferences.getBoolean(R.string.p_tag_chips, true)
    }

    fun updateFontSize(value: Int) {
        preferences.setInt(R.string.p_fontSize, value)
        fontSize = value
    }

    fun updateRowSpacing(value: Int) {
        preferences.setInt(R.string.p_rowPadding, value)
        rowSpacing = value
    }

    fun updateShowFullTitle(enabled: Boolean) {
        preferences.setBoolean(R.string.p_fullTaskTitle, enabled)
        showFullTitle = enabled
    }

    fun updateShowDescription(enabled: Boolean) {
        preferences.setBoolean(R.string.p_show_description, enabled)
        showDescription = enabled
        if (!enabled) {
            preferences.setBoolean(R.string.p_show_full_description, false)
            showFullDescription = false
        }
    }

    fun updateShowFullDescription(enabled: Boolean) {
        preferences.setBoolean(R.string.p_show_full_description, enabled)
        showFullDescription = enabled
    }

    fun updateShowLinks(enabled: Boolean) {
        preferences.setBoolean(R.string.p_linkify_task_list, enabled)
        showLinks = enabled
    }

    fun updateGroupByDay(enabled: Boolean) {
        preferences.groupMode = if (enabled) SortHelper.SORT_DUE else SortHelper.GROUP_NONE
        groupByDay = enabled
    }

    fun updateShowCompleted(enabled: Boolean) {
        preferences.showCompleted = enabled
        showCompleted = enabled
    }

    fun updateCompletedAtBottom(enabled: Boolean) {
        preferences.completedTasksAtBottom = enabled
        completedAtBottom = enabled
    }

    fun openNoTimeDialog() {
        showNoTimeDialog = true
    }

    fun dismissNoTimeDialog() {
        showNoTimeDialog = false
    }

    fun updateNoTimeAtEndOfDay(value: Boolean) {
        preferences.noTimeAtEndOfDay = value
        noTimeAtEndOfDay = value
        showNoTimeDialog = false
    }

    fun openUndatedDialog() {
        showUndatedDialog = true
    }

    fun dismissUndatedDialog() {
        showUndatedDialog = false
    }

    fun updateUndatedAtBottom(value: Boolean) {
        preferences.undatedAtBottom = value
        undatedAtBottom = value
        showUndatedDialog = false
    }

    fun openChipAppearanceDialog() {
        showChipAppearanceDialog = true
    }

    fun dismissChipAppearanceDialog() {
        showChipAppearanceDialog = false
    }

    fun setChipAppearance(value: String) {
        preferences.setString(R.string.p_chip_appearance, value)
        refreshChipAppearance()
    }

    fun getChipAppearanceCurrentValue(): String =
        preferences.getIntegerFromString(R.string.p_chip_appearance, 0).toString()

    fun updatePlaceChips(enabled: Boolean) {
        preferences.setBoolean(R.string.p_place_chips, enabled)
        placeChips = enabled
    }

    fun updateTagChips(enabled: Boolean) {
        preferences.setBoolean(R.string.p_tag_chips, enabled)
        tagChips = enabled
    }

    private fun refreshChipAppearance() {
        val currentValue = preferences.getIntegerFromString(
            R.string.p_chip_appearance, 0
        ).toString()
        val index = chipValues.indexOf(currentValue).coerceAtLeast(0)
        chipAppearanceSummary = chipEntries[index]
    }
}
