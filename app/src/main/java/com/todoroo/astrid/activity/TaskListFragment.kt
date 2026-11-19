/*
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Parcelable
import android.speech.RecognizerIntent
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LAYOUT_DIRECTION_LTR
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.ShareCompat
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.todoroo.andlib.utility.AndroidUtilities
import com.todoroo.astrid.adapter.MemosTaskAdapter
import com.todoroo.astrid.adapter.TaskAdapter
import com.todoroo.astrid.adapter.TaskAdapterProvider
import com.todoroo.astrid.api.AstridApiConstants.EXTRAS_OLD_DUE_DATE
import com.todoroo.astrid.api.AstridApiConstants.EXTRAS_TASK_ID
import com.todoroo.astrid.repeats.RepeatTaskHelper
import com.todoroo.astrid.service.TaskCreator
import com.todoroo.astrid.service.TaskDuplicator
import org.tasks.data.TaskMover
import com.todoroo.astrid.utility.Flags
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tasks.LocalBroadcastManager
import org.tasks.R
import org.tasks.ShortcutManager
import org.tasks.TasksApplication
import org.tasks.TasksUrls
import org.tasks.activities.FilterSettingsActivity
import org.tasks.analytics.Firebase
import org.tasks.billing.PurchaseActivity
import org.tasks.billing.PurchaseActivityViewModel
import org.tasks.caldav.BaseCaldavCalendarSettingsActivity
import org.tasks.caldav.LocalListSettingsActivity
import org.tasks.caldav.TasksAccountDataRepository
import org.tasks.compose.AlarmsDisabledBanner
import org.tasks.compose.AppUpdatedBanner
import org.tasks.compose.FilterSelectionActivity.Companion.launch
import org.tasks.compose.FilterSelectionActivity.Companion.registerForListPickerResult
import org.tasks.compose.NotificationsDisabledBanner
import org.tasks.compose.SubscriptionRequiredBanner
import org.tasks.compose.SyncWarningGoogleTasks
import org.tasks.compose.SyncWarningMicrosoft
import org.tasks.data.TaskContainer
import org.tasks.data.TaskSaver
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.SeriesDao
import org.tasks.data.dao.TagDataDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.db.Database
import org.tasks.data.db.SuspendDbUtils.chunkedMap
import org.tasks.data.entity.Task
import org.tasks.data.fetchTasks
import org.tasks.data.getAccountForNewList
import org.tasks.data.getOrCreateLocalAccount
import org.tasks.data.listSettingsClass
import org.tasks.data.open
import org.tasks.data.sql.QueryTemplate
import org.tasks.databinding.FragmentTaskListBinding
import org.tasks.dialogs.DateTimePicker.Companion.newDateTimePicker
import org.tasks.dialogs.DialogBuilder
import org.tasks.dialogs.PriorityPicker.Companion.newPriorityPicker
import org.tasks.extensions.Context.is24HourFormat
import org.tasks.extensions.Context.openAppNotificationSettings
import org.tasks.extensions.Context.openReminderSettings
import org.tasks.extensions.Context.openUri
import org.tasks.extensions.Fragment.safeStartActivityForResult
import org.tasks.extensions.hideKeyboard
import org.tasks.extensions.setOnQueryTextListener
import org.tasks.filters.AstridOrderingFilter
import org.tasks.filters.CaldavFilter
import org.tasks.filters.CompletedFilter
import org.tasks.filters.CustomFilter
import org.tasks.filters.Filter
import org.tasks.filters.FilterImpl
import org.tasks.constructor.fits
import org.tasks.filters.MemosFilter
import org.tasks.filters.MyTasksFilter
import org.tasks.filters.PlaceFilter
import org.tasks.filters.SearchFilter
import org.tasks.filters.TrashFilter
import org.tasks.filters.key
import org.tasks.kmp.org.tasks.time.DateStyle
import org.tasks.kmp.org.tasks.time.DateFormatter
import org.tasks.markdown.MarkdownProvider
import org.tasks.TasksBuildConfig
import org.tasks.preferences.Device
import org.tasks.preferences.MainPreferences
import org.tasks.preferences.Preferences
import org.tasks.preferences.ResourceResolver.getData
import org.tasks.repeats.SeriesScheduler
import org.tasks.resources.getString
import org.tasks.scheduling.NotificationSchedulerWork
import org.tasks.service.TaskCompleter
import org.tasks.service.TaskDeleter
import org.tasks.sync.SyncAdapters
import org.tasks.sync.SyncSource
import org.tasks.tags.TagPickerActivity
import org.tasks.tasklist.BannerAdapter
import org.tasks.tasklist.DragAndDropRecyclerAdapter
import org.tasks.tasklist.SectionedDataSource
import org.tasks.tasklist.TaskViewHolder
import org.tasks.tasklist.TasksResults
import org.tasks.tasklist.ViewHolderFactory
import org.tasks.themes.ColorProvider
import org.tasks.themes.TasksTheme
import org.tasks.themes.Theme
import org.tasks.themes.ThemeColor
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.ui.Banner
import org.tasks.ui.TaskListEvent
import org.tasks.ui.TaskListEventBus
import org.tasks.ui.TaskListViewModel
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.action_open
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@AndroidEntryPoint
class TaskListFragment : Fragment(), OnRefreshListener, Toolbar.OnMenuItemClickListener,
        MenuItem.OnActionExpandListener, SearchView.OnQueryTextListener, ActionMode.Callback,
        TaskViewHolder.ViewHolderCallbacks {
    private val repeatConfirmationReceiver = RepeatConfirmationReceiver()

    @Inject lateinit var syncAdapters: SyncAdapters
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dialogBuilder: DialogBuilder
    @Inject lateinit var taskCreator: TaskCreator
    @Inject lateinit var viewHolderFactory: ViewHolderFactory
    @Inject lateinit var localBroadcastManager: LocalBroadcastManager
    @Inject lateinit var device: Device
    @Inject lateinit var taskMover: TaskMover
    @Inject lateinit var taskAdapterProvider: TaskAdapterProvider
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var featureGate: org.tasks.constructor.FeatureGate
    @Inject lateinit var taskSaver: TaskSaver
    @Inject lateinit var taskDuplicator: TaskDuplicator
    @Inject lateinit var tagDataDao: TagDataDao
    @Inject lateinit var caldavDao: CaldavDao
    @Inject lateinit var defaultThemeColor: ThemeColor
    @Inject lateinit var colorProvider: ColorProvider
    @Inject lateinit var shortcutManager: ShortcutManager
    @Inject lateinit var taskCompleter: TaskCompleter
    @Inject lateinit var seriesScheduler: SeriesScheduler
    @Inject lateinit var seriesDao: SeriesDao
    @Inject lateinit var firebase: Firebase
    @Inject lateinit var repeatTaskHelper: RepeatTaskHelper
    @Inject lateinit var taskListEventBus: TaskListEventBus
    @Inject lateinit var database: Database
    @Inject lateinit var markdown: MarkdownProvider
    @Inject lateinit var theme: Theme
    @Inject lateinit var tasksAccountDataRepository: TasksAccountDataRepository

    private val listViewModel: TaskListViewModel by viewModels()
    private val mainViewModel: MainActivityViewModel by activityViewModels()
    private lateinit var taskAdapter: TaskAdapter
    private var recyclerAdapter: DragAndDropRecyclerAdapter? = null
    private var dirtyTaskIds: Set<Long> = emptySet()
    private var reportedFullyDrawn = false
    private val bannerAdapter = BannerAdapter()
    private lateinit var filter: Filter
    private lateinit var search: MenuItem
    private var mode: ActionMode? = null
    lateinit var themeColor: ThemeColor
    private var onClickMenu: () -> Unit = {}
    private lateinit var binding: FragmentTaskListBinding
    private var windowInsets: PaddingValues? = null
    private var recyclerViewState: Parcelable? = null
    private var hasWritableList = true
    private val listPickerLauncher = registerForListPickerResult {
        val selected = taskAdapter.getSelected()
        lifecycleScope.launch {
            taskMover.move(selected, it)
        }
        finishActionMode()
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        (activity as? MainActivity)?.restartActivity()
    }

    private val listSettingsRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            when (data.action) {
                ACTION_DELETED -> lifecycleScope.launch {
                    mainViewModel.setFilter(MyTasksFilter.create())
                }
                ACTION_RELOAD ->
                    IntentCompat.getParcelableExtra(data, MainActivity.OPEN_FILTER, Filter::class.java)?.let {
                        mainViewModel.setFilter(it)
                    }
            }
        }

    private suspend fun process(event: TaskListEvent) = when (event) {
        is TaskListEvent.TaskCreated ->
            onTaskCreated(event.uuid)
    }

    override fun onRefresh() {
        syncAdapters.sync(SyncSource.USER_INITIATED)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState != null) {
            val longArray = savedInstanceState.getLongArray(EXTRA_SELECTED_TASK_IDS)
            if (longArray?.isNotEmpty() == true) {
                taskAdapter.setSelected(longArray.toList())
                startActionMode()
            }
            recyclerViewState = savedInstanceState.getParcelable(EXTRA_RECYCLER_STATE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!this::taskAdapter.isInitialized) {
            return
        }
        val selectedTaskIds: List<Long> = taskAdapter.getSelected()
        outState.putLongArray(EXTRA_SELECTED_TASK_IDS, selectedTaskIds.toLongArray())
        val state = recyclerViewState
            ?: binding.bodyStandard.recyclerView.layoutManager?.onSaveInstanceState()
        outState.putParcelable(EXTRA_RECYCLER_STATE, state)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taskListEventBus
            .onEach { event ->
                try {
                    process(event)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Timber.e(e, "Failed to handle a task list event")
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        caldavDao.watchHasWritableList()
            .onEach { hasWritableList = it }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = mainViewModel.state.value.filter
                // search and the trash are side trips: back returns to the list they were opened from
                if ((current as? SearchFilter)?.query?.isNotBlank() == true || current is TrashFilter) {
                    lifecycleScope.launch {
                        mainViewModel.openLastViewedFilter()
                    }
                    if (search.isActionViewExpanded) {
                        search.collapseActionView()
                    }
                    Timber.d("Filtro resettato")
                } else {
                    isEnabled = false // Disabilita il callback per consentire il comportamento predefinito
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    fun setNavigationClickListener(onClick: () -> Unit) {
        onClickMenu = onClick
    }

    @SuppressLint("PrivateResource")
    fun applyInsets(windowInsets: PaddingValues) {
        if (this::binding.isInitialized) {
            applyInsetsInternal(windowInsets)
        } else {
            this.windowInsets = windowInsets
        }
    }

    private fun applyInsetsInternal(windowInsets: PaddingValues) {
        val density = resources.displayMetrics.density
        val viewLayoutDir = view?.layoutDirection ?: resources.configuration.layoutDirection
        val composeLayoutDirection =
            if (viewLayoutDir == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
        val topInset = (windowInsets.calculateTopPadding().value * density).toInt()
        val bottomInset = (windowInsets.calculateBottomPadding().value * density).toInt()
        val (startInset, endInset) =
            (windowInsets.calculateStartPadding(composeLayoutDirection).value * density).toInt().let {
            if (viewLayoutDir == LAYOUT_DIRECTION_LTR) it to 0 else 0 to it
        }
        with(binding.toolbar) {
            val actionBarHeight = TypedValue.complexToDimensionPixelSize(
                getData(requireContext(), android.R.attr.actionBarSize),
                resources.displayMetrics
            )
            val params = layoutParams
            params.height = actionBarHeight + topInset
            layoutParams = params
            updatePadding(top = topInset)
        }
        binding.taskListCoordinator.updatePadding(
            left = startInset,
            right = endInset,
        )
        binding.bottomAppBar.updatePadding(bottom = bottomInset)
        val scrimLayoutParams = binding.systemBarScrim.layoutParams
        scrimLayoutParams.height = bottomInset
        binding.systemBarScrim.layoutParams = scrimLayoutParams
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentTaskListBinding.inflate(inflater, container, false)
        windowInsets?.let { applyInsetsInternal(it) }
        filter = getFilter()
        val swipeRefreshLayout: SwipeRefreshLayout
        val emptyRefreshLayout: SwipeRefreshLayout
        val recyclerView: RecyclerView
        with (binding) {
            swipeRefreshLayout = bodyStandard.swipeLayout
            emptyRefreshLayout = bodyEmpty.swipeLayoutEmpty
            recyclerView = bodyStandard.recyclerView
            fab.setOnClickListener { createNewTask() }
            fab.isVisible = filter.isWritable
        }
        themeColor = if (filter.tint != 0) colorProvider.getThemeColor(filter.tint, true) else defaultThemeColor
        (filter as? AstridOrderingFilter)?.filterOverride = null

        // set up list adapters
        taskAdapter = taskAdapterProvider.createTaskAdapter(filter)
        (taskAdapter as? MemosTaskAdapter)?.confirmPriorityChange = { apply ->
            dialogBuilder
                .newDialog(R.string.memo_change_priority)
                .setPositiveButton(R.string.ok) { _, _ -> lifecycleScope.launch { apply() } }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        listViewModel.setFilter(filter)
        (recyclerView.itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
        // ListDetailPaneScaffold in adaptive-layout 1.2 briefly measures 0x0 without AnimatedPane,
        // which causes recycler view to lose scroll position. AnimatedPane was janky, and this is
        // getting replaced eventually, so here is a hack
        recyclerView.layoutManager = object : LinearLayoutManager(context) {
            override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
                if (height > 0) {
                    super.onLayoutChildren(recycler, state)
                }
            }
        }

        val baseFooterHeight = resources.getDimensionPixelSize(R.dimen.task_list_footer_height)
        val additionalFabSpace = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            56f,
            resources.displayMetrics
        ).toInt()
        recyclerView.updatePadding(bottom = baseFooterHeight + additionalFabSpace)
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                listViewModel.updateBannerState()
                launch {
                    listViewModel.banner.collect { banner ->
                        bannerAdapter.showBanner = banner != null
                    }
                }
                if (TasksBuildConfig.DEBUG) {
                    launch {
                        database.dirtyDao().getDirtyTaskIds().collect { ids ->
                            dirtyTaskIds = ids.toSet()
                            recyclerAdapter?.dirtyTaskIds = dirtyTaskIds
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            // STARTED, not RESUMED: the sort sheet is a translucent activity over this list,
            // and a sort picked there should show at once
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewHolderFactory.dateFormatter = DateFormatter.create(requireContext().is24HourFormat)
                listViewModel.state.collect {
                    val results = it.tasks
                    if (results is TasksResults.Results) {
                        submitList(results.tasks)
                        if (results.tasks.isEmpty()) {
                            swipeRefreshLayout.visibility = View.GONE
                            emptyRefreshLayout.visibility = View.VISIBLE
                        } else {
                            swipeRefreshLayout.visibility = View.VISIBLE
                            emptyRefreshLayout.visibility = View.GONE
                        }
                        swipeRefreshLayout.isRefreshing = it.syncOngoing
                        emptyRefreshLayout.isRefreshing = it.syncOngoing
                        reportFullyDrawn()
                    }
                }
            }
        }
        setupRefresh(swipeRefreshLayout)
        setupRefresh(emptyRefreshLayout)
        binding.toolbar.title = filter.title
        // no setting for it, so the trash says how long it keeps things
        binding.toolbar.subtitle = if (filter is TrashFilter) {
            getString(R.string.trash_retention, org.tasks.service.TaskDeleter.TRASH_RETENTION_DAYS)
        } else {
            null
        }
        binding.toolbar.setTitleTextAppearance(requireContext(), com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall)
        binding.toolbar.setTitleTextColor(themeColor.primaryColor)
        binding.appbarlayout.addOnOffsetChangedListener { _, verticalOffset ->
            if (verticalOffset == 0 && binding.bottomAppBar.isScrolledDown) {
                binding.bottomAppBar.performShow()
            }
        }

        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
        val scrimColor = typedValue.data
        binding.systemBarScrim.setBackgroundColor((scrimColor and 0x00FFFFFF) or 0xCC000000.toInt()) // 80% opacity
        with (binding.fab) {
            backgroundTintList = ColorStateList.valueOf(themeColor.primaryColor)
            imageTintList = ColorStateList.valueOf(themeColor.colorOnPrimary)
        }
        with (binding.bottomAppBar) {
            setOnMenuItemClickListener(this@TaskListFragment)
            setNavigationOnClickListener {
                activity?.hideKeyboard()
                onClickMenu()
            }
        }
        setupToolbarMenu()
        setupBottomAppBarMenu()
        bannerAdapter.content = {
            val context = LocalContext.current
            val mainActivityState = mainViewModel.state.collectAsStateWithLifecycle().value
            val state = listViewModel.state.collectAsStateWithLifecycle().value
            val banner = listViewModel.banner.collectAsStateWithLifecycle().value
            BackHandler(enabled = state.searchQuery != null && mainActivityState.task == null) {
                Timber.d("onBackPressed")
                if (search.isActionViewExpanded) {
                    search.collapseActionView()
                }
            }
            TasksTheme(
                theme = theme.themeBase.index,
                primary = theme.themeColor.primaryColor,
            ) {
                val notificationPermissions = if (AndroidUtilities.atLeastTiramisu()) {
                    rememberPermissionState(
                        Manifest.permission.POST_NOTIFICATIONS,
                        onPermissionResult = { success ->
                            if (success) {
                                NotificationSchedulerWork.enqueueWork(context)
                                listViewModel.dismissBanner(tookAction = true)
                            }
                        }
                    )
                } else {
                    null
                }

                if (banner != null) {
                    val offsetX = remember(banner) { Animatable(0f) }
                    val scope = rememberCoroutineScope()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                            .pointerInput(banner) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        scope.launch {
                                            if (abs(offsetX.value) > size.width * 0.4f) {
                                                val target = if (offsetX.value > 0) {
                                                    size.width.toFloat()
                                                } else {
                                                    -size.width.toFloat()
                                                }
                                                offsetX.animateTo(target)
                                                listViewModel.dismissBanner()
                                            } else {
                                                offsetX.animateTo(0f)
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch { offsetX.animateTo(0f) }
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        scope.launch {
                                            offsetX.snapTo(offsetX.value + dragAmount)
                                        }
                                    }
                                )
                            }
                    ) {
                        when (banner) {
                            is Banner.NotificationsDisabled ->
                                NotificationsDisabledBanner(
                                    settings = {
                                        listViewModel.dismissBanner(tookAction = true)
                                        if (notificationPermissions?.status?.shouldShowRationale == true) {
                                            context.openAppNotificationSettings()
                                        } else {
                                            notificationPermissions?.launchPermissionRequest()
                                        }
                                    },
                                    dismiss = { listViewModel.dismissBanner() },
                                )

                            Banner.AlarmsDisabled ->
                                AlarmsDisabledBanner(
                                    settings = {
                                        listViewModel.dismissBanner(tookAction = true)
                                        context.startActivity(
                                            Intent(context, org.tasks.preferences.NotificationPreferences::class.java)
                                        )
                                    },
                                    dismiss = { listViewModel.dismissBanner() },
                                )

                            is Banner.SubscriptionRequired ->
                                SubscriptionRequiredBanner(
                                    nameRes = banner.nameRes,
                                    isTasksOrg = banner.isTasksOrg,
                                    subscribe = {
                                        val isTasksOrg = banner.isTasksOrg
                                        listViewModel.dismissBanner(tookAction = true)
                                        context.startActivity(
                                            Intent(
                                                context,
                                                PurchaseActivity::class.java
                                            ).apply {
                                                putExtra(
                                                    PurchaseActivityViewModel.EXTRA_NAME_YOUR_PRICE,
                                                    !isTasksOrg
                                                )
                                                putExtra(
                                                    PurchaseActivityViewModel.EXTRA_SOURCE,
                                                    "banner"
                                                )
                                            }
                                        )
                                    },
                                    dismiss = { listViewModel.dismissBanner() },
                                )

                            Banner.WarnGoogleTasks ->
                                SyncWarningGoogleTasks(
                                    moreInfo = {
                                        listViewModel.dismissBanner(tookAction = true)
                                        context.openUri(R.string.url_google_tasks)
                                    },
                                    dismiss = { listViewModel.dismissBanner() },
                                )

                            Banner.WarnMicrosoft ->
                                SyncWarningMicrosoft(
                                    moreInfo = {
                                        listViewModel.dismissBanner(tookAction = true)
                                        context.openUri(R.string.url_microsoft)
                                    },
                                    dismiss = { listViewModel.dismissBanner() },
                                )

                            Banner.AppUpdated ->
                                AppUpdatedBanner(
                                    whatsNew = {
                                        listViewModel.dismissBanner(tookAction = true)
                                        context.openUri(TasksUrls.CHANGELOG)
                                    },
                                    dismiss = { listViewModel.dismissBanner() },
                                )

                            else -> {}
                        }
                    }
                }
            }
        }
        ViewCompat.requestApplyInsets(binding.toolbar)
        return binding.root
    }

    private fun reportFullyDrawn() {
        if (!reportedFullyDrawn) {
            reportedFullyDrawn = true
            activity?.reportFullyDrawn()
        }
    }

    private fun submitList(tasks: SectionedDataSource) {
        if (recyclerAdapter !is DragAndDropRecyclerAdapter) {
            setAdapter(
                    DragAndDropRecyclerAdapter(
                        adapter = taskAdapter,
                        recyclerView = binding.bodyStandard.recyclerView,
                        viewHolderFactory = viewHolderFactory,
                        taskList = this,
                        tasks = tasks,
                        preferences = preferences,
                        toggleCollapsed = { listViewModel.toggleCollapsed(it) },
                    )
            )
        } else {
            recyclerAdapter?.submitList(tasks)
        }
    }

    private fun setAdapter(adapter: DragAndDropRecyclerAdapter) {
        recyclerAdapter = adapter
        adapter.dirtyTaskIds = dirtyTaskIds
        adapter.dirtyColor = themeColor.primaryColor
        binding.bodyStandard.recyclerView.adapter = ConcatAdapter(bannerAdapter, adapter)
        taskAdapter.setDataSource(adapter)
    }

    private fun setupToolbarMenu() {
        val toolbar = binding.toolbar
        toolbar.setOnMenuItemClickListener(this)
        val menu = toolbar.menu
        menu.clear()
        toolbar.inflateMenu(R.menu.menu_task_list_fragment_top)
        when (filter) {
            is CaldavFilter -> R.menu.menu_caldav_list_fragment
            is CustomFilter -> R.menu.menu_custom_filter
            else -> null
        }?.let {
            toolbar.inflateMenu(it)
        }
        search = binding.toolbar.menu.findItem(R.id.menu_search).apply {
            setOnActionExpandListener(this@TaskListFragment)
            isVisible = false
        }
    }

    private fun setupBottomAppBarMenu() {
        val appBar = binding.bottomAppBar
        val menu = appBar.menu
        menu.clear()
        if (filter is PlaceFilter) {
            appBar.inflateMenu(R.menu.menu_location_actions)
        }
        appBar.inflateMenu(R.menu.menu_task_list_fragment_bottom)
        val memos = filter is MemosFilter
        menu.findItem(R.id.menu_voice_add).isVisible = !memos && device.voiceInputAvailable() && filter.isWritable
        menu.findItem(R.id.menu_clear_completed).apply {
            isVisible = !memos && filter.isWritable
            if (filter is CompletedFilter) {
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
        }
        val trash = filter is TrashFilter
        menu.findItem(R.id.menu_empty_trash).isVisible = trash
        if (trash || memos) {
            menu.findItem(R.id.menu_search).isVisible = false
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        Timber.d("onMenuItemClick($item)")
        return when (item.itemId) {
            R.id.menu_settings -> {
                settingsLauncher.launch(Intent(context, MainPreferences::class.java))
                true
            }
            R.id.menu_search -> {
                if (!search.isActionViewExpanded) {
                    search.isVisible = true
                    search.expandActionView()
                }
                true
            }
            R.id.menu_voice_add -> {
                safeStartActivityForResult(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                            putExtra(
                                    RecognizerIntent.EXTRA_PROMPT,
                                    getString(R.string.voice_create_prompt)
                            )
                        },
                        VOICE_RECOGNITION_REQUEST_CODE
                )
                true
            }
            R.id.menu_clear_completed -> {
                lifecycleScope.launch {
                    val tasks = withContext(Dispatchers.Default) {
                        listViewModel.getTasksToClear()
                    }
                    // no confirmation: the snackbar offers undo, the trash keeps them after that
                    val deleted = if (tasks.isEmpty()) {
                        emptyList()
                    } else {
                        withContext(NonCancellable) { listViewModel.markDeleted(tasks) }.map { it.id }
                    }
                    makeSnackbar(R.string.trash_moved, deleted.size.toString())
                        ?.withUndoTrash(deleted)
                        ?.show()
                }
                true
            }
            R.id.menu_empty_trash -> {
                dialogBuilder
                    .newDialog(R.string.trash_empty)
                    .setMessage(R.string.trash_empty_confirmation, TaskDeleter.TRASH_RETENTION_DAYS)
                    .setPositiveButton(R.string.ok) { _, _ -> deleteForever(null) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
            R.id.menu_filter_settings -> {
                listSettingsRequest.launch(
                    Intent(activity, FilterSettingsActivity::class.java)
                        .putExtra(FilterSettingsActivity.TOKEN_FILTER, filter)
                )
                true
            }
            R.id.menu_caldav_list_fragment -> {
                val filter = filter as? CaldavFilter ?: return false
                listSettingsRequest.launch(
                    Intent(activity, filter.account.listSettingsClass())
                        .putExtra(BaseCaldavCalendarSettingsActivity.EXTRA_CALDAV_ACCOUNT, filter.account)
                        .putExtra(BaseCaldavCalendarSettingsActivity.EXTRA_CALDAV_CALENDAR, filter.calendar)
                )
                true
            }
            R.id.menu_open_map -> {
                (filter as PlaceFilter).place.open(context)
                true
            }
            else -> onOptionsItemSelected(item)
        }
    }

    private fun createNewTask() = lifecycleScope.launch {
        if (hasWritableList) {
            shortcutManager.reportShortcutUsed(ShortcutManager.SHORTCUT_NEW_TASK)
            onTaskListItemClicked(addTask(""))
            firebase.addTask("fab")
        } else {
            Timber.e("createNewTask(): No writable list")
            val account = caldavDao.getAccountForNewList(tasksAccountDataRepository)
            if (account != null) {
                listSettingsRequest.launch(
                    Intent(activity, account.listSettingsClass())
                        .putExtra(BaseCaldavCalendarSettingsActivity.EXTRA_CALDAV_ACCOUNT, account)
                )
            } else {
                val localAccount = caldavDao.getOrCreateLocalAccount()
                listSettingsRequest.launch(
                    Intent(activity, LocalListSettingsActivity::class.java)
                        .putExtra(BaseCaldavCalendarSettingsActivity.EXTRA_CALDAV_ACCOUNT, localAccount)
                )
            }
        }
    }

    private suspend fun addTask(title: String): Task =
        if (filter is MemosFilter) taskCreator.createMemo(title) else taskCreator.createWithValues(filter, title)

    private fun setupRefresh(layout: SwipeRefreshLayout) {
        layout.setOnRefreshListener(this)
        layout.setColorSchemeColors(
                colorProvider.getPriorityColor(0),
                colorProvider.getPriorityColor(1),
                colorProvider.getPriorityColor(2),
                colorProvider.getPriorityColor(3))
    }

    override fun onResume() {
        super.onResume()
        val features = featureGate.current
        binding.bottomAppBar.navigationIcon =
            androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_outline_menu_24px)
        if (!mainViewModel.state.value.filter.fits(features)) {
            lifecycleScope.launch { mainViewModel.setFilter(MyTasksFilter.create(), remember = false) }
        }
        listViewModel.invalidate()
        localBroadcastManager.registerTaskCompletedReceiver(repeatConfirmationReceiver)
        recyclerAdapter?.let { it.notifyItemRangeChanged(0, it.itemCount) }
        recyclerViewState?.let { state ->
            binding.bodyStandard.recyclerView.layoutManager?.onRestoreInstanceState(state)
            recyclerViewState = null
        }
    }

    private fun makeSnackbar(@StringRes res: Int, vararg args: Any?): Snackbar? {
        return makeSnackbar(getString(res, *args))
    }

    private fun makeSnackbar(text: String): Snackbar? = activity?.let {
        Snackbar.make(binding.taskListCoordinator, text, 4000)
                .setAnchorView(R.id.fab)
                .setBackgroundTint(it.getColor(R.color.dialog_background))
                .setTextColor(MaterialColors.getColor(it, com.google.android.material.R.attr.colorOnSurface, 0))
                .setActionTextColor(themeColor.primaryColor)
            .apply {
                val offset = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    16f,
                    context.resources.displayMetrics
                )
                view.translationY = -offset
            }
    }

    override fun onPause() {
        super.onPause()
        recyclerViewState = binding.bodyStandard.recyclerView.layoutManager?.onSaveInstanceState()
        localBroadcastManager.unregisterReceiver(repeatConfirmationReceiver)
    }

    fun loadTaskListContent() {
        listViewModel.invalidate()
    }

    fun getFilter(): Filter {
        return requireArguments().getParcelable(EXTRA_FILTER)!!
    }

    fun memosDragEnabled(): Boolean = listViewModel.memosDragEnabled()

    private fun onTaskCreated(tasks: List<Task>) {
        for (task in tasks) {
            onTaskCreated(task.uuid)
        }
        loadTaskListContent()
    }

    private fun onTaskCreated(uuid: String) {
        lifecycleScope.launch {
            taskAdapter.onTaskCreated(uuid)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            VOICE_RECOGNITION_REQUEST_CODE -> if (resultCode == RESULT_OK) {
                lifecycleScope.launch {
                    val match: List<String>? = data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if (!match.isNullOrEmpty() && match[0].isNotEmpty()) {
                        var recognizedSpeech = match[0]
                        recognizedSpeech = (recognizedSpeech.substring(0, 1)
                            .uppercase(Locale.getDefault())
                                + recognizedSpeech.substring(1).lowercase(Locale.getDefault()))
                        onTaskListItemClicked(addTask(recognizedSpeech))
                        firebase.addTask("voice")
                    }
                }
            }
            REQUEST_TAG_TASKS -> if (resultCode == RESULT_OK) {
                lifecycleScope.launch {
                    tagDataDao.applyTags(
                            taskDao
                                .fetch(data!!.getSerializableExtra(TagPickerActivity.EXTRA_TASKS) as ArrayList<Long>)
                                .filterNot { it.readOnly },
                            data.getParcelableArrayListExtra(TagPickerActivity.EXTRA_PARTIALLY_SELECTED)!!,
                            data.getParcelableArrayListExtra(TagPickerActivity.EXTRA_SELECTED)!!
                    )
                    localBroadcastManager.broadcastRefresh()
                }
                finishActionMode()
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun onTaskListItemClicked(task: Task?) = lifecycleScope.launch {
        mainViewModel.setTask(task)
    }

    override fun onMenuItemActionExpand(item: MenuItem): Boolean {
        firebase.logEvent(R.string.event_search)
        search.setOnQueryTextListener(this)
        listViewModel.setSearchQuery("")
        return true
    }

    override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
        search.isVisible = false
        search.setOnQueryTextListener(null)
        listViewModel.setFilter(filter)
        listViewModel.setSearchQuery(null)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        mainViewModel.setFilter(SearchFilter(getString(R.string.FLA_search_filter, query.trim()), query.trim()))
        search.collapseActionView()
        return true
    }

    override fun onQueryTextChange(query: String): Boolean {
        listViewModel.setSearchQuery(query)
        return true
    }

    override fun onCreateActionMode(actionMode: ActionMode, menu: Menu): Boolean {
        val inflater = actionMode.menuInflater
        inflater.inflate(R.menu.menu_multi_select, menu)
        if (filter.isReadOnly) {
            listOf(R.id.edit_tags, R.id.move_tasks, R.id.reschedule, R.id.copy_tasks, R.id.delete)
                .forEach { menu.findItem(it).isVisible = false }
        }
        val trash = filter is TrashFilter
        listOf(R.id.restore, R.id.delete_forever).forEach { menu.findItem(it).isVisible = trash }
        menu.findItem(R.id.finish_series).isVisible = filter.isWritable && featureGate.current.repeat
        if (filter is MemosFilter) {
            listOf(R.id.move_tasks, R.id.reschedule, R.id.finish_series)
                .forEach { menu.findItem(it).isVisible = false }
        }
        if (trash) {
            listOf(R.id.edit_priority, R.id.menu_share).forEach { menu.findItem(it).isVisible = false }
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val selected = taskAdapter.getSelected()
        return when (item.itemId) {
            R.id.edit_tags -> {
                logMultiSelect("edit_tags", selected.size)
                lifecycleScope.launch {
                    val tags = tagDataDao.getTagSelections(selected)
                    val intent = Intent(context, TagPickerActivity::class.java)
                    intent.putExtra(TagPickerActivity.EXTRA_TASKS, selected)
                    intent.putParcelableArrayListExtra(
                            TagPickerActivity.EXTRA_PARTIALLY_SELECTED,
                            ArrayList(tagDataDao.getByUuid(tags.first!!)))
                    intent.putParcelableArrayListExtra(
                            TagPickerActivity.EXTRA_SELECTED, ArrayList(tagDataDao.getByUuid(tags.second!!)))
                    startActivityForResult(intent, REQUEST_TAG_TASKS)
                }
                true
            }
            R.id.edit_priority -> {
                logMultiSelect("edit_priority", selected.size)
                lifecycleScope.launch {
                    taskDao
                        .fetch(selected)
                        .filterNot { it.readOnly }
                        .takeIf { it.isNotEmpty() }
                        ?.let {
                            newPriorityPicker(it)
                                .show(parentFragmentManager, FRAG_TAG_PRIORITY_PICKER)
                        }
                }
                true
            }
            R.id.move_tasks -> {
                logMultiSelect("move", selected.size)
                lifecycleScope.launch {
                    val singleFilter = taskMover.getSingleFilter(selected)
                    listPickerLauncher.launch(
                        context = requireActivity(),
                        selectedFilter = singleFilter,
                        listsOnly = true,
                    )
                }
                true
            }
            R.id.reschedule -> {
                logMultiSelect("reschedule", selected.size)
                lifecycleScope.launch {
                    taskDao
                            .fetch(selected)
                            .filterNot { it.readOnly }
                            .takeIf { it.isNotEmpty() }
                            ?.let {
                                newDateTimePicker(
                                        false,
                                        *it.toTypedArray())
                                        .show(parentFragmentManager, FRAG_TAG_DATE_TIME_PICKER)
                            }
                }
                finishActionMode()
                true
            }
            R.id.menu_select_all -> {
                logMultiSelect("select_all", selected.size)
                lifecycleScope.launch {
                    val ids = withContext(Dispatchers.Default) {
                        taskDao.fetchTasks(listViewModel.queryPreferences(filter), filter, featureGate.current)
                            .map(TaskContainer::id)
                    }
                    setSelected(ids)
                }
                true
            }
            R.id.menu_share -> {
                logMultiSelect("share", selected.size)
                lifecycleScope.launch {
                    val tasks = withContext(Dispatchers.Default) {
                        val queryPreferences = listViewModel.queryPreferences(filter)
                        selected
                            .chunkedMap {
                                taskDao.fetchTasks(
                                    queryPreferences,
                                    FilterImpl(
                                        sql = QueryTemplate()
                                            .where(Task.ID.`in`(it))
                                            .toString()
                                    ),
                                    featureGate.current,
                                )
                            }
                    }
                    send(tasks)
                }
                true
            }
            R.id.delete -> {
                logMultiSelect("delete", selected.size)
                // no confirmation: the tasks go to the trash
                lifecycleScope.launch {
                    // uncompleted occurrences of a running series can't be deleted: complete,
                    // postpone or finish the series
                    val deletable = taskDao.fetch(selected)
                        .filterNot { task ->
                            !task.isCompleted &&
                                task.series?.let { seriesDao.getByUuid(it) }?.finished == 0L
                        }
                        .map { it.id }
                    val dropped = deletable.size < selected.size
                    when {
                        deletable.isNotEmpty() -> deleteSelectedItems(deletable, dropped)
                        else -> makeSnackbar(R.string.series_cannot_delete)?.show()
                    }
                }
                true
            }
            R.id.restore -> {
                restoreSelectedItems(selected)
                true
            }
            R.id.finish_series -> {
                lifecycleScope.launch {
                    val series = taskDao
                        .fetch(selected)
                        .filter { it.series != null && !it.isCompleted && !it.readOnly }
                    if (series.isEmpty()) {
                        makeSnackbar(R.string.series_none)?.show()
                    } else {
                        // the checkbox only finishes one occurrence, so this one asks first
                        dialogBuilder
                            .newDialog(R.string.series_finish)
                            .setMessage(R.string.series_finish_confirmation)
                            .setPositiveButton(R.string.ok) { _, _ -> finishSeries(series) }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                }
                true
            }
            R.id.delete_forever -> {
                dialogBuilder
                    .newDialog(R.string.trash_delete_forever)
                    .setMessage(
                        R.string.delete_tasks_warning,
                        resources.getQuantityString(R.plurals.Ntasks, selected.size, selected.size),
                    )
                    .setPositiveButton(R.string.ok) { _, _ -> deleteForever(selected) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
            R.id.copy_tasks -> {
                logMultiSelect("copy", selected.size)
                dialogBuilder
                        .newDialog(R.string.copy_selected_tasks)
                        .setPositiveButton(
                                R.string.ok) { _, _ -> copySelectedItems(selected) }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                true
            }
            else -> false
        }
    }

    private fun logMultiSelect(type: String, count: Int) {
        firebase.logEvent(
            R.string.event_multi_select,
            R.string.param_type to type,
            R.string.param_count to count,
        )
    }

    private fun send(tasks: List<TaskContainer>) {
        val output = tasks.joinToString("\n") { t -> Task
            "${(if (t.isCompleted) "☑" else "☐").padStart(1 + t.indent * 3, ' ')} ${t.title}"
        }
        val intent = ShareCompat
            .IntentBuilder(requireContext())
            .setType("text/plain")
            .setSubject(filter.title)
            .setText(output)
            .createChooserIntent()
        startActivity(intent)
        finishActionMode()
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        this.mode = null
        if (taskAdapter.numSelected > 0) {
            taskAdapter.clearSelections()
            recyclerAdapter?.notifyDataSetChanged()
        }
    }

    private fun showDateTimePicker(task: TaskContainer) {
        val fragmentManager = parentFragmentManager
        if (fragmentManager.findFragmentByTag(FRAG_TAG_DATE_TIME_PICKER) == null) {
            newDateTimePicker(false, task.task)
                .show(fragmentManager, FRAG_TAG_DATE_TIME_PICKER)
        }
    }

    val isActionModeActive: Boolean
        get() = mode != null

    fun startActionMode() {
        if (mode == null) {
            mode = (activity as AppCompatActivity).startSupportActionMode(this)
            updateModeTitle()
            Flags.set(Flags.TLFP_NO_INTERCEPT_TOUCH)
        }
    }

    fun finishActionMode() {
        mode?.finish()
    }

    fun updateModeTitle() {
        if (mode != null) {
            val count = max(1, taskAdapter.numSelected)
            mode!!.title = count.toString()
        }
    }

    private fun deleteSelectedItems(
        tasks: List<Long>,
        skippedOccurrences: Boolean = false,
    ) = lifecycleScope.launch {
        finishActionMode()

        val result = withContext(NonCancellable) {
            listViewModel.markDeleted(tasks)
        }
        result.forEach { taskAdapter.onTaskDeleted(it) }
        loadTaskListContent()
        if (tasks.contains(mainViewModel.state.value.task?.id)) {
            mainViewModel.setTask(null)
        }
        if (skippedOccurrences) {
            makeSnackbar(R.string.series_cannot_delete)?.show()
        } else {
            makeSnackbar(R.string.trash_moved, result.size.toString())
                ?.withUndoTrash(result.map { it.id })
                ?.show()
        }
    }

    private fun Snackbar.withUndoTrash(ids: List<Long>) = apply {
        if (ids.isNotEmpty()) {
            setAction(R.string.DLG_undo) { restoreSelectedItems(ids) }
        }
    }

    private fun restoreSelectedItems(tasks: List<Long>) = lifecycleScope.launch {
        finishActionMode()
        val restored = withContext(NonCancellable) {
            listViewModel.restore(tasks)
        }
        loadTaskListContent()
        makeSnackbar(R.string.trash_restored, restored.size.toString())?.show()
    }

    private fun finishSeries(tasks: List<Task>) = lifecycleScope.launch {
        finishActionMode()
        withContext(NonCancellable) {
            tasks.mapNotNull { it.series }.distinct().forEach { seriesScheduler.finish(it) }
        }
        loadTaskListContent()
    }

    /** Null empties the whole trash. */
    private fun deleteForever(tasks: List<Long>?) = lifecycleScope.launch {
        finishActionMode()
        withContext(NonCancellable) {
            listViewModel.deleteForever(tasks)
        }
        loadTaskListContent()
    }

    private fun setSelected(tasks: List<Long>) {
        taskAdapter.setSelected(tasks)
        updateModeTitle()
        recyclerAdapter?.notifyDataSetChanged()
    }

    private fun copySelectedItems(tasks: List<Long>) = lifecycleScope.launch {
        val duplicates = withContext(NonCancellable) {
            taskDuplicator.duplicate(tasks)
        }
        onTaskCreated(duplicates)
        setSelected(duplicates.map(Task::id))
        makeSnackbar(R.string.copy_multiple_tasks_confirmation, duplicates.size.toString())?.show()
    }

    fun canSwipe(task: TaskContainer) =
        !isActionModeActive && filter !is TrashFilter && !task.isReadOnly

    /** A swipe trashes in «Завершено» and on notes, and toggles done everywhere else. */
    fun swipeTrashes(task: TaskContainer) = filter is CompletedFilter || task.task.memo

    fun onSwiped(task: TaskContainer) {
        if (swipeTrashes(task)) {
            deleteSelectedItems(listOf(task.id))
        } else {
            onCompletedTask(task, !task.isCompleted)
        }
    }

    override fun onCompletedTask(task: TaskContainer, newState: Boolean) {
        if (task.isReadOnly || filter is TrashFilter || task.task.memo) {
            return
        }
        lifecycleScope.launch {
            taskCompleter.setComplete(task.task, newState)
            firebase.completeTask("task_list")
            taskAdapter.onCompletedTask(task.uuid, newState)
            loadTaskListContent()
        }
    }

    override fun onLinkClicked(vh: TaskViewHolder, url: String) =
        if (isActionModeActive) {
            recyclerAdapter?.toggle(vh)
            true
        } else {
            false
        }

    override fun onClick(taskViewHolder: TaskViewHolder) {
        if (filter is TrashFilter) {
            // a trashed task is not edited, only restored or deleted
            startActionMode()
        }
        if (isActionModeActive) {
            recyclerAdapter?.toggle(taskViewHolder)
        } else {
            onTaskListItemClicked(taskViewHolder.task.task)
        }
    }

    override fun onClick(filter: Filter) {
        if (!isActionModeActive) {
            mainViewModel.setFilter(filter)
        }
    }

    override fun onLongPress(taskViewHolder: TaskViewHolder): Boolean {
        if (recyclerAdapter?.dragAndDropEnabled() != true) {
            startActionMode()
        }
        if (isActionModeActive && !taskViewHolder.moving) {
            recyclerAdapter?.toggle(taskViewHolder)
        }
        return true
    }

    override fun onChangeDueDate(task: TaskContainer) {
        if (task.isReadOnly || filter is TrashFilter) {
            return
        }
        showDateTimePicker(task)
    }

    override fun toggleSubtasks(task: Long, collapsed: Boolean) {
        lifecycleScope.launch {
            taskSaver.setCollapsed(task, collapsed)
        }
    }

    private inner class RepeatConfirmationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lifecycleScope.launch {
                val tasks =
                    (intent.getSerializableExtra(EXTRAS_TASK_ID) as? ArrayList<Long>)
                        ?.let {
                            Timber.d("Repeating tasks: $it")
                            taskDao.fetch(it)
                        }
                        ?.filterNot { it.readOnly }
                        ?.takeIf { it.isNotEmpty() }
                        ?: return@launch
                val isRecurringCompletion =
                    tasks.size == 1 && tasks.first().let { it.isRecurring && !it.isCompleted }
                val oldDueDate = if (isRecurringCompletion) {
                    intent.getLongExtra(EXTRAS_OLD_DUE_DATE, 0)
                } else {
                    0
                }
                val undoCompletion = View.OnClickListener {
                    lifecycleScope.launch {
                        // occurrences (and their discarded subtasks) first, they come back by themselves
                        seriesScheduler.undoCompletion(tasks.map { it.id })
                        tasks
                            .filter { it.series == null }
                            .partition { it.isRecurring }
                            .let { (recurring, notRecurring) ->
                                recurring.forEach { repeatTaskHelper.undoRepeat(it, oldDueDate) }
                                taskCompleter.setComplete(notRecurring, 0L)
                            }
                    }
                }
                if (isRecurringCompletion) {
                    val task = tasks.first()
                    val fullTitle = markdown.markdown().toMarkdown(task.title)?.toString() ?: ""
                    val date = DateFormatter.create(context.is24HourFormat).relativeDateTime(
                        task.dueDate,
                        DateStyle.LONG,
                        lowercase = true
                    )
                    val maxTitleLength = 30
                    val title = if (fullTitle.length > maxTitleLength) {
                        fullTitle.take(maxTitleLength - 1).trimEnd() + "…"
                    } else {
                        fullTitle
                    }
                    val text = getString(R.string.repeat_snackbar, title, date)
                    makeSnackbar(text)?.setAction(R.string.DLG_undo, undoCompletion)?.show()
                } else {
                    val text = if (tasks.size == 1) {
                        context.getString(R.string.snackbar_task_completed)
                    } else {
                        context.getString(R.string.snackbar_tasks_completed, tasks.size)
                    }
                    makeSnackbar(text)?.setAction(R.string.DLG_undo, undoCompletion)?.show()
                }
            }
        }
    }

    companion object {
        const val ACTION_RELOAD = "action_reload"
        const val ACTION_DELETED = "action_deleted"
        private const val EXTRA_SELECTED_TASK_IDS = "extra_selected_task_ids"
        private const val EXTRA_RECYCLER_STATE = "extra_recycler_state"
        private const val VOICE_RECOGNITION_REQUEST_CODE = 1234
        const val EXTRA_FILTER = "extra_filter"
        private const val FRAG_TAG_DATE_TIME_PICKER = "frag_tag_date_time_picker"
        private const val FRAG_TAG_PRIORITY_PICKER = "frag_tag_priority_picker"
        private const val REQUEST_TAG_TASKS = 10106
    }
}
