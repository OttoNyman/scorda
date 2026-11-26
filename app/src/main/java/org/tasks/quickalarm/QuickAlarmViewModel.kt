package org.tasks.quickalarm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todoroo.astrid.service.TaskCreator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class QuickAlarmViewModel @Inject constructor(
    private val taskCreator: TaskCreator,
) : ViewModel() {
    var saving by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set

    private val failureEvents = Channel<Unit>(Channel.BUFFERED)
    val failures = failureEvents.receiveAsFlow()

    fun confirm(title: String?, dueAtMillis: Long, defaultTitle: String) {
        if (saving || done) return
        saving = true
        viewModelScope.launch {
            try {
                taskCreator.createQuickAlarm(title, dueAtMillis, defaultTitle)
                done = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                saving = false
                failureEvents.trySend(Unit)
            }
        }
    }
}
