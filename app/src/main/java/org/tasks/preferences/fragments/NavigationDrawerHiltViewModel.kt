package org.tasks.preferences.fragments

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.constructor.ConstructorRepository
import org.tasks.injection.ApplicationScope
import org.tasks.preferences.Preferences
import org.tasks.viewmodel.NavigationDrawerViewModel
import javax.inject.Inject

@HiltViewModel
class NavigationDrawerHiltViewModel @Inject constructor(
    preferences: Preferences,
    refreshBroadcaster: RefreshBroadcaster,
    @ApplicationScope persistenceScope: CoroutineScope,
    private val constructorRepository: ConstructorRepository,
) : NavigationDrawerViewModel(
    appPreferences = preferences,
    refreshBroadcaster = refreshBroadcaster,
    persistenceScope = persistenceScope,
) {
    /** The only «Фильтры» switch left: it also sets the constructor flag that used to have its own. */
    override fun updateFiltersEnabled(enabled: Boolean) {
        super.updateFiltersEnabled(enabled)
        constructorRepository.write(constructorRepository.raw.value.copy(filters = enabled))
    }
}
