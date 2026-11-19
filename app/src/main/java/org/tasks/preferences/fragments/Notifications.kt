package org.tasks.preferences.fragments

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import dagger.hilt.android.AndroidEntryPoint
import org.jetbrains.compose.resources.stringResource
import org.tasks.R
import org.tasks.compose.settings.NotificationsContent
import androidx.compose.ui.platform.LocalContext
import org.tasks.extensions.Context.is24HourFormat
import org.tasks.extensions.Context.openChannelNotificationSettings
import org.tasks.extensions.Context.openUri
import org.tasks.notifications.AlertLevel
import org.tasks.preferences.BasePreferences
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import timber.log.Timber
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.more_notification_settings_summary
import javax.inject.Inject

@AndroidEntryPoint
class Notifications : Fragment() {

    @Inject lateinit var theme: Theme

    private val viewModel: NotificationsHiltViewModel by viewModels()

    private var pickingLevel: AlertLevel = AlertLevel.ONCE

    private val levelSoundLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        viewModel.handleLevelSoundResult(pickingLevel, result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickingLevel = savedInstanceState
            ?.getString(STATE_PICKING_LEVEL)
            ?.let { runCatching { AlertLevel.valueOf(it) }.getOrNull() }
            ?: AlertLevel.ONCE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PICKING_LEVEL, pickingLevel.name)
    }

    private val ttsCheckLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        viewModel.handleTtsCheckResult(result.resultCode)
        if (result.resultCode != TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
            try {
                startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = content {
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            LaunchedEffect(Unit) {
                viewModel.ttsCheckRequests.collect {
                    try {
                        ttsCheckLauncher.launch(Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA))
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }
            }

            val is24HourFormat = LocalContext.current.is24HourFormat

            NotificationsContent(
                viewModel = viewModel,
                is24HourFormat = is24HourFormat,
                openUri = { context?.openUri(it) },
                onBatteryOptimization = {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                },
                onLevelSound = {
                    pickingLevel = it
                    launchRingtonePicker(level = it)
                },
                onLevelToggle = { level, enabled -> viewModel.onLevelChange(level, enabled) },
                onConfirmLevelDisable = { viewModel.confirmLevelDisable() },
                onDismissLevelDisable = { viewModel.dismissLevelDisable() },
                onMoreSettings = { requireContext().openChannelNotificationSettings() },
                moreSettingsSummary = stringResource(Res.string.more_notification_settings_summary),
                bottomInsets = {
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                },
            )
        }
    }

    private fun launchRingtonePicker(level: AlertLevel = AlertLevel.ONCE) {
        val alarm = level == AlertLevel.SUPER
        val defaultUri = if (alarm) {
            Settings.System.DEFAULT_ALARM_ALERT_URI
        } else {
            Settings.System.DEFAULT_NOTIFICATION_URI
        }
        val existingValue = viewModel.levelRingtoneValue(level)
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION,
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri)
            when (existingValue) {
                null -> putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, defaultUri)
                "" -> putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, null as Uri?)
                else -> putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    Uri.parse(existingValue)
                )
            }
        }
        levelSoundLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshState()
        val surfaceColor = theme.themeBase.getSettingsSurfaceColor(requireActivity())
        (activity as? BasePreferences)?.toolbar?.let { toolbar ->
            toolbar.setBackgroundColor(surfaceColor)
            (toolbar.parent as? View)?.setBackgroundColor(surfaceColor)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val defaultColor = ContextCompat.getColor(requireContext(), R.color.content_background)
        (activity as? BasePreferences)?.toolbar?.let { toolbar ->
            toolbar.setBackgroundColor(defaultColor)
            (toolbar.parent as? View)?.setBackgroundColor(defaultColor)
        }
    }

    companion object {
        private const val STATE_PICKING_LEVEL = "picking_level"
    }
}
