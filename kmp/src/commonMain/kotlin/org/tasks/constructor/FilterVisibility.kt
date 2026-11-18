package org.tasks.constructor

import org.tasks.filters.CaldavFilter
import org.tasks.filters.CustomFilter
import org.tasks.filters.Filter
import org.tasks.filters.MemosFilter
import org.tasks.filters.NotificationsFilter
import org.tasks.filters.PlaceFilter
import org.tasks.filters.RecentlyModifiedFilter
import org.tasks.filters.SnoozedFilter
import org.tasks.filters.TagFilter
import org.tasks.filters.TodayFilter

fun Filter.fits(features: Constructor): Boolean = when (this) {
    is CaldavFilter -> features.lists
    is TagFilter -> features.tags
    is PlaceFilter -> features.geo
    is CustomFilter -> features.filters
    is MemosFilter -> features.memos
    is TodayFilter, is RecentlyModifiedFilter, is SnoozedFilter, is NotificationsFilter -> features.filters
    else -> true
}
