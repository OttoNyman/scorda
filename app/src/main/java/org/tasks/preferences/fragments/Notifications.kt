package org.tasks.preferences.fragments

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource as kmpStringResource
import org.tasks.R
import org.tasks.compose.settings.NotificationsContent
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
import tasks.kmp.generated.resources.reminder_sound
import tasks.kmp.generated.resources.reminder_sound_system
import javax.inject.Inject

@AndroidEntryPoint
class Notifications : Fragment() {

    @Inject lateinit var theme: Theme

    private val viewModel: NotificationsHiltViewModel by viewModels()

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

            val context = LocalContext.current
            val is24HourFormat = context.is24HourFormat
            var toneLevel by remember { mutableStateOf<AlertLevel?>(null) }
            var tones by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
            var chosen by remember { mutableStateOf<String?>(null) }
            var tonesReady by remember { mutableStateOf(false) }

            toneLevel?.let { level ->
                LaunchedEffect(level) {
                    tonesReady = false
                    val saved = viewModel.levelRingtoneValue(level)?.takeIf { it.isNotEmpty() }
                    val loaded = withContext(Dispatchers.IO) {
                        notificationTones(context, level == AlertLevel.SUPER, saved)
                    }
                    chosen = saved?.takeIf { uri -> loaded.any { it.second == uri } }
                    tones = loaded
                    tonesReady = true
                }
                if (tonesReady) {
                    TonePickerDialog(
                        tones = tones,
                        selected = chosen,
                        alarm = level == AlertLevel.SUPER,
                        onSelect = { chosen = it },
                        onConfirm = {
                            viewModel.setLevelRingtone(level, chosen)
                            toneLevel = null
                        },
                        onDismiss = { toneLevel = null },
                    )
                }
            }

            NotificationsContent(
                viewModel = viewModel,
                is24HourFormat = is24HourFormat,
                openUri = { context?.openUri(it) },
                onBatteryOptimization = {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                },
                onLevelSound = {
                    tonesReady = false
                    toneLevel = it
                },
                onLevelToggle = { level, enabled -> viewModel.onLevelChange(level, enabled) },
                onConfirmLevelDisable = { viewModel.confirmLevelDisable() },
                onDismissLevelDisable = { viewModel.dismissLevelDisable() },
                onMoreSettings = { requireContext().openChannelNotificationSettings() },
                moreSettingsSummary = kmpStringResource(Res.string.more_notification_settings_summary),
                bottomInsets = {
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                },
            )
        }
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

}

private fun notificationTones(context: Context, alarm: Boolean, saved: String?): List<Pair<String, String>> {
    val manager = RingtoneManager(context).apply {
        setType(if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
    }
    val cursor = manager.cursor ?: return emptyList()
    val byTitle = LinkedHashMap<String, String>()
    try {
        if (!cursor.moveToFirst()) return emptyList()
        do {
            val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)?.takeIf { it.isNotBlank() }
                ?: continue
            val uri = manager.getRingtoneUri(cursor.position)?.toString() ?: continue
            val current = byTitle[title]
            if (current == null || uri == saved) byTitle[title] = uri
        } while (cursor.moveToNext())
    } catch (e: Exception) {
        Timber.e(e)
    } finally {
        cursor.close()
    }
    return byTitle.map { it.key to it.value }
}

@Composable
private fun TonePickerDialog(
    tones: List<Pair<String, String>>,
    selected: String?,
    alarm: Boolean,
    onSelect: (String?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<Ringtone?>(null) }
    DisposableEffect(Unit) {
        onDispose { preview?.stop() }
    }
    val defaultUri = if (alarm) {
        Settings.System.DEFAULT_ALARM_ALERT_URI
    } else {
        Settings.System.DEFAULT_NOTIFICATION_URI
    }
    fun play(uri: Uri) {
        preview?.stop()
        preview = RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kmpStringResource(Res.string.reminder_sound)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                ToneRow(
                    title = kmpStringResource(Res.string.reminder_sound_system),
                    selected = selected.isNullOrEmpty(),
                    onClick = {
                        onSelect(null)
                        play(defaultUri)
                    },
                )
                tones.forEach { (title, uri) ->
                    ToneRow(
                        title = title,
                        selected = uri == selected,
                        onClick = {
                            onSelect(uri)
                            play(Uri.parse(uri))
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ToneRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = title,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
