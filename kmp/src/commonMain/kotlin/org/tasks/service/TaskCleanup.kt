package org.tasks.service

interface TaskCleanup {
    /** The tasks are going away for good. */
    suspend fun cleanup(tasks: List<Long>) {}

    /** The tasks moved to the trash and can still come back. */
    suspend fun trashed(tasks: List<Long>) = cleanup(tasks)
}
