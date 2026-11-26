package org.tasks.quickalarm

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.compose.QuickAlarmScreen
import org.tasks.notifications.AndroidReminderHealth
import org.tasks.preferences.Preferences
import org.tasks.themes.TasksTheme
import org.tasks.themes.Theme
import javax.inject.Inject

@AndroidEntryPoint
class QuickAlarmActivity : AppCompatActivity() {
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var theme: Theme

    private val viewModel: QuickAlarmViewModel by viewModels()
    private val reminderHealth by lazy { AndroidReminderHealth(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TasksTheme(
                theme = theme.themeBase.index,
                primary = theme.themeColor.primaryColor,
            ) {
                QuickAlarmScreen(
                    preferences = preferences,
                    viewModel = viewModel,
                    reminderHealth = reminderHealth,
                    onFinish = { finish() },
                )
            }
        }
    }
}
