package org.tasks.preferences.fragments

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import androidx.core.net.toUri
import com.todoroo.astrid.voice.VoiceOutputAssistant
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.tasks.PlatformConfiguration
import org.tasks.R
import org.tasks.compose.settings.PendingLevelDisable
import org.tasks.data.TaskSaver
import org.tasks.data.dao.SeriesDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.Task
import org.tasks.injection.ApplicationScope
import org.tasks.extensions.Context.getResourceUri
import org.tasks.extensions.Context.canScheduleExactAlarms
import org.tasks.notifications.AlertLevel
import org.tasks.notifications.AndroidReminderHealth
import org.tasks.notifications.OemFamily
import org.tasks.notifications.ReminderCheckId
import org.tasks.notifications.modeLine
import org.tasks.notifications.oemFamily
import org.tasks.notifications.priorityOf
import org.tasks.notifications.visibleChecks
import org.tasks.viewmodel.LockScreenAsk
import org.tasks.preferences.Preferences
import org.tasks.repeats.SeriesTemplates
import org.tasks.resources.getString
import org.tasks.scheduling.NotificationSchedulerWork
import org.tasks.viewmodel.NotificationsViewModel
import org.tasks.viewmodel.ReminderChange
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.reminder_sound_system
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class NotificationsHiltViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: Preferences,
    private val voiceOutputAssistant: VoiceOutputAssistant,
    private val taskDao: TaskDao,
    private val seriesDao: SeriesDao,
    private val seriesTemplates: SeriesTemplates,
    private val taskSaver: TaskSaver,
    platformConfiguration: PlatformConfiguration,
    @ApplicationScope persistenceScope: CoroutineScope,
) : NotificationsViewModel(
    appPreferences = preferences,
    platformConfiguration = platformConfiguration,
    persistenceScope = persistenceScope,
    rescheduleNotifications = { change ->
        NotificationSchedulerWork.enqueueWork(
            context,
            cancelNotifications = change == ReminderChange.ON_SCREEN,
        )
    },
) {

    private val ttsChecks = Channel<Unit>(Channel.CONFLATED)
    val ttsCheckRequests: Flow<Unit> = ttsChecks.receiveAsFlow()
    private val health = AndroidReminderHealth(context)

    init {
        refreshAndroidState()
    }

    override fun refreshState() {
        super.refreshState()
        settleLockScreen()
        settleExactAlarm()
        refreshAndroidState()
    }

    private fun settleLockScreen() {
        when (preferences.getStringValue(R.string.p_alarm_lock_pending)) {
            "android" -> {
                preferences.setString(R.string.p_alarm_lock_pending, null)
                if (health.facts().canFullScreen) {
                    if (isXiaomi()) lockScreenAsk = LockScreenAsk.XIAOMI else enableLockScreen()
                } else {
                    lockScreenDenied = true
                }
            }
            "xiaomi" -> {
                preferences.setString(R.string.p_alarm_lock_pending, null)
                enableLockScreen()
            }
        }
    }

    private fun settleExactAlarm() {
        if (!preferences.getBoolean(R.string.p_exact_alarm_pending, false)) return
        preferences.setBoolean(R.string.p_exact_alarm_pending, false)
        if (context.canScheduleExactAlarms()) {
            viewModelScope.launch { applyLevelEnabled(AlertLevel.SUPER, true) }
        }
    }

    private fun isXiaomi() = oemFamily(Build.MANUFACTURER) == OemFamily.XIAOMI

    private fun enableLockScreen() {
        preferences.setBoolean(R.string.p_alarm_on_lock_screen, true)
        alarmOnLockScreen = true
    }

    private fun refreshAndroidState() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        showBatteryOptimization =
            !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        refreshCompletionSoundName()
        refreshLevelSoundNames()
        superBypassSilent = preferences.getBoolean(R.string.p_super_bypass_silent, false)
        alarmOnLockScreen = preferences.getBoolean(R.string.p_alarm_on_lock_screen, false)
        val facts = health.facts(
            alarmEnabled = runBlocking { preferences.levelAlarmEnabled() },
            wantsLockScreen = alarmOnLockScreen,
        )
        setHealth(health.openable(visibleChecks(facts)), modeLine(facts))
    }

    override fun openHealthCheck(id: ReminderCheckId) = health.open(id)

    override fun scheduleReminderTest() {
        health.scheduleTest()
        markTestPending()
    }

    override fun updateSuperBypass(enabled: Boolean) {
        super.updateSuperBypass(enabled)
        preferences.setBoolean(R.string.p_super_bypass_silent, enabled)
    }

    override fun updateAlarmOnLockScreen(enabled: Boolean) {
        if (!enabled) {
            preferences.setString(R.string.p_alarm_lock_pending, null)
            lockScreenAsk = null
            lockScreenDenied = false
            preferences.setBoolean(R.string.p_alarm_on_lock_screen, false)
            super.updateAlarmOnLockScreen(false)
            refreshAndroidState()
            return
        }
        lockScreenAsk = when {
            !health.facts().canFullScreen -> LockScreenAsk.ANDROID
            isXiaomi() -> LockScreenAsk.XIAOMI
            else -> {
                enableLockScreen()
                refreshAndroidState()
                null
            }
        }
    }

    override fun confirmAlarmLockScreen() {
        when (lockScreenAsk) {
            LockScreenAsk.ANDROID -> {
                preferences.setString(R.string.p_alarm_lock_pending, "android")
                health.open(ReminderCheckId.FULL_SCREEN)
            }
            LockScreenAsk.XIAOMI -> {
                preferences.setString(R.string.p_alarm_lock_pending, "xiaomi")
                health.open(ReminderCheckId.LOCK_SCREEN)
            }
            null -> Unit
        }
        lockScreenAsk = null
    }

    override fun dismissAlarmLockScreen() {
        lockScreenAsk = null
    }

    override fun confirmExactAlarms() {
        askExactAlarms = false
        preferences.setBoolean(R.string.p_exact_alarm_pending, true)
        health.open(ReminderCheckId.EXACT_ALARMS)
    }

    fun onLevelChange(level: AlertLevel, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                if (level == AlertLevel.SUPER && !context.canScheduleExactAlarms()) {
                    askExactAlarms = true
                } else {
                    applyLevelEnabled(level, true)
                }
            } else {
                val priority = priorityOf(level)
                val tasks = taskDao.countTasksWithPriority(priority)
                val memos = taskDao.countMemosWithPriority(priority)
                if (tasks == 0 && memos == 0) {
                    applyLevelEnabled(level, false)
                    if (preferences.defaultPriority() == priority) {
                        preferences.setDefaultPriority(Task.Priority.LOW)
                    }
                } else {
                    pendingLevelDisable = PendingLevelDisable(level, tasks, memos)
                }
            }
        }
    }

    fun confirmLevelDisable() {
        val pending = pendingLevelDisable ?: return
        pendingLevelDisable = null
        disableLevelAndReset(pending.level)
    }

    fun dismissLevelDisable() {
        pendingLevelDisable = null
    }

    private suspend fun applyLevelEnabled(level: AlertLevel, enabled: Boolean) {
        when (level) {
            AlertLevel.SILENT -> {
                preferences.setLevelSilentEnabled(enabled)
                levelSilentEnabled = enabled
            }
            AlertLevel.PERSISTENT -> {
                preferences.setLevelPersistentEnabled(enabled)
                levelPersistentEnabled = enabled
            }
            AlertLevel.SUPER -> {
                preferences.setLevelAlarmEnabled(enabled)
                levelAlarmEnabled = enabled
                refreshAndroidState()
            }
            AlertLevel.ONCE -> Unit
        }
    }

    private fun disableLevelAndReset(level: AlertLevel) {
        val priority = priorityOf(level)
        viewModelScope.launch {
            withContext(NonCancellable) {
                taskDao.fetch(taskDao.idsWithPriority(priority)).forEach { task ->
                    taskSaver.save(task.copy(priority = Task.Priority.LOW), task)
                }
                seriesDao.getAll().forEach { series ->
                    val template = runCatching { seriesTemplates.decode(series.template) }.getOrNull()
                        ?: return@forEach
                    if (template.priority != priority) return@forEach
                    seriesDao.update(
                        series.copy(
                            template = seriesTemplates.encode(
                                template.copy(priority = Task.Priority.LOW),
                            ),
                        ),
                    )
                }
                if (preferences.defaultPriority() == priority) {
                    preferences.setDefaultPriority(Task.Priority.LOW)
                }
                when (level) {
                    AlertLevel.SILENT -> preferences.setLevelSilentEnabled(false)
                    AlertLevel.PERSISTENT -> preferences.setLevelPersistentEnabled(false)
                    AlertLevel.SUPER -> preferences.setLevelAlarmEnabled(false)
                    AlertLevel.ONCE -> Unit
                }
            }
            withContext(Dispatchers.Main) {
                when (level) {
                    AlertLevel.SILENT -> levelSilentEnabled = false
                    AlertLevel.PERSISTENT -> levelPersistentEnabled = false
                    AlertLevel.SUPER -> {
                        levelAlarmEnabled = false
                        refreshAndroidState()
                    }
                    AlertLevel.ONCE -> Unit
                }
            }
        }
    }

    fun levelRingtoneValue(level: AlertLevel): String? = preferences.ringtoneForLevel(level)

    fun handleLevelSoundResult(level: AlertLevel, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            val ringtone: Uri? = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            setLevelRingtone(level, ringtone?.toString())
        }
    }

    fun setLevelRingtone(level: AlertLevel, uri: String?) {
        levelRingtoneKey(level)?.let { preferences.setString(it, uri ?: "") }
        refreshLevelSoundNames()
    }

    private fun levelRingtoneKey(level: AlertLevel): Int? = when (level) {
        AlertLevel.ONCE -> R.string.p_ringtone_once
        AlertLevel.PERSISTENT -> R.string.p_ringtone_persistent
        AlertLevel.SUPER -> R.string.p_ringtone_alarm
        AlertLevel.SILENT -> null
    }

    private fun refreshLevelSoundNames() {
        onceSoundName = levelSoundName(AlertLevel.ONCE)
        persistentSoundName = levelSoundName(AlertLevel.PERSISTENT)
        alarmSoundName = levelSoundName(AlertLevel.SUPER)
    }

    private fun levelSoundName(level: AlertLevel): String {
        val value = preferences.ringtoneForLevel(level)
        return if (value.isNullOrEmpty()) {
            runBlocking { getString(Res.string.reminder_sound_system) }
        } else {
            try {
                RingtoneManager.getRingtone(context, value.toUri())?.getTitle(context) ?: value
            } catch (e: SecurityException) {
                Timber.e(e)
                value
            }
        }
    }

    override fun updateVoice(enabled: Boolean) {
        val needsTtsCheck: Boolean
        try {
            if (enabled) {
                needsTtsCheck = !voiceOutputAssistant.isTTSInitialized
            } else {
                needsTtsCheck = false
                if (voiceOutputAssistant.isTTSInitialized) {
                    voiceOutputAssistant.shutdown()
                }
            }
        } catch (e: VerifyError) {
            Timber.e(e)
            super.updateVoice(false)
            return
        }
        super.updateVoice(enabled)
        if (needsTtsCheck) {
            ttsChecks.trySend(Unit)
        }
    }

    fun handleTtsCheckResult(resultCode: Int) {
        if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
            voiceOutputAssistant.initTTS()
        }
    }

    fun getCompletionRingtoneValue(): String? =
        preferences.getStringValue(R.string.p_completion_ringtone)

    fun handleCompletionSoundResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            val ringtone: Uri? = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            preferences.setString(R.string.p_completion_ringtone, ringtone?.toString() ?: "")
            refreshCompletionSoundName()
        }
    }

    private fun refreshCompletionSoundName() {
        val value = preferences.getStringValue(R.string.p_completion_ringtone)
        val defaultUri = context.getResourceUri(R.raw.long_rising_tone)
        completionSoundName = when {
            value == "" -> context.getString(R.string.none)
            value != null -> {
                val uri = value.toUri()
                if (uri == defaultUri) {
                    context.getString(R.string.settings_default)
                } else {
                    try {
                        RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: value
                    } catch (e: SecurityException) {
                        Timber.e(e)
                        value
                    }
                }
            }
            else -> context.getString(R.string.settings_default)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceOutputAssistant.shutdown()
    }
}
