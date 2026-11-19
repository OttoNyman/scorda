package org.tasks.reminders

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.todoroo.astrid.alarms.AlarmService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.tasks.data.dao.TaskDao
import org.tasks.notifications.NotificationActions
import org.tasks.notifications.Ringer
import org.tasks.notifications.TEST_NOTIFICATION_ID
import org.tasks.preferences.Preferences
import org.tasks.receivers.CompleteTaskReceiver
import org.tasks.themes.TasksTheme
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.no_title
import tasks.kmp.generated.resources.rmd_NoA_done
import tasks.kmp.generated.resources.rmd_NoA_snooze
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var alarmService: AlarmService
    @Inject lateinit var ringer: Ringer

    private var taskId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (taskId == 0L) {
            finish()
            return
        }
        val readOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false)
        setContent {
            TasksTheme {
                var title by remember { mutableStateOf(intent.getStringExtra(EXTRA_TITLE).orEmpty()) }
                val fallbackTitle = stringResource(Res.string.no_title)
                LaunchedEffect(taskId) {
                    if (title.isBlank()) {
                        title = taskDao.fetch(taskId)?.title.orEmpty()
                    }
                    if (title.isBlank()) {
                        title = fallbackTitle
                    }
                    while (isActive) {
                        val stillUp = NotificationManagerCompat.from(this@AlarmActivity)
                            .activeNotifications
                            .any { it.id.toLong() == taskId }
                        if (!stillUp) {
                            finish()
                            break
                        }
                        delay(500)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(title, style = MaterialTheme.typography.headlineMedium)
                    if (!readOnly) {
                        Button(
                            onClick = { complete() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.rmd_NoA_done))
                        }
                    }
                    OutlinedButton(
                        onClick = { snooze() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.rmd_NoA_snooze))
                    }
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private fun unlockThen(action: () -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true && Build.VERSION.SDK_INT >= 26) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = action()
                override fun onDismissCancelled() {}
                override fun onDismissError() = action()
            })
        } else {
            action()
        }
    }

    private fun closeTest() {
        NotificationManagerCompat.from(this).cancel(taskId.toInt())
        ringer.stop(listOf(taskId))
        finish()
    }

    private fun complete() {
        if (taskId == TEST_NOTIFICATION_ID.toLong()) {
            closeTest()
            return
        }
        unlockThen {
            NotificationActions.markCompleting(taskId)
            sendBroadcast(
                Intent(this, CompleteTaskReceiver::class.java)
                    .putExtra(CompleteTaskReceiver.TASK_ID, taskId)
            )
            ringer.stop(listOf(taskId))
            finish()
        }
    }

    private fun snooze() {
        if (taskId == TEST_NOTIFICATION_ID.toLong()) {
            closeTest()
            return
        }
        unlockThen {
            lifecycleScope.launch {
                var snoozeTime = preferences.swipeToSnoozeIntervalMS()
                if (snoozeTime == 0L) snoozeTime = 60_000L
                alarmService.snooze(
                    time = currentTimeMillis() + snoozeTime,
                    taskIds = listOf(taskId),
                )
                ringer.stop(listOf(taskId))
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_READ_ONLY = "extra_read_only"

        fun newIntent(
            context: Context,
            title: String?,
            id: Long,
            readOnly: Boolean = false,
        ): Intent =
            Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TASK_ID, id)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_READ_ONLY, readOnly)
            }
    }
}
