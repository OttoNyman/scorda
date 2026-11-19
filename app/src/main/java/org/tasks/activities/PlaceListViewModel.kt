package org.tasks.activities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.data.dao.LocationDao
import org.tasks.data.entity.Place
import org.tasks.filters.PlaceFilter
import org.tasks.filters.key
import org.tasks.preferences.FilterPreferences.Companion.delete
import org.tasks.preferences.TasksPreferences
import javax.inject.Inject

@HiltViewModel
class PlaceListViewModel @Inject constructor(
    private val locationDao: LocationDao,
    private val tasksPreferences: TasksPreferences,
    private val refreshBroadcaster: RefreshBroadcaster,
) : ViewModel() {

    var places by mutableStateOf<List<Place>>(emptyList())
        private set

    fun refresh() {
        viewModelScope.launch {
            places = locationDao.getPlaces().sortedBy { it.displayName.lowercase() }
        }
    }

    fun delete(place: Place) {
        viewModelScope.launch {
            place.uid?.let { locationDao.deleteGeofencesByPlace(it) }
            locationDao.delete(place)
            tasksPreferences.delete(PlaceFilter(place).key())
            refreshBroadcaster.broadcastRefresh()
            places = locationDao.getPlaces().sortedBy { it.displayName.lowercase() }
        }
    }
}
