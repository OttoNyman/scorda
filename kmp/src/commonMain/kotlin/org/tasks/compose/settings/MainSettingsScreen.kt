package org.tasks.compose.settings

import org.tasks.themes.TasksIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.tasks.data.entity.CaldavAccount
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.EPr_edit_screen_options
import tasks.kmp.generated.resources.account_and_backup
import tasks.kmp.generated.resources.caldav
import tasks.kmp.generated.resources.etesync
import tasks.kmp.generated.resources.gtasks_GPr_header
import tasks.kmp.generated.resources.microsoft
import tasks.kmp.generated.resources.constructor_title
import tasks.kmp.generated.resources.date_and_time
import tasks.kmp.generated.resources.debug
import tasks.kmp.generated.resources.about
import tasks.kmp.generated.resources.local_lists
import tasks.kmp.generated.resources.mcp_server
import tasks.kmp.generated.resources.tasks_org
import tasks.kmp.generated.resources.navigation_drawer
import tasks.kmp.generated.resources.notifications
import tasks.kmp.generated.resources.preferences_advanced
import tasks.kmp.generated.resources.preferences_look_and_feel
import tasks.kmp.generated.resources.screen_settings
import tasks.kmp.generated.resources.settings
import tasks.kmp.generated.resources.task_defaults
import tasks.kmp.generated.resources.trash
import tasks.kmp.generated.resources.task_list_options
import tasks.kmp.generated.resources.link_desktop
import tasks.kmp.generated.resources.link_desktop_description
import tasks.kmp.generated.resources.widget_settings
import tasks.kmp.generated.resources.works_with_tasks
import tasks.kmp.generated.resources.works_with_tasks_description

sealed interface SettingsPane {
    val titleRes: StringResource
}

sealed class SettingsDestination(override val titleRes: StringResource) : SettingsPane {
    data object LookAndFeel : SettingsDestination(Res.string.preferences_look_and_feel)
    data object Notifications : SettingsDestination(Res.string.notifications)
    data object TaskDefaults : SettingsDestination(Res.string.task_defaults)
    data object Constructor : SettingsDestination(Res.string.constructor_title)
    data object Screens : SettingsDestination(Res.string.screen_settings)
    data object TaskList : SettingsDestination(Res.string.task_list_options)
    data object TaskEdit : SettingsDestination(Res.string.EPr_edit_screen_options)
    data object DateAndTime : SettingsDestination(Res.string.date_and_time)
    data object NavigationDrawer : SettingsDestination(Res.string.navigation_drawer)
    data object Backups : SettingsDestination(Res.string.account_and_backup)
    data object Widgets : SettingsDestination(Res.string.widget_settings)
    data object McpServer : SettingsDestination(Res.string.mcp_server)
    data object Advanced : SettingsDestination(Res.string.preferences_advanced)
    data object WorksWith : SettingsDestination(Res.string.works_with_tasks)
    data object HelpAndFeedback : SettingsDestination(Res.string.about)
    data object Debug : SettingsDestination(Res.string.debug)
}

data class LocalAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.local_lists
}

data class TasksAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.tasks_org
}

data class CaldavAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.caldav
}

data class EtebaseAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.etesync
}

data class GoogleTasksAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.gtasks_GPr_header
}

data class MicrosoftAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.microsoft
}

data class OpenTaskAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.settings
}

