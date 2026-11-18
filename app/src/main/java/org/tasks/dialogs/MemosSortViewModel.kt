package org.tasks.dialogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.memos.MemosPreferences
import org.tasks.preferences.TasksPreferences
import javax.inject.Inject

@HiltViewModel
class MemosSortViewModel @Inject constructor(
    private val tasksPreferences: TasksPreferences,
    private val refreshBroadcaster: RefreshBroadcaster,
) : ViewModel() {
    private val initial = MemosPreferences.read(tasksPreferences)
    private val _sortMode = MutableStateFlow(initial)
    val sortMode = _sortMode.asStateFlow()

    val changed: Boolean
        get() = initial != _sortMode.value

    fun setSortMode(mode: Int) {
        if (_sortMode.value == mode) return
        viewModelScope.launch {
            MemosPreferences.set(tasksPreferences, mode)
            _sortMode.value = mode
            refreshBroadcaster.broadcastRefresh()
        }
    }
}