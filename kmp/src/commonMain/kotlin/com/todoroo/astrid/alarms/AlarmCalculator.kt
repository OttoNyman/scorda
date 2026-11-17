package com.todoroo.astrid.alarms

import org.tasks.constructor.FeatureGate
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Notification
import org.tasks.data.entity.Task
import org.tasks.reminders.Random
import org.tasks.reminders.isAdvance
import org.tasks.time.withMillisOfDay

class AlarmCalculator(
    private val random: Random,
    private val gate: FeatureGate? = null,
) {
    fun toAlarmEntry(task: Task, alarm: Alarm, defaultDueTime: Int): Notification? {
        if (gate != null && !gate.current.schedules(alarm)) {
            return null
        }
        val trigger = when (alarm.type) {
            Alarm.TYPE_SNOOZE,
            Alarm.TYPE_DATE_TIME ->
                alarm.time
            Alarm.TYPE_REL_START ->
                when {
                    task.hasStartTime() ->
                        task.hideUntil + alarm.time
                    task.hasStartDate() ->
                        task.hideUntil.withMillisOfDay(defaultDueTime) + alarm.time
                    else ->
                        AlarmService.NO_ALARM
                }
            Alarm.TYPE_REL_END ->
                when {
                    task.hasDueTime() ->
                        task.dueDate + alarm.time
                    task.hasDueDate() ->
                        task.dueDate.withMillisOfDay(defaultDueTime) + alarm.time
                    else ->
                        AlarmService.NO_ALARM
                }
            Alarm.TYPE_RANDOM ->
                calculateNextRandomReminder(random, task, alarm.time)
            else ->
                AlarmService.NO_ALARM
        }
        return when {
            trigger <= AlarmService.NO_ALARM ->
                null
            // "in advance" whose moment had passed when the task was saved (due in 10 min, ring
            // 15 min early; an occurrence emitted one short period ahead): no instant ring
            alarm.isAdvance() && trigger < maxOf(task.creationDate, task.modificationDate) ->
                null
            trigger > task.reminderLast || alarm.type == Alarm.TYPE_SNOOZE ->
                Notification(taskId = alarm.task, timestamp = trigger, type = alarm.type)
            alarm.repeat > 0 -> {
                val past = (task.reminderLast - trigger) / alarm.interval
                val next = trigger + (past + 1) * alarm.interval
                if (past < alarm.repeat && next > task.reminderLast) {
                    Notification(taskId = alarm.task, timestamp = next, type = alarm.type)
                } else {
                    null
                }
            }
            else ->
                null
        }
    }

    private fun calculateNextRandomReminder(random: Random, task: Task, reminderPeriod: Long) =
        if (reminderPeriod > 0) {
            val baseline = when {
                task.reminderLast > 0 -> task.reminderLast
                task.isRecurring -> task.modificationDate
                else -> task.creationDate
            }

            val multiplier = 0.85f + 0.3f * random.nextFloat(task.id + baseline)

            maxOf(
                baseline.plus((reminderPeriod * multiplier).toLong()),
                task.hideUntil
            )
        } else {
            AlarmService.NO_ALARM
        }
}
