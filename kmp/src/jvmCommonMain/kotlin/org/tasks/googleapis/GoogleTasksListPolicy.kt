package org.tasks.googleapis

import org.tasks.data.dao.CaldavDao

internal const val SCORDA_LIST_NAME = "Scorda"

internal suspend fun moveLocalTasksToList(
    caldavDao: CaldavDao,
    fromListId: String?,
    toListId: String,
) {
    if (fromListId.isNullOrBlank() || fromListId == toListId) return
    caldavDao.reassignCalendar(fromListId, toListId)
    caldavDao.markCalendarDirty(toListId)
}

internal data class RemoteTaskList(val id: String, val title: String?)

internal sealed class ScordaListChoice {
    data class Ready(val id: String) : ScordaListChoice()
    data object Create : ScordaListChoice()
    data class Ambiguous(val ids: List<String>) : ScordaListChoice()
    data object Missing : ScordaListChoice()
}

internal fun chooseScordaList(remote: List<RemoteTaskList>, savedId: String?): ScordaListChoice {
    if (!savedId.isNullOrBlank()) {
        return if (remote.any { it.id == savedId }) {
            ScordaListChoice.Ready(savedId)
        } else {
            ScordaListChoice.Missing
        }
    }
    val named = remote.filter { it.title == SCORDA_LIST_NAME }
    return when (named.size) {
        0 -> ScordaListChoice.Create
        1 -> ScordaListChoice.Ready(named.single().id)
        else -> ScordaListChoice.Ambiguous(named.map { it.id })
    }
}

internal fun shouldDeleteLocalList(
    localRemoteId: String?,
    keptRemoteIds: Set<String>,
    protectedId: String?,
): Boolean = !localRemoteId.isNullOrBlank() &&
        localRemoteId != protectedId &&
        localRemoteId !in keptRemoteIds
