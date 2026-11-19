package org.tasks.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tasks.PlatformConfiguration
import org.tasks.data.entity.Alarm
import org.tasks.notifications.ModeLine
import org.tasks.notifications.ReminderCheck
import org.tasks.notifications.ReminderCheckId
import org.tasks.preferences.AppPreferences
import org.tasks.preferences.NotificationSettings
import org.tasks.preferences.SwipeAction

enum class ReminderChange {
    PENDING,

    ON_SCREEN,

    OFF,
}

open class NotificationsViewModel(
    private val appPreferences: AppPreferences,
    platformConfiguration: PlatformConfiguration,
    private val persistenceScope: CoroutineScope,
    private val rescheduleNotifications: suspend (ReminderChange) -> Unit,
) : ViewModel() {

    enum class TimePickerTarget {
        DEFAULT_REMINDER,
    }

    val showTroubleshooting: Boolean = platformConfiguration.supportsNotificationTroubleshooting
    val showSystemNotificationSettings: Boolean =
        platformConfiguration.supportsSystemNotificationSettings
    val showOngoingNotifications: Boolean = platformConfiguration.supportsOngoingNotifications
    val showBundleNotifications: Boolean = platformConfiguration.supportsBundledNotifications
    val showVoiceReminders: Boolean = platformConfiguration.supportsVoiceReminders
    val showCompletionSound: Boolean = platformConfiguration.supportsCompletionSound
    val showSwipeToSnooze: Boolean = platformConfiguration.supportsSwipeToSnooze
    val showNotificationsEnabled: Boolean = platformConfiguration.showNotificationsEnabledSwitch

    var settings by mutableStateOf(NotificationSettings())
        private set

    var loaded by mutableStateOf(false)
        private set

    var timePickerInputMode by mutableStateOf(false)
        private set

    var showBatteryOptimization by mutableStateOf(false)
        protected set

    var completionSoundName by mutableStateOf("")
        protected set

    var showSnoozeDialog by mutableStateOf(false)
        private set

    var showSwipeDialog by mutableStateOf(false)

    var showSwipeHelp by mutableStateOf(false)

    var timePickerTarget by mutableStateOf<TimePickerTarget?>(null)
        private set

    var healthChecks by mutableStateOf<List<ReminderCheck>>(emptyList())
        private set

    var modeLine by mutableStateOf(ModeLine.ON_TIME)
        private set

    var healthExpanded by mutableStateOf(false)
        private set

    var moreExpanded by mutableStateOf(false)
        private set

    var testPending by mutableStateOf(false)
        private set

    var testSecondsLeft by mutableStateOf(0)
        private set

    var defaultAlarms by mutableStateOf<List<Alarm>>(emptyList())
        private set

    var levelSilentEnabled by mutableStateOf(false)
        protected set

    var levelPersistentEnabled by mutableStateOf(false)
        protected set

    var levelAlarmEnabled by mutableStateOf(true)
        protected set

    var pendingLevelDisable by mutableStateOf<org.tasks.compose.settings.PendingLevelDisable?>(null)
        protected set

    var alarmRingSeconds by mutableStateOf(60)
        private set

    var showAlarmDurationDialog by mutableStateOf(false)
        private set

    var superBypassSilent by mutableStateOf(false)
        protected set

    var onceSoundName by mutableStateOf("")
        protected set

    var persistentSoundName by mutableStateOf("")
        protected set

    var alarmSoundName by mutableStateOf("")
        protected set

    private var healthTouched = false

    private val writes = PreferenceWriteQueue(
        viewModelScope = viewModelScope,
        persistenceScope = persistenceScope,
        tag = TAG,
        reload = { reloadSafely() },
    )

    init {
        viewModelScope.launch {
            reloadSafely()
        }
    }

    protected suspend fun reload() {
        settings = appPreferences.notificationSettings()
        defaultAlarms = appPreferences.defaultAlarms()
        levelSilentEnabled = appPreferences.levelSilentEnabled()
        levelPersistentEnabled = appPreferences.levelPersistentEnabled()
        levelAlarmEnabled = appPreferences.levelAlarmEnabled()
        alarmRingSeconds = appPreferences.alarmRingSeconds()
        timePickerInputMode = appPreferences.datePickerPreferences().timePickerInputMode
        loaded = true
    }

    private suspend fun reloadSafely() {
        try {
            reload()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e, tag = TAG) { "Failed to reload notification settings" }
        }
    }

    open fun refreshState() = writes.refresh()

    private fun persist(block: suspend () -> Unit) = writes.write(block)

    fun updateNotificationsEnabled(enabled: Boolean) {
        settings = settings.copy(notificationsEnabled = enabled)
        persist {
            appPreferences.setNotificationsEnabled(enabled)
            rescheduleNotifications(if (enabled) ReminderChange.PENDING else ReminderChange.OFF)
        }
    }

    fun updatePersistent(enabled: Boolean) {
        settings = settings.copy(
            persistentNotifications = enabled,
            wearableNotifications = if (enabled) false else settings.wearableNotifications,
        )
        persist {
            appPreferences.setPersistentNotifications(enabled)
            if (enabled) {
                appPreferences.setWearableNotifications(false)
            }
            rescheduleNotifications(ReminderChange.PENDING)
        }
    }

    fun updateWearable(enabled: Boolean) {
        settings = settings.copy(
            wearableNotifications = enabled,
            persistentNotifications = if (enabled) false else settings.persistentNotifications,
        )
        persist {
            appPreferences.setWearableNotifications(enabled)
            if (enabled) {
                appPreferences.setPersistentNotifications(false)
            }
            rescheduleNotifications(ReminderChange.PENDING)
        }
    }

    fun updateBundle(enabled: Boolean) {
        settings = settings.copy(bundleNotifications = enabled)
        persist {
            appPreferences.setBundleNotifications(enabled)
            rescheduleNotifications(ReminderChange.ON_SCREEN)
        }
    }

    open fun updateVoice(enabled: Boolean) {
        settings = settings.copy(voiceReminders = enabled)
        persist { appPreferences.setVoiceReminders(enabled) }
    }

    fun updateSwipeAction(action: SwipeAction) {
        settings = settings.copy(swipeAction = action)
        showSwipeDialog = false
        persist { appPreferences.setSwipeAction(action) }
    }

    fun setSnoozeTime(minutes: Int) {
        settings = settings.copy(swipeToSnoozeMinutes = minutes)
        showSnoozeDialog = false
        persist { appPreferences.setSwipeToSnoozeMinutes(minutes) }
    }

    fun openSnoozeDialog() {
        showSnoozeDialog = true
    }

    fun dismissSnoozeDialog() {
        showSnoozeDialog = false
    }

    fun updateDefaultReminders(enabled: Boolean) {
        settings = settings.copy(defaultRemindersEnabled = enabled)
        persist { appPreferences.setDefaultRemindersEnabled(enabled) }
    }

    fun openTimePicker(target: TimePickerTarget) {
        timePickerTarget = target
    }

    fun dismissTimePicker() {
        timePickerTarget = null
    }

    fun timePickerInitialValue(target: TimePickerTarget): Int = when (target) {
        TimePickerTarget.DEFAULT_REMINDER -> settings.defaultReminderTime
    }

    fun setTime(target: TimePickerTarget, millisOfDay: Int) {
        when (target) {
            TimePickerTarget.DEFAULT_REMINDER -> {
                settings = settings.copy(defaultReminderTime = millisOfDay)
                persist {
                    appPreferences.setDefaultReminderTime(millisOfDay)
                    rescheduleNotifications(ReminderChange.PENDING)
                }
            }
        }
    }

    fun updateTimePickerInputMode(inputMode: Boolean) {
        timePickerInputMode = inputMode
        persist { appPreferences.setTimePickerInputMode(inputMode) }
    }

    fun setHealth(checks: List<ReminderCheck>, line: ModeLine) {
        healthChecks = checks
        modeLine = line
        if (!healthTouched) {
            healthExpanded = checks.any { it.state == org.tasks.notifications.CheckState.PROBLEM } ||
                line != ModeLine.ON_TIME
        }
    }

    fun toggleHealth() {
        healthTouched = true
        healthExpanded = !healthExpanded
    }

    fun toggleMore() {
        moreExpanded = !moreExpanded
    }

    fun markTestPending() {
        testPending = true
        testSecondsLeft = 10
        viewModelScope.launch {
            while (testSecondsLeft > 0) {
                delay(1_000)
                testSecondsLeft--
            }
            testPending = false
        }
    }

    open fun openHealthCheck(id: ReminderCheckId) = Unit

    open fun scheduleReminderTest() = Unit

    fun updateDefaultAlarms(alarms: List<Alarm>) {
        defaultAlarms = alarms
        persist { appPreferences.setDefaultAlarms(alarms) }
    }

    fun openAlarmDuration() {
        showAlarmDurationDialog = true
    }

    fun dismissAlarmDuration() {
        showAlarmDurationDialog = false
    }

    fun updateAlarmRingSeconds(seconds: Int) {
        alarmRingSeconds = seconds
        showAlarmDurationDialog = false
        persist { appPreferences.setAlarmRingSeconds(seconds) }
    }

    open fun updateSuperBypass(enabled: Boolean) {
        superBypassSilent = enabled
    }
}

private const val TAG = "NotificationsViewModel"
