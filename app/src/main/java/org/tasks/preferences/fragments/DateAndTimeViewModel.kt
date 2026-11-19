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
import org.tasks.kmp.org.tasks.time.DateFormats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DateAndTimeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: Preferences,
) : ViewModel() {

    var dateFormat by mutableStateOf(DateFormats.AUTO)
        private set

    /** Every offered format with how today looks in it. */
    val dateFormatOptions: List<Pair<String, String>>
        get() {
            val today = LocalDate.now()
            val locale = Locale.getDefault()
            val system = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(today)
            return listOf(
                DateFormats.AUTO to context.getString(R.string.date_format_auto),
                DateFormats.SYSTEM to context.getString(R.string.date_format_system, system),
            ) + DateFormats.patterns.map { it to DateTimeFormatter.ofPattern(it, locale).format(today) }
        }

    val dateFormatLabel: String
        get() = dateFormatOptions.firstOrNull { it.first == dateFormat }?.second.orEmpty()
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
        dateFormat = preferences.dateFormat
        refreshMorning()
        refreshAfternoon()
        refreshEvening()
        refreshNight()
    }

    fun updateDateFormat(value: String) {
        preferences.dateFormat = value
        dateFormat = value
    }

    fun getMillisOfDay(prefKey: Int, defaultRes: Int): Int =
        preferences.getInt(prefKey, context.resources.getInteger(defaultRes))

    fun setTimePreference(prefKey: Int, millisOfDay: Int) {
        preferences.setInt(prefKey, millisOfDay)
    }

    sealed interface TimePickerResult {
        data object Success : TimePickerResult
        data class Error(val messageResId: Int, val setting: Int, val relative: Int) : TimePickerResult
    }

    fun handleMorningResult(millisOfDay: Int): TimePickerResult {
        if (millisOfDay >= getMillisOfDay(R.string.p_date_shortcut_afternoon, R.integer.default_afternoon)) {
            return TimePickerResult.Error(R.string.date_shortcut_must_come_before, R.string.date_shortcut_morning, R.string.date_shortcut_afternoon)
        }
        setTimePreference(R.string.p_date_shortcut_morning, millisOfDay)
        refreshMorning()
        return TimePickerResult.Success
    }

    fun handleAfternoonResult(millisOfDay: Int): TimePickerResult {
        if (millisOfDay <= getMillisOfDay(R.string.p_date_shortcut_morning, R.integer.default_morning)) {
            return TimePickerResult.Error(R.string.date_shortcut_must_come_after, R.string.date_shortcut_afternoon, R.string.date_shortcut_morning)
        }
        if (millisOfDay >= getMillisOfDay(R.string.p_date_shortcut_evening, R.integer.default_evening)) {
            return TimePickerResult.Error(R.string.date_shortcut_must_come_before, R.string.date_shortcut_afternoon, R.string.date_shortcut_evening)
        }
        setTimePreference(R.string.p_date_shortcut_afternoon, millisOfDay)
        refreshAfternoon()
        return TimePickerResult.Success
    }

    fun handleEveningResult(millisOfDay: Int): TimePickerResult {
        if (millisOfDay <= getMillisOfDay(R.string.p_date_shortcut_afternoon, R.integer.default_afternoon)) {
            return TimePickerResult.Error(R.string.date_shortcut_must_come_after, R.string.date_shortcut_evening, R.string.date_shortcut_afternoon)
        }
        if (millisOfDay >= getMillisOfDay(R.string.p_date_shortcut_night, R.integer.default_night)) {
            return TimePickerResult.Error(R.string.date_shortcut_must_come_before, R.string.date_shortcut_evening, R.string.date_shortcut_night)
        }
        setTimePreference(R.string.p_date_shortcut_evening, millisOfDay)
        refreshEvening()
        return TimePickerResult.Success
    }

    fun handleNightResult(millisOfDay: Int): TimePickerResult {
        if (millisOfDay <= getMillisOfDay(R.string.p_date_shortcut_evening, R.integer.default_evening)) {
            return TimePickerResult.Error(R.string.date_shortcut_must_come_after, R.string.date_shortcut_night, R.string.date_shortcut_evening)
        }
        setTimePreference(R.string.p_date_shortcut_night, millisOfDay)
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
