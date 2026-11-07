package com.todoroo.astrid.activity

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todoroo.astrid.activity.MainActivity.Companion.LOAD_FILTER
import com.todoroo.astrid.activity.MainActivity.Companion.OPEN_FILTER
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.widget.Toast
import org.tasks.resources.getString
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.default_list
import tasks.kmp.generated.resources.inbox_list
import tasks.kmp.generated.resources.lists_on_message
import kotlinx.coroutines.runBlocking
import org.tasks.R
import org.tasks.analytics.Firebase
import org.tasks.caldav.TasksAccountDataRepository
import org.tasks.data.dao.CaldavDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.Task
import org.tasks.data.getOrCreateLocalAccount
import org.tasks.constructor.fits
import org.tasks.filters.CaldavFilter
import org.tasks.filters.Filter
import org.tasks.filters.SearchFilter
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.preferences.TasksPreferences
import org.tasks.viewmodel.OnboardingRouting
import org.tasks.viewmodel.OnboardingState
import org.tasks.viewmodel.DrawerViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val defaultFilterProvider: DefaultFilterProvider,
    @ApplicationContext private val applicationContext: Context,
    private val caldavDao: CaldavDao,
    private val accountDataRepository: TasksAccountDataRepository,
    private val firebase: Firebase,
    private val tasksPreferences: TasksPreferences,
    private val featureGate: org.tasks.constructor.FeatureGate,
    private val constructorRepository: org.tasks.constructor.ConstructorRepository,
    val drawerViewModel: DrawerViewModel,
) : ViewModel() {

    data class State(
        val filter: Filter,
        val task: Task? = null,
    )

    private val _state = MutableStateFlow(
        State(
            filter = savedStateHandle.get<Filter>(OPEN_FILTER)
                ?: savedStateHandle.get<String>(LOAD_FILTER)?.let {
                    runBlocking { defaultFilterProvider.getFilterFromPreference(it) }
                }
                ?: runBlocking {
                    val startup = defaultFilterProvider.getStartupFilter()
                    if (startup.fits(featureGate.current)) startup else org.tasks.filters.MyTasksFilter.create()
                },
            task = savedStateHandle.get<Task>(EXTRA_TASK),
        )
    )
    val state = _state.asStateFlow()

    companion object {
        private const val EXTRA_TASK = "extra_task"
        private const val KEY_WAS_IN_ONBOARDING = "was_in_onboarding"
        private const val KEY_WAS_IN_CLOUD_ONBOARDING = "was_in_cloud_onboarding"

    }

    val accountExists: Flow<Boolean>
        get() = caldavDao.watchAccountExists()

    val needsCloudOnboarding: StateFlow<Boolean?> =
        tasksPreferences.flow(TasksPreferences.needsCloudOnboarding, false)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var wasInOnboarding: Boolean
        get() = savedStateHandle[KEY_WAS_IN_ONBOARDING] ?: false
        set(value) { savedStateHandle[KEY_WAS_IN_ONBOARDING] = value }

    private var wasInCloudOnboarding: Boolean
        get() = savedStateHandle[KEY_WAS_IN_CLOUD_ONBOARDING] ?: false
        set(value) { savedStateHandle[KEY_WAS_IN_CLOUD_ONBOARDING] = value }

    fun routeOnboarding(
        hasAccount: Boolean?,
        needsCloudOnboarding: Boolean?,
        isImporting: Boolean,
    ): OnboardingRouting {
        val routing = org.tasks.viewmodel.routeOnboarding(
            OnboardingState(wasInOnboarding, wasInCloudOnboarding),
            hasAccount,
            needsCloudOnboarding,
            isImporting,
        )
        wasInOnboarding = routing.state.wasInOnboarding
        wasInCloudOnboarding = routing.state.wasInCloudOnboarding
        return routing
    }

    suspend fun logOnboardingComplete() {
        if (!tasksPreferences.get(TasksPreferences.hasLoggedOnboardingComplete, false)) {
            firebase.logEvent(R.string.event_onboarding_complete)
            tasksPreferences.set(TasksPreferences.hasLoggedOnboardingComplete, true)
        }
    }

    init {
        drawerViewModel.setSelectedFilter(_state.value.filter)
        watchFeatures()
    }

    /**
     * Turning lists on opens the default list and says that every task is there. Any other change
     * that hides what is on screen goes back to all tasks.
     */
    private fun watchFeatures() = viewModelScope.launch {
        var listsWere = constructorRepository.effective.value.lists
        constructorRepository.effective.drop(1).collect { features ->
            if (features.lists && !listsWere) {
                val list = inboxNamed(defaultFilterProvider.getDefaultList())
                setFilter(list)
                Toast.makeText(
                    applicationContext,
                    getString(Res.string.lists_on_message, list.title.orEmpty()),
                    Toast.LENGTH_LONG,
                ).show()
            } else if (!_state.value.filter.fits(features)) {
                setFilter(org.tasks.filters.MyTasksFilter.create())
            }
            listsWere = features.lists
        }
    }

    /** The hidden local list still carries the old default name: it becomes Inbox when it shows up. */
    private suspend fun inboxNamed(list: CaldavFilter): CaldavFilter {
        if (!list.account.isLocalList || list.calendar.name != getString(Res.string.default_list)) return list
        val renamed = list.calendar.copy(name = getString(Res.string.inbox_list))
        caldavDao.update(renamed)
        return list.copy(calendar = renamed)
    }

    suspend fun resetFilter() {
        setFilter(defaultFilterProvider.getDefaultOpenFilter())
    }

    fun setFilter(
        filter: Filter,
        task: Task? = null,
        remember: Boolean = true,
    ) {
        if (filter == _state.value.filter && task == null) {
            return
        }
        savedStateHandle[EXTRA_TASK] = task
        _state.update {
            it.copy(
                filter = filter,
                task = task,
            )
        }
        drawerViewModel.setSelectedFilter(filter)
        if (remember && filter !is SearchFilter) {
            defaultFilterProvider.setLastViewedFilter(filter)
        }
    }

    fun setTask(task: Task?) {
        savedStateHandle[EXTRA_TASK] = task
        _state.update { it.copy(task = task) }
    }

    suspend fun getAccount(id: Long) = caldavDao.getAccount(id)

    suspend fun getOrCreateLocalAccount(): CaldavAccount = caldavDao.getOrCreateLocalAccount()

    suspend fun isTasksGuest(): Boolean =
        try {
            val response = accountDataRepository.getAccountResponse()
                ?: accountDataRepository.fetchAndCache()
            response?.guest ?: false
        } catch (e: Exception) {
            Timber.e(e, "Failed to check guest status")
            false
        }

    fun openLastViewedFilter() = viewModelScope.launch {
        setFilter(defaultFilterProvider.getLastViewedFilter())
    }
}
