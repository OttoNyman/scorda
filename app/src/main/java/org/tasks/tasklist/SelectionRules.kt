package org.tasks.tasklist

import org.tasks.data.TaskContainer

/** «Завершить серию» makes sense only when an open, writable occurrence is selected. */
fun selectionHasOpenSeries(rows: List<TaskContainer>, selected: Set<Long>): Boolean =
    rows.any { it.id in selected && it.task.series != null && !it.isCompleted && !it.task.readOnly }
