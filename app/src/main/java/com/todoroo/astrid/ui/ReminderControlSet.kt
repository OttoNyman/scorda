package com.todoroo.astrid.ui

import android.Manifest
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.todoroo.andlib.utility.AndroidUtilities
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.R
import org.tasks.activities.DateAndTimePickerActivity
import org.tasks.compose.edit.AlarmRow
import org.tasks.compose.rememberReminderPermissionState
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Alarm.Companion.TYPE_DATE_TIME
import org.tasks.date.DateTimeUtils
import org.tasks.extensions.Context.openReminderSettings
import org.tasks.reminders.advance
import kotlinx.collections.immutable.toPersistentSet
import org.tasks.scheduling.NotificationSchedulerWork
import org.tasks.ui.TaskEditControlFragment
import javax.inject.Inject

@AndroidEntryPoint
class ReminderControlSet : TaskEditControlFragment() {
    @Inject lateinit var activity: Activity
    @Inject lateinit var featureGate: org.tasks.constructor.FeatureGate

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    override fun Content() {
        val hasReminderPermissions by rememberReminderPermissionState()
        val notificationPermissions = if (AndroidUtilities.atLeastTiramisu()) {
            rememberPermissionState(
                Manifest.permission.POST_NOTIFICATIONS,
                onPermissionResult = { success ->
                    if (success) {
                        NotificationSchedulerWork.enqueueWork(requireContext())
                    }
                }
            )
        } else {
            null
        }
        val pickDateAndTime =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode != RESULT_OK) return@rememberLauncherForActivityResult
                val data = result.data ?: return@rememberLauncherForActivityResult
                val timestamp =
                    data.getLongExtra(DateAndTimePickerActivity.EXTRA_TIMESTAMP, 0L)
                val replace: Alarm? = data.getParcelableExtra(EXTRA_REPLACE)
                replace?.let { viewModel.removeAlarm(it) }
                viewModel.addAlarm(Alarm(time = timestamp, type = TYPE_DATE_TIME))
            }
        val viewState = viewModel.viewState.collectAsStateWithLifecycle().value
        val startDate = viewModel.startDate.collectAsStateWithLifecycle().value
        val dueDate = viewModel.dueDate.collectAsStateWithLifecycle().value
        val context = LocalContext.current
        AlarmRow(
            // with "In advance" on, its early ring lives in that row, not in this list
            alarms = if (featureGate.current.advance) {
                viewState.alarms.advance()?.let { advance -> viewState.alarms.filterNot { it === advance }.toPersistentSet() }
                    ?: viewState.alarms
            } else {
                viewState.alarms
            },
            hasNotificationPermissions = hasReminderPermissions &&
                    (notificationPermissions == null || notificationPermissions.status == PermissionStatus.Granted),
            fixNotificationPermissions = {
                if (hasReminderPermissions) {
                    notificationPermissions?.launchPermissionRequest()
                } else {
                    context.openReminderSettings()
                }
            },
            addAlarm = viewModel::addAlarm,
            isNew = viewState.isNew,
            hasStartDate = startDate > 0,
            hasDueDate = dueDate > 0,
            deleteAlarm = viewModel::removeAlarm,
            pickDateAndTime = { replace ->
                val timestamp = replace?.takeIf { it.type == TYPE_DATE_TIME }?.time
                    ?: DateTimeUtils.newDateTime().noon().millis
                pickDateAndTime.launch(
                    Intent(activity, DateAndTimePickerActivity::class.java)
                        .putExtra(DateAndTimePickerActivity.EXTRA_TIMESTAMP, timestamp)
                        .putExtra(EXTRA_REPLACE, replace)
                )
            }
        )
    }

    companion object {
        val TAG = R.string.TEA_ctrl_reminders_pref
        private const val EXTRA_REPLACE = "extra_replace"
    }
}
