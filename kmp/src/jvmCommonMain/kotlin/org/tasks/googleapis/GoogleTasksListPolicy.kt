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

internal data class LocalList(val uuid: String, val name: String?)

/**
 * Where each local list goes when everything moves to Google: the default one and one named
 * Scorda into Scorda, one sharing a name with a known Google list into it, the rest into a new
 * list (null target). A chosen [target] that still exists takes every list.
 */
internal fun planLocalMove(
    local: List<LocalList>,
    localDefault: String?,
    scordaId: String,
    known: List<LocalList>,
    target: String? = null,
): List<Pair<LocalList, String?>> = local.map { list ->
    list to when {
        target != null && known.any { it.uuid == target } -> target
        list.uuid == localDefault || list.name == SCORDA_LIST_NAME -> scordaId
        else -> known.firstOrNull { it.name == list.name }?.uuid
    }
}
