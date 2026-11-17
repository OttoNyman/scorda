package org.tasks.compose.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.tasks.BuildConfig

@Composable
fun AndroidMainSettingsScreen(
    proCardState: ProCardState?,
    environmentLabel: String? = null,
    showBackupWarning: Boolean,
    accountError: Boolean = false,
    isDebug: Boolean = BuildConfig.DEBUG,
    onSettingsClick: (SettingsDestination) -> Unit,
    onProCardClick: () -> Unit,
    showDesktopLinking: Boolean = false,
    showNavigationDrawer: Boolean = true,
    onLinkDesktopClick: () -> Unit = {},
    onTrash: () -> Unit = {},
) {
    MainSettingsScreen(
        proCardState = proCardState,
        environmentLabel = environmentLabel,
        showBackupWarning = showBackupWarning,
        accountError = accountError,
        isDebug = isDebug,
        showNavigationDrawer = showNavigationDrawer,
        onSettingsClick = onSettingsClick,
        onProCardClick = onProCardClick,
        showDesktopLinking = showDesktopLinking,
        onLinkDesktopClick = onLinkDesktopClick,
        onTrash = onTrash,
        bottomContent = {
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        },
    )
}
