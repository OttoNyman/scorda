package org.tasks.activities

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.todoroo.astrid.activity.TaskListFragment
import dagger.hilt.android.AndroidEntryPoint
import org.jetbrains.compose.resources.stringResource
import org.tasks.compose.components.SymbolIcon
import org.tasks.compose.settings.SettingsContentPadding
import org.tasks.compose.settings.SettingsItemCard
import org.tasks.data.entity.Place
import org.tasks.location.LocationPickerActivity
import org.tasks.themes.TasksIcons
import org.tasks.themes.TasksTheme
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.back
import tasks.kmp.generated.resources.cancel
import tasks.kmp.generated.resources.delete
import tasks.kmp.generated.resources.drawer_places
import tasks.kmp.generated.resources.new_place

@AndroidEntryPoint
class PlaceListActivity : AppCompatActivity() {

    private val viewModel: PlaceListViewModel by viewModels()

    private var editingUid: String? = null

    private val edit = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.refresh()
        val uid = editingUid
        editingUid = null
        if (result.resultCode == RESULT_OK &&
            result.data?.action == TaskListFragment.ACTION_DELETED &&
            uid != null &&
            uid == openFilterId
        ) {
            setResult(RESULT_OK, Intent(TaskListFragment.ACTION_DELETED))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TasksTheme {
                PlaceListScreen(
                    places = viewModel.places,
                    onBack = { finish() },
                    onAdd = {
                        edit.launch(Intent(this, LocationPickerActivity::class.java))
                    },
                    onEdit = { place ->
                        editingUid = place.uid
                        edit.launch(
                            Intent(this, PlaceSettingsActivity::class.java)
                                .putExtra(PlaceSettingsActivity.EXTRA_PLACE, place as Parcelable)
                        )
                    },
                    onDelete = { place ->
                        viewModel.delete(place)
                        if (place.uid != null && place.uid == openFilterId) {
                            setResult(RESULT_OK, Intent(TaskListFragment.ACTION_DELETED))
                        }
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
private fun PlaceListScreen(
    places: List<Place>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Place) -> Unit,
    onDelete: (Place) -> Unit,
) {
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val deleting = places.firstOrNull { it.id == deletingId }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .union(WindowInsets.systemBars)
            .union(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.drawer_places)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SymbolIcon(
                            name = TasksIcons.ARROW_BACK,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onAdd) {
                        SymbolIcon(
                            name = TasksIcons.ADD,
                            contentDescription = stringResource(Res.string.new_place),
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
            items(places, key = { it.uid ?: it.id }) { place ->
                SettingsItemCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(place) }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = place.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        IconButton(onClick = { deletingId = place.id }) {
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
    deleting?.let { place ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(Res.string.delete)) },
            text = { Text(place.displayName) },
            confirmButton = {
                TextButton(onClick = {
                    deletingId = null
                    onDelete(place)
                }) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}
