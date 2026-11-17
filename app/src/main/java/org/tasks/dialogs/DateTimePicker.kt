package org.tasks.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import org.tasks.compose.edit.SeriesEditScopeDialog
import org.tasks.compose.pickers.DueDatePickerSheet
import org.tasks.compose.pickers.MULTIPLE_DAYS
import org.tasks.compose.pickers.MULTIPLE_TIMES
import org.tasks.compose.pickers.NO_DAY
import org.tasks.compose.pickers.NO_TIME
import org.tasks.data.TaskSaver
import org.tasks.data.createDueDate
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.Task
import org.tasks.date.DateTimeUtils.newDateTime
import org.tasks.date.DateTimeUtils.toDateTime
import org.tasks.extensions.Context.is24HourFormat
import org.tasks.repeats.SeriesEditScope
import org.tasks.repeats.SeriesScheduler
import org.tasks.themes.TasksTheme
import org.tasks.time.millisOfDay
import org.tasks.time.startOfDay
import javax.inject.Inject

@AndroidEntryPoint
class DateTimePicker : BaseDateTimePicker() {

    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var taskSaver: TaskSaver
    @Inject lateinit var seriesScheduler: SeriesScheduler

    private val today = newDateTime().startOfDay()

    companion object {
        const val EXTRA_DAY = "extra_day"
        const val EXTRA_TIME = "extra_time"
        const val EXTRA_TASKS = "extra_tasks"
        const val EXTRA_HIDE_NO_DATE = "extra_hide_no_date"
        private const val EXTRA_ASK_SCOPE = "extra_ask_scope"

        fun newDateTimePicker(
            autoClose: Boolean,
            vararg tasks: Task,
        ): DateTimePicker {
            val fragment = DateTimePicker()
            val dueDates = tasks.map { it.dueDate.startOfDay() }.toSet()
            val dueTimes = tasks.map { it.dueDate.millisOfDay }.toSet()
            fragment.arguments = Bundle().apply {
                putLongArray(EXTRA_TASKS, tasks.map { it.id }.toLongArray())
                putLong(EXTRA_DAY, if (dueDates.size == 1) dueDates.first() else MULTIPLE_DAYS)
                putInt(EXTRA_TIME, if (dueTimes.size == 1) dueTimes.first() else MULTIPLE_TIMES)
                putBoolean(EXTRA_HIDE_NO_DATE, tasks.any { it.isRecurring || it.series != null })
                putBoolean(EXTRA_ASK_SCOPE, tasks.any { it.series != null })
                putBoolean(EXTRA_AUTO_CLOSE, autoClose)
            }
            return fragment
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        TasksTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            val args = requireArguments()
            // the picked day and time, waiting for "this occurrence or the schedule"
            var pending by rememberSaveable { mutableStateOf<Pair<Long, Int>?>(null) }
            val selection = pending
            if (selection == null) {
                DueDatePickerSheet(
                    initialDay = args.getLong(EXTRA_DAY),
                    initialTime = args.getInt(EXTRA_TIME),
                    is24Hour = requireContext().is24HourFormat,
                    autoClose = autoclose,
                    showNoDate = !args.getBoolean(EXTRA_HIDE_NO_DATE, false),
                    times = preferences.quickPickTimes,
                    onSelected = { day, time ->
                        if (args.getBoolean(EXTRA_ASK_SCOPE) && changed(day, time)) {
                            pending = day to time
                        } else {
                            save(day, time, SeriesEditScope.OCCURRENCE)
                        }
                    },
                    onDismiss = { onDismissHandler?.onDismiss() ?: dismiss() },
                )
            } else {
                SeriesEditScopeDialog(
                    onSelected = { save(selection.first, selection.second, it) },
                    onDismiss = { dismiss() },
                )
            }
        }
    }

    private fun changed(selectedDay: Long, selectedTime: Int): Boolean {
        val args = requireArguments()
        return selectedDay != args.getLong(EXTRA_DAY) || selectedTime != args.getInt(EXTRA_TIME)
    }

    /** OCCURRENCE moves the dates (a postpone for occurrences); SCHEDULE then moves their series. */
    private fun save(selectedDay: Long, selectedTime: Int, scope: SeriesEditScope) {
        val args = requireArguments()
        if (changed(selectedDay, selectedTime)) {
            val taskIds = args.getLongArray(EXTRA_TASKS) ?: longArrayOf()
            lifecycleScope.launch(NonCancellable) {
                // one task per series: the schedule moves once, from that task's old date
                val moved = LinkedHashMap<String, Task>()
                taskDao
                    .fetch(taskIds.toList())
                    .forEach {
                        val original = it.copy()
                        it.series?.let { series -> moved.putIfAbsent(series, original) }
                        val day = if (selectedDay == MULTIPLE_DAYS) {
                            if (it.hasDueDate()) it.dueDate else today.millis
                        } else {
                            selectedDay
                        }
                        val time = if (selectedTime == MULTIPLE_TIMES) {
                            if (it.hasDueTime()) it.dueDate.millisOfDay else NO_TIME
                        } else {
                            selectedTime
                        }
                        it.setDueDateAdjustingHideUntil(
                            when {
                                day == NO_DAY -> 0L
                                time == NO_TIME -> createDueDate(
                                    Task.URGENCY_SPECIFIC_DAY,
                                    day
                                )

                                else -> createDueDate(
                                    Task.URGENCY_SPECIFIC_DAY_TIME,
                                    day.toDateTime().withMillisOfDay(time).millis
                                )
                            }
                        )
                        taskSaver.save(it, original)
                    }
                if (scope == SeriesEditScope.SCHEDULE) {
                    moved.values.forEach { seriesScheduler.editSchedule(it.id, null, it.dueDate) }
                }
            }
        }
        dismiss()
    }
}
