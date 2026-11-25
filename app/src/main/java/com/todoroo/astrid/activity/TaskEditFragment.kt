package com.todoroo.astrid.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.todoroo.astrid.activity.MainActivity.Companion.finishAffinity
import com.todoroo.astrid.activity.MainActivity.Companion.removeTask
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.tasks.compose.edit.TaskEditScreen
import org.tasks.repeats.RepeatRuleToString
import org.tasks.repeats.SeriesEditScope
import org.tasks.data.Location
import org.tasks.data.dao.UserActivityDao
import org.tasks.data.entity.Geofence
import org.tasks.dialogs.GeofenceDialog
import org.tasks.dialogs.Linkify
import org.tasks.location.LocationPermissionDialog.Companion.newLocationPermissionDialog
import org.tasks.preferences.PermissionChecker
import org.tasks.extensions.hideKeyboard
import org.tasks.markdown.MarkdownProvider
import org.tasks.notifications.CancelReason
import org.tasks.notifications.NotificationManager
import org.tasks.play.PlayServices
import org.tasks.preferences.Preferences
import org.tasks.themes.TasksTheme
import org.tasks.themes.Theme
import org.tasks.ui.ChipProvider
import org.tasks.ui.TaskEditViewModel
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class TaskEditFragment : Fragment() {
    @Inject lateinit var userActivityDao: UserActivityDao
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var linkify: Linkify
    @Inject lateinit var locale: Locale
    @Inject lateinit var chipProvider: ChipProvider
    @Inject lateinit var playServices: PlayServices
    @Inject lateinit var theme: Theme
    @Inject lateinit var repeatRuleToString: RepeatRuleToString
    @Inject lateinit var permissionChecker: PermissionChecker

    private val editViewModel: TaskEditViewModel by viewModels()
    private val mainViewModel: MainActivityViewModel by activityViewModels()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ) = content {
        TasksTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            val viewState = editViewModel.viewState.collectAsStateWithLifecycle().value
            val draftListFilter = mainViewModel.state.collectAsStateWithLifecycle().value.filter
            LaunchedEffect(viewState.isNew) {
                if (!viewState.isNew) {
                    notificationManager.cancel(viewState.task.id, CancelReason.EDIT)
                }
            }
            val context = LocalContext.current
            val keyboard = LocalSoftwareKeyboardController.current

            TaskEditScreen(
                editViewModel = editViewModel,
                viewState = viewState,
                comments = userActivityDao
                    .watchComments(viewState.task.uuid)
                    .collectAsStateWithLifecycle(emptyList())
                    .value,
                save = { scope ->
                    keyboard?.hide()
                    lifecycleScope.launch { save(scope) }
                },
                discard = {
                    keyboard?.hide()
                    discard()
                },
                delete = {
                    keyboard?.hide()
                    lifecycleScope.launch {
                        editViewModel.delete()
                        clearTask()
                    }
                },
                dismissBeastMode = { editViewModel.hideBeastModeHint(click = false) },
                deleteComment = {
                    lifecycleScope.launch {
                        userActivityDao.delete(it)
                    }
                },
                markdownProvider = remember { MarkdownProvider(context, preferences) },
                linkify = if (viewState.linkify) linkify else null,
                preferences = preferences,
                onConfigureGeofence = { configureGeofence() },
                repeatRuleToString = { repeatRuleToString.toStringBlocking(it) },
                colorProvider = { chipProvider.getColor(it) },
                locale = remember { locale },
                draftListFilter = draftListFilter,
            )
        }
    }

    private fun clearTask() {
        Timber.d("clearTask()")
        val activity = activity ?: return
        mainViewModel.setTask(null)
        activity.hideKeyboard()
        when {
            activity.intent.finishAffinity -> {
                Timber.d("finishAffinity")
                activity.finishAffinity()
            }
            activity.intent.removeTask -> {
                Timber.d("removeTask")
                activity.moveTaskToBack(true)
                activity.finish()
            }
        }
    }

    suspend fun save(scope: SeriesEditScope? = null, remove: Boolean = true) {
        editViewModel.save(scope)
        if (remove) {
            clearTask()
        }
        activity?.let { playServices.requestReview(it) }
    }

    private fun configureGeofence() {
        val location = editViewModel.viewState.value.location ?: return
        if (permissionChecker.canAccessBackgroundLocation()) {
            GeofenceDialog.newGeofenceDialog(location).apply {
                setTargetFragment(this@TaskEditFragment, REQUEST_GEOFENCE)
            }.show(parentFragmentManager, FRAG_TAG_GEOFENCE)
        } else {
            newLocationPermissionDialog(this, REQUEST_LOCATION_PERMISSIONS)
                .show(parentFragmentManager, FRAG_TAG_LOCATION_PERMISSION)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_LOCATION_PERMISSIONS -> if (resultCode == Activity.RESULT_OK) configureGeofence()
            REQUEST_GEOFENCE -> if (resultCode == Activity.RESULT_OK) {
                val geofence = data?.let {
                    IntentCompat.getParcelableExtra(it, GeofenceDialog.EXTRA_GEOFENCE, Geofence::class.java)
                } ?: return
                val place = editViewModel.viewState.value.location?.place ?: return
                editViewModel.setLocation(Location(geofence, place))
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun discard() = lifecycleScope.launch {
        editViewModel.discard()
        clearTask()
    }

    companion object {
        const val EXTRA_TASK = "extra_task"

        const val FRAG_TAG_CALENDAR_PICKER = "frag_tag_calendar_picker"
        private const val FRAG_TAG_GEOFENCE = "frag_tag_geofence"
        private const val FRAG_TAG_LOCATION_PERMISSION = "frag_tag_location_permission"
        private const val REQUEST_GEOFENCE = 12154
        private const val REQUEST_LOCATION_PERMISSIONS = 12155
    }
}
