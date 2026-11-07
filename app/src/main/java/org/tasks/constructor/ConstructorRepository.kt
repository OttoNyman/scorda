package org.tasks.constructor

import com.todoroo.astrid.timers.TimerPlugin
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.tasks.LocalBroadcastManager
import org.tasks.PlatformConfiguration
import org.tasks.R
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.jobs.WorkManager
import org.tasks.location.LocationService
import org.tasks.preferences.Preferences
import org.tasks.sync.SyncSource
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConstructorRepository @Inject constructor(
    private val preferences: Preferences,
    private val caldavDao: CaldavDao,
    private val taskDao: TaskDao,
    private val gate: FeatureGate,
    private val timerPlugin: Lazy<TimerPlugin>,
    private val configuration: PlatformConfiguration,
    private val workManager: Lazy<WorkManager>,
    private val locationService: Lazy<LocationService>,
    private val localBroadcastManager: Lazy<LocalBroadcastManager>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rawFlow = MutableStateFlow(preferences.readConstructor())
    private val effectiveFlow = MutableStateFlow(compute(rawFlow.value))

    val raw: StateFlow<Constructor> = rawFlow.asStateFlow()
    val effective: StateFlow<Constructor> = effectiveFlow.asStateFlow()

    @Volatile
    private var hasGoogleAccount: Boolean = false

    /** Google Tasks account present and not paused. */
    val sync: Boolean
        get() = hasGoogleAccount && !preferences.getBoolean(R.string.p_google_tasks_paused, false)

    init {
        // Before the first account read: anything scheduled at startup already sees the saved flags.
        gate.current = effectiveFlow.value
        caldavDao.watchAccounts()
            .onEach { accounts ->
                hasGoogleAccount = accounts.any { it.accountType == CaldavAccount.TYPE_GOOGLE_TASKS }
                publish(rawFlow.value)
            }
            .launchIn(scope)
    }

    fun write(value: Constructor) {
        preferences.writeConstructor(value)
        rawFlow.value = value
        scope.launch { publish(value) }
    }

    /** Pause or resume changed: re-read [sync]. */
    fun refreshSync() {
        scope.launch { publish(rawFlow.value) }
    }

    /**
     * The next Google sync moves every local list to Google: into [target] when it is one of
     * [googleLists], otherwise each list under its own name.
     */
    fun moveLocalListsToGoogle(target: String? = null) {
        preferences.setBoolean(R.string.p_google_tasks_move_local, true)
        preferences.setString(R.string.p_google_tasks_move_target, target)
        scope.launch {
            try {
                workManager.get().sync(SyncSource.USER_INITIATED)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    /** Google lists the app keeps, as uuid to name, Scorda first. */
    suspend fun googleLists(): List<Pair<String, String>> {
        val account = caldavDao.getAccounts(CaldavAccount.TYPE_GOOGLE_TASKS).firstOrNull() ?: return emptyList()
        val scorda = preferences.getStringValue(R.string.p_google_tasks_list_id)
        return caldavDao.getCalendarsByAccount(account.uuid!!)
            .mapNotNull { c -> c.uuid?.let { it to (c.name ?: it) } }
            .sortedBy { it.first != scorda }
    }

    suspend fun counts(now: Long): ConstructorCounts {
        fun open(where: String) =
            "SELECT COUNT(1) FROM tasks WHERE deleted = 0 AND completed = 0 AND $where"
        return ConstructorCounts(
            repeat = taskDao.count(open("recurrence IS NOT NULL AND recurrence != ''")),
            overdue = taskDao.count(
                """
                SELECT COUNT(DISTINCT tasks._id) FROM tasks
                INNER JOIN alarms ON alarms.task = tasks._id
                WHERE tasks.deleted = 0 AND tasks.completed = 0
                AND alarms.type = 2 AND alarms.time > 0
                """.trimIndent()
            ),
            geo = taskDao.count(
                """
                SELECT COUNT(DISTINCT tasks._id) FROM tasks
                INNER JOIN geofences ON geofences.task = tasks._id
                WHERE tasks.deleted = 0 AND tasks.completed = 0
                """.trimIndent()
            ),
            calendar = taskDao.count(open("calendarUri IS NOT NULL AND calendarUri != ''")),
            extraReminders = taskDao.count(
                """
                SELECT COUNT(DISTINCT tasks._id) FROM tasks
                INNER JOIN alarms ON alarms.task = tasks._id
                WHERE tasks.deleted = 0 AND tasks.completed = 0 AND (
                    alarms.type IN (0, 3)
                    OR (alarms.type = 2 AND alarms.time < 0)
                    OR (alarms.type = 2 AND alarms.time = 0 AND alarms.repeat > 0)
                )
                """.trimIndent()
            ),
            startDate = taskDao.count(open("hideUntil > $now")),
            localLists = taskDao.count(
                """
                SELECT COUNT(1) FROM caldav_lists
                INNER JOIN caldav_accounts ON cda_uuid = cdl_account
                WHERE cda_account_type = ${CaldavAccount.TYPE_LOCAL}
                """.trimIndent()
            ),
        )
    }

    private fun compute(raw: Constructor) = raw.effective(
        sync = sync,
        geofences = configuration.supportsGeofences,
        calendarEvents = configuration.supportsCalendarEvents,
    )

    @Synchronized
    private fun apply(raw: Constructor): Pair<Constructor, Constructor>? {
        val previous = effectiveFlow.value
        val value = compute(raw)
        gate.sync = sync
        if (value == previous) return null
        effectiveFlow.value = value
        gate.current = value
        return previous to value
    }

    private suspend fun publish(raw: Constructor) {
        val (previous, value) = apply(raw) ?: return
        try {
            if (previous.timer && !value.timer) {
                taskDao.runningTimers().forEach { timerPlugin.get().stopTimer(it) }
            }
            if (previous.geo != value.geo) {
                locationService.get().registerAllGeofences()
            }
            workManager.get().triggerNotifications()
            localBroadcastManager.get().broadcastRefresh()
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}
