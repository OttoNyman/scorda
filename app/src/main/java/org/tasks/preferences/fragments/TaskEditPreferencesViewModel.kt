package org.tasks.preferences.fragments

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tasks.R
import org.tasks.extensions.Context.is24HourFormat
import org.tasks.kmp.formatTime
import org.tasks.preferences.Preferences
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.time.withMillisOfDay
import javax.inject.Inject

@HiltViewModel
class TaskEditPreferencesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: Preferences,
) : ViewModel() {

    var showLinks by mutableStateOf(false)
        private set
    var backButtonSaves by mutableStateOf(false)
        private set
    var showComments by mutableStateOf(false)
        private set
    var showWithoutUnlock by mutableStateOf(false)
        private set
    var morningSummary by mutableStateOf("")
        private set
    var afternoonSummary by mutableStateOf("")
        private set
    var eveningSummary by mutableStateOf("")
        private set
    var nightSummary by mutableStateOf("")
        private set

    init {
        refreshState()
    }

    fun refreshState() {
        showLinks = preferences.getBoolean(R.string.p_linkify_task_edit, false)
        backButtonSaves = preferences.getBoolean(R.string.p_back_button_saves_task, false)
        showComments = preferences.getBoolean(R.string.p_show_task_edit_comments, false)
        showWithoutUnlock = preferences.getBoolean(
            R.string.p_show_edit_screen_without_unlock, false
        )
        refreshMorning()
        refreshAfternoon()
        refreshEvening()
        refreshNight()
    }

    fun updateShowLinks(enabled: Boolean) {
        preferences.setBoolean(R.string.p_linkify_task_edit, enabled)
        showLinks = enabled
    }

    fun updateBackButtonSaves(enabled: Boolean) {
        preferences.setBoolean(R.string.p_back_button_saves_task, enabled)
        backButtonSaves = enabled
    }

    fun updateShowComments(enabled: Boolean) {
        preferences.setBoolean(R.string.p_show_task_edit_comments, enabled)
        showComments = enabled
    }

    fun updateShowWithoutUnlock(enabled: Boolean) {
        preferences.setBoolean(R.string.p_show_edit_screen_without_unlock, enabled)
        showWithoutUnlock = enabled
    }

    fun getMillisOfDay(prefKey: Int, defaultRes: Int): Int =
        preferences.getInt(prefKey, context.resources.getInteger(defaultRes))

    sealed interface TimePickerResult {
        data object Success : TimePickerResult
        data class Error(val messageResId: Int, val setting: Int, val relative: Int) : TimePickerResult
    }

    fun handleMorningResult(millisOfDay: Int): TimePickerResult {
        if (millisOfDay >= getMillisOfDay(R.string.p_date_shortcut_afternoon, R.integer.default_afternoon)) {
            return TimePickerResult.Error(
                R.string.date_shortcut_must_come_before,
                R.string.date_shortcut_morning,
                R.string.date_shortcut_afternoon,
            )
        }
        preferences.setInt(R.string.p_date_shortcut_morning, millisOfDay)
        refreshMorning()
        return TimePickerResult.Success
    }

    fun handleAfternoonResult(millisOfDay: Int): TimePickerResult {
        if (millisOfDay <= getMillisOfDay(R.string.p_date_shortcut_morning, R.integer.default_morning)) {
            return TimePickerResult.Error(
                R.string.date_shortcut_must_come_after,
                R.string.date_shortcut_afternoon,
                R.string.date_shortcut_morning,
            )
        }
        if (millisOfDay >= getMillisOfDay(R.string.p_date_shortcut_evening, R.integer.default_evening)) {
            return TimePickerResult.Error(
                R.string.date_shortcut_must_come_before,
                R.string.date_shortcut_afternoon,
                R.string.date_shortcut_evening,
            )
        }
        preferences.setInt(R.string.p_date_shortcut_afternoon, millisOfDay)
        refreshAfternoon()
        return TimePickerResult.Success
    }

    fun handleEveningResult(millisOfDay: Int): TimePickerResult {
        if (millisOfDay <= getMillisOfDay(R.string.p_date_shortcut_afternoon, R.integer.default_afternoon)) {
            return TimePickerResult.Error(
                R.string.date_shortcut_must_come_after,
                R.string.date_shortcut_evening,
                R.string.date_shortcut_afternoon,
            )
        }
        if (millisOfDay >= getMillisOfDay(R.string.p_date_shortcut_night, R.integer.default_night)) {
            return TimePickerResult.Error(
                R.string.date_shortcut_must_come_before,
                R.string.date_shortcut_evening,
                R.string.date_shortcut_night,
            )
        }
        preferences.setInt(R.string.p_date_shortcut_evening, millisOfDay)
        refreshEvening()
        return TimePickerResult.Success
    }

    fun handleNightResult(millisOfDay: Int): TimePickerResult {
        if (millisOfDay <= getMillisOfDay(R.string.p_date_shortcut_evening, R.integer.default_evening)) {
            return TimePickerResult.Error(
                R.string.date_shortcut_must_come_after,
                R.string.date_shortcut_night,
                R.string.date_shortcut_evening,
            )
        }
        preferences.setInt(R.string.p_date_shortcut_night, millisOfDay)
        refreshNight()
        return TimePickerResult.Success
    }

    private fun formatTime(millisOfDay: Int): String =
        formatTime(
            currentTimeMillis().withMillisOfDay(millisOfDay),
            context.is24HourFormat,
        )

    private fun refreshMorning() {
        morningSummary = formatTime(
            getMillisOfDay(R.string.p_date_shortcut_morning, R.integer.default_morning)
        )
    }

    private fun refreshAfternoon() {
        afternoonSummary = formatTime(
            getMillisOfDay(R.string.p_date_shortcut_afternoon, R.integer.default_afternoon)
        )
    }

    private fun refreshEvening() {
        eveningSummary = formatTime(
            getMillisOfDay(R.string.p_date_shortcut_evening, R.integer.default_evening)
        )
    }

    private fun refreshNight() {
        nightSummary = formatTime(
            getMillisOfDay(R.string.p_date_shortcut_night, R.integer.default_night)
        )
    }
}
