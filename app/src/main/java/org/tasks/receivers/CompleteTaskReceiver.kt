package org.tasks.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.tasks.analytics.Firebase
import org.tasks.injection.ApplicationScope
import org.tasks.notifications.NotificationActions
import org.tasks.service.TaskCompleter
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class CompleteTaskReceiver : BroadcastReceiver() {
    @Inject lateinit var taskCompleter: TaskCompleter
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject lateinit var firebase: Firebase

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(TASK_ID, 0)
        if (taskId == 0L) return
        Timber.i("Completing %s", taskId)
        NotificationActions.markCompleting(taskId)
        val pending = goAsync()
        scope.launch {
            try {
                taskCompleter.setComplete(taskId)
                firebase.completeTask("notification")
            } finally {
                NotificationActions.clearCompleting(taskId)
                pending.finish()
            }
        }
    }

    companion object {
        const val TASK_ID = "id"
    }
}
