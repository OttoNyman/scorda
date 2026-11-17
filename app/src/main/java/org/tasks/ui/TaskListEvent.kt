package org.tasks.ui

import kotlinx.coroutines.flow.MutableSharedFlow

typealias TaskListEventBus = MutableSharedFlow<TaskListEvent>

sealed class TaskListEvent {
    data class TaskCreated(val uuid: String) : TaskListEvent()
}
