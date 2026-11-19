package org.tasks.preferences

interface QueryPreferences {
    var sortMode: Int

    var groupMode: Int

    var completedMode: Int

    var subtaskMode: Int

    var isManualSort: Boolean

    var isAstridSort: Boolean

    var sortAscending: Boolean

    var groupAscending: Boolean

    var completedAscending: Boolean

    var subtaskAscending: Boolean

    var showHidden: Boolean

    var showCompleted: Boolean

    var showCompletedSubtasks: Boolean

    val alwaysDisplayFullDate: Boolean

    var completedTasksAtBottom: Boolean

    /** Undated tasks at the end of the list (true) or the start (false). */
    var undatedAtBottom: Boolean

    /** Dateless-time tasks after timed ones that day (true) or before them (false). */
    var noTimeAtEndOfDay: Boolean
}