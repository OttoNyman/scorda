package org.tasks.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.tasks.injection.ApplicationScope
import org.tasks.pins.TaskPinner
import javax.inject.Inject

/** «Открепить» on a pinned card, and a swiped pinned card that has to come back. */
@AndroidEntryPoint
class PinReceiver : BroadcastReceiver() {
    @Inject lateinit var taskPinner: TaskPinner
    @Inject lateinit var pinNotifier: PinNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_UNPIN -> if (taskId > 0) taskPinner.unpin(listOf(taskId))
                    ACTION_SWIPED -> if (taskId > 0) pinNotifier.show(taskId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_UNPIN = "org.tasks.action.UNPIN"
        const val ACTION_SWIPED = "org.tasks.action.PIN_SWIPED"
        const val EXTRA_TASK_ID = "pin_task_id"
    }
}