@Composable
fun MainSettingsScreen(
    proCardState: ProCardState?,
    environmentLabel: String? = null,
    showBackupWarning: Boolean,
    accountError: Boolean = false,
    showNotifications: Boolean = true,
    showMcpServer: Boolean = false,
    showNavigationDrawer: Boolean = true,
    isDebug: Boolean = false,
    onSettingsClick: (SettingsDestination) -> Unit,
    onProCardClick: () -> Unit,
    showDesktopLinking: Boolean = false,
    showWorksWith: Boolean = false,
    onLinkDesktopClick: () -> Unit = {},
    /** Opens the trash. Null hides the row. */
    onTrash: (() -> Unit)? = null,
    bottomContent: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))

        // Pro/tasks.org card
        if (proCardState != null) {
            AccountSettingsCard(
                state = proCardState,
                onClick = onProCardClick,
                modifier = Modifier.padding(horizontal = SettingsContentPadding),
                environmentLabel = environmentLabel,
            )
            Spacer(modifier = Modifier.height(SettingsContentPadding))
        }

        SettingsItemCard(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
            PreferenceRow(
                title = stringResource(Res.string.account_and_backup),
                icon = TasksIcons.CLOUD_UPLOAD,
                showWarning = showBackupWarning,
                showError = accountError,
                onClick = { onSettingsClick(SettingsDestination.Backups) },
            )
        }
        Spacer(modifier = Modifier.height(SettingsContentPadding))

        if (showDesktopLinking || showWorksWith) {
            Column(
                modifier = Modifier.padding(horizontal = SettingsContentPadding),
                verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
            ) {
                if (showDesktopLinking) {
                    SettingsItemCard(
                        position = if (showWorksWith) CardPosition.First else CardPosition.Only,
                    ) {
                        PreferenceRow(
                            title = stringResource(Res.string.link_desktop),
                            summary = stringResource(Res.string.link_desktop_description),
                            icon = TasksIcons.LAPTOP,
                            onClick = onLinkDesktopClick,
                        )
                    }
                }
                if (showWorksWith) {
                    SettingsItemCard(
                        position = if (showDesktopLinking) CardPosition.Last else CardPosition.Only,
                    ) {
                        PreferenceRow(
                            title = stringResource(Res.string.works_with_tasks),
                            summary = stringResource(Res.string.works_with_tasks_description),
                            icon = TasksIcons.EXTENSION,
                            onClick = { onSettingsClick(SettingsDestination.WorksWith) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(SettingsContentPadding))
        }

        SettingsCategories(
            showNotifications = showNotifications,
            showMcpServer = showMcpServer,
            showNavigationDrawer = showNavigationDrawer,
            isDebug = isDebug,
            onSettingsClick = onSettingsClick,
            onTrash = onTrash,
        )

        bottomContent()
    }
}

@Composable
fun SettingsCategories(
    showNotifications: Boolean,
    showMcpServer: Boolean = false,
    showNavigationDrawer: Boolean = true,
    isDebug: Boolean,
    onSettingsClick: (SettingsDestination) -> Unit,
    onTrash: (() -> Unit)? = null,
) {
    // Appearance island
    Column(
        modifier = Modifier.padding(horizontal = SettingsContentPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
    ) {
        SettingsItemCard(
            position = if (showNotifications) CardPosition.First else CardPosition.Only,
        ) {
            PreferenceRow(
                title = stringResource(Res.string.preferences_look_and_feel),
                icon = TasksIcons.PALETTE,
                onClick = { onSettingsClick(SettingsDestination.LookAndFeel) }
            )
        }
        if (showNotifications) {
            SettingsItemCard(position = CardPosition.Last) {
                PreferenceRow(
                    title = stringResource(Res.string.notifications),
                    icon = TasksIcons.NOTIFICATIONS,
                    onClick = { onSettingsClick(SettingsDestination.Notifications) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(SettingsContentPadding))

    // Tasks island
    Column(
        modifier = Modifier.padding(horizontal = SettingsContentPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
    ) {
        SettingsItemCard(position = CardPosition.First) {
            PreferenceRow(
                title = stringResource(Res.string.constructor_title),
                icon = TasksIcons.BUILD,
                onClick = { onSettingsClick(SettingsDestination.Constructor) }
            )
        }
        SettingsItemCard(position = CardPosition.Middle) {
            PreferenceRow(
                title = stringResource(Res.string.task_defaults),
                icon = TasksIcons.ADD,
                onClick = { onSettingsClick(SettingsDestination.TaskDefaults) }
            )
        }
        SettingsItemCard(position = CardPosition.Last) {
            PreferenceRow(
                title = stringResource(Res.string.screen_settings),
                icon = TasksIcons.APPS,
                onClick = { onSettingsClick(SettingsDestination.Screens) }
            )
        }
    }

    Spacer(modifier = Modifier.height(SettingsContentPadding))

    // Configuration island
    Column(
        modifier = Modifier.padding(horizontal = SettingsContentPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
    ) {
        SettingsItemCard(position = CardPosition.First) {
            PreferenceRow(
                title = stringResource(Res.string.date_and_time),
                icon = TasksIcons.SCHEDULE,
                onClick = { onSettingsClick(SettingsDestination.DateAndTime) }
            )
        }
        if (showNavigationDrawer) {
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(Res.string.navigation_drawer),
                    icon = TasksIcons.MENU,
                    onClick = { onSettingsClick(SettingsDestination.NavigationDrawer) }
                )
            }
        }
        if (showMcpServer) {
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(Res.string.mcp_server),
                    icon = TasksIcons.TERMINAL,
                    onClick = { onSettingsClick(SettingsDestination.McpServer) }
                )
            }
        }
        SettingsItemCard(position = CardPosition.Last) {
            PreferenceRow(
                title = stringResource(Res.string.preferences_advanced),
                icon = TasksIcons.BUILD,
                onClick = { onSettingsClick(SettingsDestination.Advanced) }
            )
        }
    }

    Spacer(modifier = Modifier.height(SettingsContentPadding))

    if (onTrash != null) {
        SettingsItemCard(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
            PreferenceRow(
                title = stringResource(Res.string.trash),
                icon = TasksIcons.DELETE,
                onClick = onTrash,
            )
        }
        Spacer(modifier = Modifier.height(SettingsContentPadding))
    }

    // About card group
    Column(
        modifier = Modifier.padding(horizontal = SettingsContentPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
    ) {
        SettingsItemCard(
            position = if (isDebug) CardPosition.First else CardPosition.Only,
        ) {
            PreferenceRow(
                title = stringResource(Res.string.about),
                icon = TasksIcons.INFO,
                onClick = { onSettingsClick(SettingsDestination.HelpAndFeedback) }
            )
        }
        if (isDebug) {
            SettingsItemCard(position = CardPosition.Last) {
                PreferenceRow(
                    title = stringResource(Res.string.debug),
                    icon = TasksIcons.BUG_REPORT,
                    onClick = { onSettingsClick(SettingsDestination.Debug) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(SettingsContentPadding))
}

