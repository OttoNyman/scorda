@file:Suppress("ClassName")

package com.todoroo.astrid.service

import org.tasks.data.dao.UpgraderDao
import org.tasks.service.Upgrade

class Upgrade_15_14(
    private val upgraderDao: UpgraderDao,
) : Upgrade {
    override suspend fun run() {
        upgraderDao.unsetPriorityToOrdinary()
    }

    companion object {
        const val VERSION = PRIORITY_ALERT_VERSION
    }
}

const val PRIORITY_ALERT_VERSION = 151308
