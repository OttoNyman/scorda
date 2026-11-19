package org.tasks.activities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.caldav.metadata.TagMetadataSync
import org.tasks.data.dao.TagDataDao
import org.tasks.data.entity.TagData
import org.tasks.resources.getString
import org.tasks.sync.SyncAdapters
import org.tasks.sync.SyncSource
import org.tasks.themes.TasksIcons
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.name_cannot_be_empty
import tasks.kmp.generated.resources.tag_already_exists
import javax.inject.Inject

@HiltViewModel
class TagListViewModel @Inject constructor(
    private val tagDataDao: TagDataDao,
    private val tagMetadataSync: TagMetadataSync,
    private val syncAdapters: SyncAdapters,
    private val refreshBroadcaster: RefreshBroadcaster,
) : ViewModel() {

    var tags by mutableStateOf<List<TagData>>(emptyList())
        private set

    fun refresh() {
        viewModelScope.launch { tags = tagDataDao.tagDataOrderedByName() }
    }

    fun create(name: String, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                onError(getString(Res.string.name_cannot_be_empty))
                return@launch
            }
            if (tagDataDao.getTagByName(trimmed) != null) {
                onError(getString(Res.string.tag_already_exists))
                return@launch
            }
            val created = withContext(NonCancellable) {
                val row = tagDataDao.createDirty(TagData(name = trimmed, icon = TasksIcons.LABEL))
                if (row != null) {
                    syncAdapters.sync(SyncSource.METADATA_CHANGE)
                    refreshBroadcaster.broadcastRefresh()
                }
                row
            }
            if (created == null) {
                onError(getString(Res.string.tag_already_exists))
                return@launch
            }
            tags = tagDataDao.tagDataOrderedByName()
            onDone()
        }
    }

    fun rename(tag: TagData, name: String, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            val trimmed = name.trim()
            val remoteId = tag.remoteId
            if (trimmed.isEmpty()) {
                onError(getString(Res.string.name_cannot_be_empty))
                return@launch
            }
            if (remoteId == null) return@launch
            if (!trimmed.equals(tag.name, ignoreCase = true) && tagDataDao.getTagByName(trimmed) != null) {
                onError(getString(Res.string.tag_already_exists))
                return@launch
            }
            val saved = withContext(NonCancellable) {
                val renamed = TagData.normalize(trimmed) != tag.normalizedName || trimmed != tag.name
                if (renamed) {
                    val row = tagMetadataSync.renameTag(
                        remoteId = remoteId,
                        name = trimmed,
                        color = tag.color ?: 0,
                        icon = tag.icon,
                        colorChanged = false,
                        iconChanged = false,
                        order = tag.order,
                    )
                    if (row == null) return@withContext false
                }
                syncAdapters.sync(SyncSource.METADATA_CHANGE)
                refreshBroadcaster.broadcastRefresh()
                true
            }
            if (!saved) {
                onError(getString(Res.string.tag_already_exists))
                return@launch
            }
            tags = tagDataDao.tagDataOrderedByName()
            onDone()
        }
    }

    fun setColor(tag: TagData, color: Int) {
        val remoteId = tag.remoteId ?: return
        val name = tag.name ?: return
        viewModelScope.launch {
            withContext(NonCancellable) {
                tagDataDao.editTag(
                    remoteId = remoteId,
                    name = name,
                    color = color,
                    icon = tag.icon,
                    nameChanged = false,
                    colorChanged = true,
                    iconChanged = false,
                    order = tag.order,
                )
                syncAdapters.sync(SyncSource.METADATA_CHANGE)
                refreshBroadcaster.broadcastRefresh()
            }
            tags = tagDataDao.tagDataOrderedByName()
        }
    }

    fun delete(tag: TagData) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                tagMetadataSync.deleteTag(tag)
                syncAdapters.sync(SyncSource.METADATA_CHANGE)
                refreshBroadcaster.broadcastRefresh()
            }
            tags = tagDataDao.tagDataOrderedByName()
        }
    }
}
