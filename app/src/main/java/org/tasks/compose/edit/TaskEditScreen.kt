package org.tasks.compose.edit

import org.tasks.themes.TasksIcons
import org.tasks.compose.components.SymbolIcon
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import org.tasks.themes.TestTags
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.IntentCompat.getParcelableExtra
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todoroo.andlib.utility.AndroidUtilities.atLeastOreoMR1
import com.todoroo.astrid.activity.BeastModePreferences
import com.todoroo.astrid.activity.MainActivity
import com.todoroo.astrid.files.FilesControlSet
import com.todoroo.astrid.repeats.RepeatControlSet
import com.todoroo.astrid.tags.TagsControlSet
import com.todoroo.astrid.ui.ReminderControlSet
import com.todoroo.astrid.ui.StartDateControlSet
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.compose.BeastModeBanner
import org.tasks.compose.FilterSelectionActivity.Companion.EXTRA_FILTER
import org.tasks.compose.FilterSelectionActivity.Companion.launch
import org.tasks.compose.pickers.DueDatePickerSheet
import org.tasks.compose.pickers.dueDateFromSelection
import org.tasks.compose.pickers.dueDateToSelection
import org.tasks.data.Location
import org.tasks.data.createGeofence
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Place
import org.tasks.data.entity.Task
import org.tasks.data.entity.UserActivity
import org.tasks.dialogs.Linkify
import org.tasks.extensions.Context.findActivity
import org.tasks.extensions.Context.is24HourFormat
import org.tasks.extensions.hideKeyboardThen
import org.tasks.files.FileHelper
import org.tasks.filters.CaldavFilter
import org.tasks.filters.Filter
import org.tasks.fragments.CommentBarFragment
import org.tasks.kmp.org.tasks.extensions.gesturesDisabled
import org.tasks.kmp.org.tasks.taskedit.TaskEditViewState
import org.tasks.markdown.MarkdownProvider
import org.tasks.preferences.Preferences
import org.tasks.resources.getString
import org.tasks.repeats.SeriesEditScope
import org.tasks.themes.TasksTheme
import org.tasks.location.LocationPickerActivity
import org.tasks.ui.LocationControlSet
import org.tasks.ui.TaskEditViewModel
import org.tasks.ui.TaskEditViewModel.MemoConvertBlock
import org.tasks.ui.TaskEditViewModel.Companion.TAG_CREATION
import org.tasks.checklist.Checklist
import org.tasks.ui.TaskEditViewModel.Companion.TAG_CHECKLIST
import org.tasks.ui.TaskEditViewModel.Companion.TAG_DESCRIPTION
import org.tasks.ui.TaskEditViewModel.Companion.TAG_DUE_DATE
import org.tasks.ui.TaskEditViewModel.Companion.TAG_ADVANCE
import org.tasks.reminders.advance
import org.tasks.ui.TaskEditViewModel.Companion.TAG_LIST
import org.tasks.ui.TaskEditViewModel.Companion.TAG_PRIORITY
import org.tasks.ui.TaskEditViewModel.Companion.TAG_TITLE
import org.tasks.utility.copyToClipboard
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.delete_task
import tasks.kmp.generated.resources.pin
import tasks.kmp.generated.resources.pin_series_pinned
import tasks.kmp.generated.resources.pin_this_only
import tasks.kmp.generated.resources.pin_whole_series
import tasks.kmp.generated.resources.unpin
import org.tasks.compose.edit.ConvertToMemoDialog
import org.tasks.compose.edit.ConvertToTaskDialog
import tasks.kmp.generated.resources.convert_to_memo
import tasks.kmp.generated.resources.convert_to_memo_blocked_children
import tasks.kmp.generated.resources.convert_to_memo_blocked_series
import tasks.kmp.generated.resources.convert_to_task
import tasks.kmp.generated.resources.draft_switch_to_memo
import tasks.kmp.generated.resources.draft_switch_to_task
import tasks.kmp.generated.resources.menu_discard_changes
import timber.log.Timber
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    editViewModel: TaskEditViewModel,
    viewState: TaskEditViewState,
    comments: List<UserActivity>,
    save: (SeriesEditScope?) -> Unit,
    discard: () -> Unit,
    delete: () -> Unit,
    dismissBeastMode: () -> Unit,
    deleteComment: (UserActivity) -> Unit,
    onClickRepeat: () -> Unit,
    onConfigureGeofence: () -> Unit = {},
    repeatRuleToString: (String?) -> String?,
    markdownProvider: MarkdownProvider,
    linkify: Linkify?,
    locale: Locale,
    preferences: Preferences,
    colorProvider: (Int) -> Int,
    draftListFilter: Filter? = null,
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { context.is24HourFormat }
    val scope = rememberCoroutineScope()
    var showDueDatePicker by rememberSaveable { mutableStateOf(false) }
    val placePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val place = result.data?.let {
            getParcelableExtra(it, LocationPickerActivity.EXTRA_PLACE, Place::class.java)
        } ?: return@rememberLauncherForActivityResult
        scope.launch {
            val existing = editViewModel.viewState.value.location
            val geofence = if (existing == null) {
                createGeofence(place.uid, preferences)
            } else {
                existing.geofence.copy(place = place.uid)
            }
            editViewModel.setLocation(Location(geofence, place))
            onConfigureGeofence()
        }
    }
    val pickPlace = {
        val intent = Intent(context, LocationPickerActivity::class.java)
        viewState.location?.let { intent.putExtra(LocationPickerActivity.EXTRA_PLACE, it.place as Parcelable) }
        placePicker.launch(intent)
    }
    var showDiscardConfirmation by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSeriesScope by rememberSaveable { mutableStateOf(false) }
    var showPinScope by rememberSaveable { mutableStateOf(false) }
    var showTaskInfo by rememberSaveable { mutableStateOf(false) }
    var showConvertToMemo by rememberSaveable { mutableStateOf(false) }
    var showConvertToTask by rememberSaveable { mutableStateOf(false) }
    var convertMenuExpanded by remember { mutableStateOf(false) }
    var convertBlock by remember { mutableStateOf(MemoConvertBlock.NONE) }
    var convertHasGoogle by remember { mutableStateOf(false) }
    // The save button on a new task without a title points at the title instead of closing.
    var titleMissing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewState.task.id, viewState.task.memo, viewState.isNew) {
        if (!viewState.isNew && editViewModel.memosEnabled) {
            convertBlock = editViewModel.memoConvertBlock()
            convertHasGoogle = !viewState.task.memo && editViewModel.hasRemoteListForConvert()
        }
    }
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        if (atLeastOreoMR1() && viewState.showEditScreenWithoutUnlock) {
            activity?.setShowWhenLocked(true)
        }

        onDispose {
            if (atLeastOreoMR1()) {
                activity?.setShowWhenLocked(false)
            }
        }
    }
    val requestDiscard = {
        if (editViewModel.hasChanges()) {
            val prompt = { showDiscardConfirmation = true }
            context.findActivity()?.hideKeyboardThen(prompt) ?: prompt()
        } else {
            discard()
        }
    }
    // an occurrence asks once per save whether the edit is for it or for its schedule
    val requestSave = {
        if (editViewModel.reopenSeries.value) {
            Unit
        } else if (editViewModel.asksSeriesScope()) {
            val prompt = { showSeriesScope = true }
            context.findActivity()?.hideKeyboardThen(prompt) ?: prompt()
        } else {
            save(null)
        }
    }
    val requestSaveButton: () -> Unit = {
        if (viewState.isNew && viewState.task.title.isNullOrBlank()) {
            titleMissing = true
        } else {
            requestSave()
        }
    }
    val requestDelete = {
        val prompt = { showDeleteConfirmation = true }
        context.findActivity()?.hideKeyboardThen(prompt) ?: prompt()
    }
    val onBackPressed = {
        if (viewState.backButtonSavesTask) {
            requestSave()
        } else {
            requestDiscard()
        }
    }
    BackHandler {
        Timber.d("onBackPressed")
        onBackPressed()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    if (viewState.isReadOnly) {
                        IconButton(onClick = { onBackPressed() }) {
                            SymbolIcon(
                                name = TasksIcons.ARROW_BACK,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { requestSaveButton() },
                            modifier = Modifier.testTag(TestTags.SAVE_TASK),
                        ) {
                            SymbolIcon(
                                name = TasksIcons.SAVE,
                                contentDescription = stringResource(R.string.save)
                            )
                        }
                    }
                },
                title = {
                    if (viewState.pinSeries && editViewModel.pinningEnabled) {
                        Text(
                            text = org.jetbrains.compose.resources.stringResource(Res.string.pin_series_pinned),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                actions = {
                    if (!viewState.isNew) {
                        IconButton(onClick = { showTaskInfo = true }) {
                            SymbolIcon(
                                name = TasksIcons.INFO,
                                contentDescription = stringResource(R.string.task_info),
                            )
                        }
                    }
                    if (viewState.isReadOnly) {
                        return@TopAppBar
                    }
                    if (editViewModel.pinningEnabled) {
                        val pinned = viewState.task.pinned > 0
                        IconButton(
                            onClick = {
                                when {
                                    pinned -> editViewModel.setPinned(false)
                                    !viewState.isNew && editViewModel.pinAsksSeries -> showPinScope = true
                                    else -> editViewModel.setPinned(true)
                                }
                            },
                        ) {
                            SymbolIcon(
                                name = TasksIcons.PUSH_PIN,
                                contentDescription = org.jetbrains.compose.resources.stringResource(
                                    if (pinned) Res.string.unpin else Res.string.pin
                                ),
                                tint = if (pinned) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                filled = pinned,
                            )
                        }
                    }
                    // an uncompleted occurrence can't be deleted: complete, postpone or finish the series
                    if (!viewState.isNew && !(editViewModel.isLiveOccurrence && !viewState.isCompleted)) {
                        IconButton(onClick = { requestDelete() }) {
                            SymbolIcon(
                                name = TasksIcons.DELETE,
                                contentDescription = org.jetbrains.compose.resources.stringResource(
                                    Res.string.delete_task
                                ),
                            )
                        }
                    }
                    if (viewState.backButtonSavesTask) {
                        IconButton(onClick = { requestDiscard() }) {
                            SymbolIcon(
                                name = TasksIcons.CLEAR,
                                contentDescription = org.jetbrains.compose.resources.stringResource(
                                    Res.string.menu_discard_changes
                                ),
                            )
                        }
                    }
                    if (!viewState.isNew && editViewModel.memosEnabled) {
                        Box {
                            IconButton(
                                onClick = { convertMenuExpanded = true },
                                modifier = Modifier.testTag(TestTags.CONVERT_MENU),
                            ) {
                                SymbolIcon(
                                    name = TasksIcons.MORE_VERT,
                                    contentDescription = org.jetbrains.compose.resources.stringResource(
                                        Res.string.convert_to_memo
                                    ),
                                )
                            }
                            DropdownMenu(
                                expanded = convertMenuExpanded,
                                onDismissRequest = { convertMenuExpanded = false },
                            ) {
                                if (viewState.task.memo) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                org.jetbrains.compose.resources.stringResource(
                                                    Res.string.convert_to_task
                                                )
                                            )
                                        },
                                        onClick = {
                                            convertMenuExpanded = false
                                            showConvertToTask = true
                                        },
                                        modifier = Modifier.testTag(TestTags.CONVERT_TO_TASK),
                                    )
                                } else {
                                    val blocked = convertBlock != MemoConvertBlock.NONE
                                    val label = when (convertBlock) {
                                        MemoConvertBlock.CHILDREN ->
                                            org.jetbrains.compose.resources.stringResource(
                                                Res.string.convert_to_memo_blocked_children
                                            )
                                        MemoConvertBlock.SERIES ->
                                            org.jetbrains.compose.resources.stringResource(
                                                Res.string.convert_to_memo_blocked_series
                                            )
                                        MemoConvertBlock.NONE ->
                                            org.jetbrains.compose.resources.stringResource(
                                                Res.string.convert_to_memo
                                            )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            convertMenuExpanded = false
                                            if (!blocked) {
                                                showConvertToMemo = true
                                            }
                                        },
                                        enabled = !blocked,
                                        modifier = Modifier.testTag(TestTags.CONVERT_TO_MEMO),
                                    )
                                }
                            }
                        }
                    }
                    if (viewState.isNew && editViewModel.memosEnabled && draftListFilter != null) {
                        val switchToMemo = !viewState.task.memo
                        IconButton(
                            onClick = {
                                scope.launch {
                                    editViewModel.switchDraftType(switchToMemo, draftListFilter)
                                }
                            },
                            modifier = Modifier.testTag(
                                if (switchToMemo) TestTags.DRAFT_SWITCH_TO_MEMO else TestTags.DRAFT_SWITCH_TO_TASK
                            ),
                        ) {
                            SymbolIcon(
                                name = if (switchToMemo) TasksIcons.NOTE else TasksIcons.CHECKLIST,
                                contentDescription = org.jetbrains.compose.resources.stringResource(
                                    if (switchToMemo) Res.string.draft_switch_to_memo
                                    else Res.string.draft_switch_to_task
                                ),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (viewState.showComments && !viewState.isReadOnly) {
                AndroidFragment<CommentBarFragment>(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WindowInsets.navigationBars.asPaddingValues())
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .gesturesDisabled(viewState.isReadOnly)
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            viewState.displayOrder.forEach { tag ->
                when (tag) {
                    TAG_TITLE -> {
                        TitleRow(
                            text = viewState.task.title,
                            onChanged = { text: CharSequence? ->
                                editViewModel.setTitle(text.toString().trim { it <= ' ' })
                            },
                            linkify = linkify,
                            markdownProvider = markdownProvider,
                            isCompleted = viewState.isCompleted,
                            showComplete = !viewState.isNew && !viewState.task.memo,
                            priority = viewState.task.priority,
                            onComplete = {
                                if (viewState.isCompleted) {
                                    editViewModel.setComplete(false)
                                } else {
                                    editViewModel.setComplete(true)
                                    scope.launch {
                                        save(null)
                                    }
                                }
                            },
                            hint = if (viewState.task.memo) R.string.memo_title_hint else R.string.TEA_title_hint,
                            requestFocus = viewState.showKeyboard,
                            save = {
                                scope.launch {
                                    requestSaveButton()
                                }
                            },
                            missing = titleMissing && viewState.task.title.isNullOrBlank(),
                        )
                    }

                    TAG_DUE_DATE -> {
                        val due = editViewModel.dueDate.collectAsStateWithLifecycle().value
                        val openDueDate = {
                            val showPicker = { showDueDatePicker = true }
                            context.findActivity()?.hideKeyboardThen(showPicker) ?: showPicker()
                        }
                        if (editViewModel.placesEnabled()) {
                            WhenRow(
                                dueDate = due,
                                hasDueDateAlarm = remember(viewState.alarms) {
                                    viewState.alarms.any { it.type == Alarm.TYPE_REL_END }
                                },
                                is24HourFormat = is24Hour,
                                alwaysDisplayFullDate = viewState.alwaysDisplayFullDate,
                                location = viewState.location,
                                onSwitchToPlace = pickPlace,
                                onSwitchToTime = { editViewModel.setLocation(null) },
                                onEditDueDate = openDueDate,
                                onChangePlace = pickPlace,
                                onGeofence = onConfigureGeofence,
                            )
                        } else {
                            DueDateRow(
                                dueDate = due,
                                hasDueDateAlarm = remember(viewState.alarms) {
                                    viewState.alarms.any { it.type == Alarm.TYPE_REL_END }
                                },
                                is24HourFormat = is24Hour,
                                alwaysDisplayFullDate = viewState.alwaysDisplayFullDate,
                                onClick = openDueDate,
                            )
                        }
                    }
                    TAG_ADVANCE -> {
                        // early ring counts from the reminder time: no time, nothing to count from
                        if (editViewModel.dueDate.collectAsStateWithLifecycle().value > 0) {
                            AdvanceRow(
                                offset = remember(viewState.alarms) { viewState.alarms.advance()?.let { -it.time } },
                                onPick = { editViewModel.setAdvance(it) },
                            )
                        }
                    }
                    TAG_PRIORITY -> {
                        var enabledPriorities by remember { mutableStateOf(allPriorities) }
                        LaunchedEffect(Unit) {
                            enabledPriorities = org.tasks.notifications.prioritiesOf(preferences.enabledLevels())
                        }
                        // edits go to editViewModel.dueDate, not viewState.task
                        val due = editViewModel.dueDate.collectAsStateWithLifecycle().value
                        val placeTask = viewState.location != null && editViewModel.placesEnabled()
                        PriorityRow(
                            priority = viewState.task.priority,
                            onChangePriority = { editViewModel.setPriority(it) },
                            enabledPriorities = enabledPriorities,
                            caption = when {
                                viewState.task.memo -> PriorityCaption.None
                                placeTask || Task.hasDueTime(due) -> PriorityCaption.Alert
                                due > 0 -> PriorityCaption.Day
                                else -> PriorityCaption.Undated
                            },
                        )
                    }

                    TAG_DESCRIPTION -> {
                        // with the checklist on, its lines live in their own block below
                        val checklist = TAG_CHECKLIST in viewState.displayOrder
                        DescriptionRow(
                            text = if (checklist) Checklist.parse(viewState.task.notes).text else viewState.task.notes,
                            onChanged = { text ->
                                val trimmed = text.toString().trim { it <= ' ' }
                                if (checklist) editViewModel.setDescriptionText(trimmed) else editViewModel.setDescription(trimmed)
                            },
                            linkify = if (viewState.linkify) linkify else null,
                            markdownProvider = markdownProvider,
                        )
                    }

                    TAG_CHECKLIST ->
                        ChecklistRow(
                            notes = viewState.task.notes,
                            onChange = { editViewModel.setChecklist(it) },
                        )

                    TAG_LIST -> {
                        val listPickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult()
                        ) { it ->
                            it.data
                                ?.let { getParcelableExtra(it, EXTRA_FILTER, CaldavFilter::class.java) }
                                ?.let { editViewModel.setList(it) }
                        }
                        ListRow(
                            list = viewState.list,
                            colorProvider = colorProvider,
                            onClick = {
                                listPickerLauncher.launch(
                                    context = context,
                                    selectedFilter = viewState.list,
                                    listsOnly = true
                                )
                            }
                        )
                    }

                    TAG_CREATION ->
                        InfoRow(
                            creationDate = viewState.task.creationDate,
                            modificationDate = viewState.task.modificationDate,
                            completionDate = viewState.task.completionDate,
                            locale = locale,
                        )

                    StartDateControlSet.TAG -> AndroidFragment<StartDateControlSet>()
                    ReminderControlSet.TAG -> AndroidFragment<ReminderControlSet>()
                    LocationControlSet.TAG -> AndroidFragment<LocationControlSet>()
                    FilesControlSet.TAG -> AndroidFragment<FilesControlSet>()
                    TagsControlSet.TAG -> AndroidFragment<TagsControlSet>()
                    RepeatControlSet.TAG -> {
                        val dueDate = editViewModel.dueDate.collectAsStateWithLifecycle().value
                        LaunchedEffect(dueDate) {
                            editViewModel.onDueDateChanged()
                        }
                        RepeatRow(
                            recurrence = repeatRuleToString(viewState.task.recurrence),
                            repeatFrom = viewState.task.repeatFrom,
                            onClick = onClickRepeat,
                            onRepeatFromChanged = { editViewModel.setRepeatFrom(it) },
                        )
                    }
                    else -> throw IllegalArgumentException("Unknown row: $tag")
                }
                HorizontalDivider()
            }
            if (viewState.showComments) {
                CommentsRow(
                    comments = comments,
                    copyCommentToClipboard = { copyToClipboard(context, R.string.comment, it) },
                    deleteComment = deleteComment,
                    openImage = { FileHelper.startActionView(context, it) },
                )
            }
            val beastMode = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                (context.findActivity() as? MainActivity)?.restartActivity()
            }
            BeastModeBanner(
                visible = viewState.showBeastModeHint,
                showSettings = {
                    editViewModel.hideBeastModeHint(click = true)
                    beastMode.launch(Intent(context, BeastModePreferences::class.java))
                },
                dismiss = dismissBeastMode,
            )
        }
        if (showDueDatePicker) {
            val dueDate = editViewModel.dueDate.collectAsStateWithLifecycle().value
            val (initialDay, initialTime) = dueDateToSelection(dueDate)
            DueDatePickerSheet(
                initialDay = initialDay,
                initialTime = initialTime,
                is24Hour = is24Hour,
                showNoDate = !viewState.task.isRecurring,
                times = remember { preferences.quickPickTimes },
                onSelected = { day, time ->
                    editViewModel.setDueDate(dueDateFromSelection(day, time))
                    showDueDatePicker = false
                },
                onDismiss = { showDueDatePicker = false },
            )
        }
        if (showDiscardConfirmation) {
            DiscardChangesDialog(
                onDiscard = {
                    showDiscardConfirmation = false
                    discard()
                },
                onDismiss = { showDiscardConfirmation = false },
            )
        }
        if (editViewModel.reopenSeries.collectAsStateWithLifecycle().value) {
            AlertDialog(
                onDismissRequest = { editViewModel.answerReopen(false) },
                title = { Text(stringResource(R.string.reopen_completed_title)) },
                text = {
                    Text(
                        stringResource(
                            if (editViewModel.reopenRestartsSeries) {
                                R.string.reopen_series_body
                            } else {
                                R.string.reopen_occurrence_body
                            }
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = { editViewModel.answerReopen(true) }) {
                        Text(stringResource(R.string.reopen_completed_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editViewModel.answerReopen(false) }) {
                        Text(stringResource(R.string.reopen_completed_keep))
                    }
                },
            )
        }
        if (showSeriesScope) {
            SeriesEditScopeDialog(
                onSelected = {
                    showSeriesScope = false
                    save(it)
                },
                onDismiss = { showSeriesScope = false },
            )
        }
        if (showTaskInfo) {
            TaskInfoDialog(
                task = viewState.task,
                seriesCreated = editViewModel.seriesCreated,
                onDismiss = { showTaskInfo = false },
            )
        }
        if (showPinScope) {
            AlertDialog(
                onDismissRequest = { showPinScope = false },
                title = { Text(org.jetbrains.compose.resources.stringResource(Res.string.pin)) },
                confirmButton = {
                    TextButton(onClick = {
                        showPinScope = false
                        editViewModel.setPinned(true, wholeSeries = true)
                    }) {
                        Text(org.jetbrains.compose.resources.stringResource(Res.string.pin_whole_series))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPinScope = false
                        editViewModel.setPinned(true)
                    }) {
                        Text(org.jetbrains.compose.resources.stringResource(Res.string.pin_this_only))
                    }
                },
            )
        }
        if (showDeleteConfirmation) {
            DeleteTaskDialog(
                onDelete = {
                    showDeleteConfirmation = false
                    delete()
                },
                onDismiss = { showDeleteConfirmation = false },
            )
        }
        if (showConvertToMemo) {
            ConvertToMemoDialog(
                hasGoogleList = convertHasGoogle,
                onConfirm = {
                    showConvertToMemo = false
                    scope.launch {
                        when (editViewModel.convertToMemo()) {
                            MemoConvertBlock.CHILDREN -> Toast.makeText(
                                context,
                                getString(Res.string.convert_to_memo_blocked_children),
                                Toast.LENGTH_SHORT,
                            ).show()
                            MemoConvertBlock.SERIES -> Toast.makeText(
                                context,
                                getString(Res.string.convert_to_memo_blocked_series),
                                Toast.LENGTH_SHORT,
                            ).show()
                            MemoConvertBlock.NONE -> Unit
                        }
                    }
                },
                onDismiss = { showConvertToMemo = false },
            )
        }
        if (showConvertToTask) {
            ConvertToTaskDialog(
                onConfirm = {
                    showConvertToTask = false
                    scope.launch { editViewModel.convertToTask() }
                },
                onDismiss = { showConvertToTask = false },
            )
        }
    }
}

/** Spec point 10: an edit of an occurrence goes to it alone or to its schedule. */
@Composable
fun SeriesEditScopeDialog(
    onSelected: (SeriesEditScope) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.series_edit_scope_title)) },
        confirmButton = {
            TextButton(onClick = { onSelected(SeriesEditScope.SCHEDULE) }) {
                Text(stringResource(R.string.series_edit_schedule))
            }
        },
        dismissButton = {
            TextButton(onClick = { onSelected(SeriesEditScope.OCCURRENCE) }) {
                Text(stringResource(R.string.series_edit_occurrence))
            }
        },
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun TaskEditScreenPreview() {
    TasksTheme {
//        TaskEditScreen(
//
//        )
    }
}
