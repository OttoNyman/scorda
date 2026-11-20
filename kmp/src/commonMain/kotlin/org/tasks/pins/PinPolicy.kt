package org.tasks.pins

import org.tasks.constructor.Constructor
import org.tasks.data.entity.Task

/**
 * Which of the [pinned] tasks get a pinned card in the shade. A task whose reminder is
 * showing ([reminderIds]) keeps the reminder card: the pinned card comes back once it is gone.
 */
fun pinCards(pinned: List<Task>, reminderIds: Set<Long>, features: Constructor): List<Task> =
    if (!features.pinning || !features.pinShade) {
        emptyList()
    } else {
        pinned.filter { (features.memos || !it.memo) && it.id !in reminderIds }
    }
