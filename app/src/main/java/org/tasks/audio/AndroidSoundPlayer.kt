package org.tasks.audio

import android.content.Context
import org.tasks.notifications.NotificationManager
import org.tasks.preferences.Preferences

class AndroidSoundPlayer(
    context: Context,
    preferences: Preferences,
    notificationManager: NotificationManager,
) : SoundPlayer {
    override suspend fun playCompletionSound() = Unit
}
