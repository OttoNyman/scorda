package org.tasks.activities

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.todoroo.astrid.activity.TaskListFragment
import dagger.hilt.android.AndroidEntryPoint
import org.jetbrains.compose.resources.stringResource
import org.tasks.billing.Inventory
import org.tasks.billing.PurchaseActivity
import org.tasks.billing.PurchaseActivityViewModel
import org.tasks.compose.ColorWheelDialog
import org.tasks.compose.components.SymbolIcon
import org.tasks.compose.settings.ColorPickerDialog
import org.tasks.compose.settings.SettingsContentPadding
import org.tasks.compose.settings.SettingsItemCard
import org.tasks.compose.settings.buildPickerColors
import org.tasks.data.entity.TagData
import org.tasks.themes.TasksIcons
import org.tasks.themes.TasksTheme
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.back
import tasks.kmp.generated.resources.cancel
import tasks.kmp.generated.resources.delete
import tasks.kmp.generated.resources.delete_tag_confirmation
import tasks.kmp.generated.resources.delete_tag_warning
import tasks.kmp.generated.resources.display_name
import tasks.kmp.generated.resources.new_tag
import tasks.kmp.generated.resources.ok
import tasks.kmp.generated.resources.tag_settings
import javax.inject.Inject

@AndroidEntryPoint
class TagListActivity : AppCompatActivity() {

    @Inject lateinit var inventory: Inventory

    private val viewModel: TagListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TasksTheme {
                TagListScreen(
                    tags = viewModel.tags,
                    hasPro = inventory.purchasedThemes(),
                    onBack = { finish() },
                    onCreate = viewModel::create,
                    onRename = viewModel::rename,
                    onColor = viewModel::setColor,
                    onDelete = { tag ->
                        viewModel.delete(tag)
                        if (tag.remoteId != null && tag.remoteId == openFilterId) {
                            setResult(RESULT_OK, Intent(TaskListFragment.ACTION_DELETED))
                        }
                    },
                    onSubscribe = {
                        startActivity(
                            Intent(this, PurchaseActivity::class.java)
                                .putExtra(PurchaseActivityViewModel.EXTRA_SOURCE, "list_colors")
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private val openFilterId: String?
        get() = intent.getStringExtra(EXTRA_OPEN_FILTER_ID)

    companion object {
        const val EXTRA_OPEN_FILTER_ID = "open_filter_id"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagListScreen(
    tags: List<TagData>,
    hasPro: Boolean,
    onBack: () -> Unit,
    onCreate: (String, (String) -> Unit, () -> Unit) -> Unit,
    onRename: (TagData, String, (String) -> Unit, () -> Unit) -> Unit,
    onColor: (TagData, Int) -> Unit,
    onDelete: (TagData) -> Unit,
    onSubscribe: () -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var coloringId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showColorWheel by rememberSaveable { mutableStateOf(false) }
    val openingWheel = androidx.compose.runtime.remember { mutableStateOf(false) }
    val colors = buildPickerColors(isSystemInDarkTheme())
    val coloring = tags.firstOrNull { it.id == coloringId }
    val deleting = tags.firstOrNull { it.id == deletingId }
    val namingTag = tags.firstOrNull { it.id == naming }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .union(WindowInsets.systemBars)
            .union(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.tag_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SymbolIcon(
                            name = TasksIcons.ARROW_BACK,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        SymbolIcon(
                            name = TasksIcons.ADD,
                            contentDescription = stringResource(Res.string.new_tag),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(modifier = Modifier.height(SettingsContentPadding)) }
            items(tags, key = { it.id ?: it.remoteId ?: it.name ?: "" }) { tag ->
                SettingsItemCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val shown = colors.firstOrNull { it.originalColor == tag.color }?.primaryColor
                            ?: tag.color
                            ?: 0
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (shown == 0) MaterialTheme.colorScheme.surfaceContainerHigh
                                    else Color(shown)
                                )
                                .clickable { coloringId = tag.id },
                        )
                        Text(
                            text = tag.name.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { naming = tag.id }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        IconButton(onClick = { deletingId = tag.id }) {
                            SymbolIcon(
                                name = TasksIcons.DELETE,
                                contentDescription = stringResource(Res.string.delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating || namingTag != null) {
        NameDialog(
            title = stringResource(if (creating) Res.string.new_tag else Res.string.display_name),
            initial = namingTag?.name.orEmpty(),
            onDismiss = {
                creating = false
                naming = null
            },
            onSave = { value, onError, onDone ->
                if (creating) onCreate(value, onError, onDone)
                else namingTag?.let { onRename(it, value, onError, onDone) }
            },
        )
    }

    coloring?.let { tag ->
        if (showColorWheel) {
            ColorWheelDialog(
                initialColor = tag.color ?: 0,
                onColorSelected = {
                    onColor(tag, it)
                    showColorWheel = false
                    coloringId = null
                },
                onCancel = { showColorWheel = false },
                onDismiss = { showColorWheel = false },
            )
        } else {
            ColorPickerDialog(
                hasPro = hasPro,
                colors = colors,
                onDismiss = {
                    if (openingWheel.value) openingWheel.value = false else coloringId = null
                },
                onColorSelected = { onColor(tag, it.originalColor) },
                onSubscribe = onSubscribe,
                onColorWheelSelected = {
                    openingWheel.value = true
                    showColorWheel = true
                },
                showColorWheel = hasPro,
            )
        }
    }

    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(Res.string.delete_tag_confirmation, tag.name.orEmpty())) },
            text = { Text(stringResource(Res.string.delete_tag_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    deletingId = null
                    onDelete(tag)
                }) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String, (String) -> Unit, () -> Unit) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val save = {
        onSave(value, { error = it }) {
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = save) { Text(stringResource(Res.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
