package org.tasks.compose.drawer


interface DrawerConfiguration {
    val filtersEnabled: Boolean
        get() = true

    val placesEnabled: Boolean
        get() = true

    val hideUnusedPlaces: Boolean
        get() = false

    val tagsEnabled: Boolean
        get() = true

    val hideUnusedTags: Boolean
        get() = false

    val todayFilter: Boolean
        get() = true

    val recentlyModifiedFilter: Boolean
        get() = true

    val listsEnabled: Boolean
        get() = true

    val startDateEnabled: Boolean
        get() = true

    /** Local lists block, hidden while syncing unless the local-lists toggle is on. */
    val localListsEnabled: Boolean
        get() = true

    /** Google Tasks is syncing, not paused: its block offers a new list. */
    val googleTasksSyncing: Boolean
        get() = true

    val timerEnabled: Boolean
        get() = true

    val canCreateFilters: Boolean
        get() = true

    val canCreateTags: Boolean
        get() = true

    val canCreatePlaces: Boolean
        get() = true
}
