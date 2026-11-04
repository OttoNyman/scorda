package org.tasks.googleapis

import com.google.api.services.tasks.model.TaskList
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.data.dao.CaldavDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavCalendar
import org.tasks.service.TaskDeleter

class GtasksListService(
    private val caldavDao: CaldavDao,
    private val taskDeleter: TaskDeleter,
    private val refreshBroadcaster: RefreshBroadcaster,
) {
    suspend fun updateLists(
        account: CaldavAccount,
        remoteLists: List<TaskList>,
        protectedRemoteId: String? = null,
    ) {
        val lists = caldavDao.getCalendarsByAccount(account.uuid!!)
        val previousLists: MutableSet<Long> = HashSet()
        for (list in lists) {
            previousLists.add(list.id)
        }
        for (i in remoteLists.indices) {
            val remote = remoteLists[i]
            val id = remote.id
            var local: CaldavCalendar? = null
            for (list in lists) {
                if (list.uuid == id) {
                    local = list
                    break
                }
            }
            if (local == null) {
                local = CaldavCalendar(
                    account = account.uuid,
                    uuid = id,
                )
            }
            local.name = remote.title
            caldavDao.insertOrReplace(local)
            previousLists.remove(local.id)
        }

        val kept = remoteLists.mapNotNull { it.id }.toSet()
        for (listId in previousLists) {
            val calendar = caldavDao.getCalendarById(listId) ?: continue
            if (shouldDeleteLocalList(calendar.uuid, kept, protectedRemoteId)) {
                taskDeleter.delete(calendar)
            }
        }
        refreshBroadcaster.broadcastRefresh()
    }
}
