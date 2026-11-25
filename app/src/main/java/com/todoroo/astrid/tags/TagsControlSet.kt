package com.todoroo.astrid.tags

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.compose.edit.TagsRow
import org.tasks.data.dao.TagDataDao
import org.tasks.data.entity.TagData
import org.tasks.sync.SyncAdapters
import org.tasks.sync.SyncSource
import org.tasks.ui.ChipProvider
import org.tasks.ui.TaskEditControlFragment
import javax.inject.Inject

@AndroidEntryPoint
class TagsControlSet : TaskEditControlFragment() {
    @Inject lateinit var chipProvider: ChipProvider
    @Inject lateinit var tagDataDao: TagDataDao
    @Inject lateinit var syncAdapters: SyncAdapters

    @Composable
    override fun Content() {
        val viewState = viewModel.viewState.collectAsStateWithLifecycle().value
        var menu by remember { mutableStateOf(false) }
        var allTags by remember { mutableStateOf<List<TagData>>(emptyList()) }
        var showNew by remember { mutableStateOf(false) }
        var newName by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        LaunchedEffect(menu) {
            if (menu) {
                allTags = tagDataDao.tagDataOrderedByName()
            }
        }
        Box {
            TagsRow(
                tags = viewState.tags,
                colorProvider = { chipProvider.getColor(it) },
                onClick = { menu = true },
                onClear = { viewModel.setTags(viewState.tags.minus(it)) },
            )
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                allTags.forEach { tag ->
                    val selected = viewState.tags.any { it.remoteId == tag.remoteId }
                    DropdownMenuItem(
                        text = { Text(tag.name.orEmpty()) },
                        leadingIcon = {
                            Checkbox(checked = selected, onCheckedChange = null)
                        },
                        onClick = {
                            val next = if (selected) {
                                viewState.tags.filterNot { it.remoteId == tag.remoteId }.toSet()
                            } else {
                                viewState.tags + tag
                            }
                            viewModel.setTags(next)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.new_tag)) },
                    onClick = {
                        menu = false
                        newName = ""
                        showNew = true
                    },
                )
            }
        }
        if (showNew) {
            AlertDialog(
                onDismissRequest = { showNew = false },
                title = { Text(stringResource(R.string.new_tag)) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val name = newName.trim()
                            if (name.isEmpty()) return@TextButton
                            showNew = false
                            scope.launch {
                                val tag = tagDataDao.createDirty(TagData(name = name))?.also {
                                    syncAdapters.sync(SyncSource.METADATA_CHANGE)
                                } ?: tagDataDao.getOrCreateTag(name)
                                viewModel.setTags(viewModel.viewState.value.tags + tag)
                            }
                        },
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNew = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }

    companion object {
        val TAG = R.string.TEA_ctrl_lists_pref
    }
}
