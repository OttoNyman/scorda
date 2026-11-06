package org.tasks.filters

import org.tasks.R
import org.tasks.compose.drawer.DrawerConfiguration
import org.tasks.preferences.Preferences

class PreferenceDrawerConfiguration(
    private val preferences: Preferences,
    private val gate: org.tasks.constructor.FeatureGate,
) : DrawerConfiguration {
    private val features get() = gate.current

    override val filtersEnabled: Boolean
        get() = features.filters && preferences.getBoolean(R.string.p_filters_enabled, super.filtersEnabled)

    override val placesEnabled: Boolean
        get() = features.geo && preferences.getBoolean(R.string.p_places_enabled, super.placesEnabled)

    override val hideUnusedPlaces: Boolean
        get() = preferences.getBoolean(R.string.p_places_hide_unused, super.hideUnusedPlaces)

    override val tagsEnabled: Boolean
        get() = features.tags && preferences.getBoolean(R.string.p_tags_enabled, super.tagsEnabled)

    override val hideUnusedTags: Boolean
        get() = preferences.getBoolean(R.string.p_tags_hide_unused, super.hideUnusedTags)

    override val todayFilter: Boolean
        get() = features.filters && preferences.getBoolean(R.string.p_show_today_filter, super.todayFilter)

    override val recentlyModifiedFilter: Boolean
        get() = features.filters && preferences.getBoolean(R.string.p_show_recently_modified_filter, super.recentlyModifiedFilter)

    override val listsEnabled: Boolean
        get() = features.lists

    override val startDateEnabled: Boolean
        get() = features.startDate

    override val localListsEnabled: Boolean
        get() = features.localLists

    override val googleTasksSyncing: Boolean
        get() = gate.sync

    override val timerEnabled: Boolean
        get() = features.timer
}