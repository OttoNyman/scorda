package org.tasks.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tasks.BuildConfig
import org.tasks.R
import org.tasks.billing.Inventory
import org.tasks.data.TaskContainer
import org.tasks.data.entity.Task
import org.tasks.data.isHidden
import org.tasks.extensions.Context.is24HourFormat
import org.tasks.extensions.setColorFilter
import org.tasks.themes.chipColors
import org.tasks.filters.Filter
import org.tasks.filters.PlaceFilter
import org.tasks.filters.TagFilter
import org.tasks.filters.getIcon
import org.tasks.kmp.org.tasks.time.DateFormatter
import org.tasks.kmp.formatTime
import org.tasks.time.startOfDay
import org.tasks.compose.chips.ChipDataProvider
import javax.inject.Inject

class WidgetChipProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chipListCache: ChipDataProvider,
    private val inventory: Inventory,
) {
    var isDark = false

    fun getChecklistChip(done: Int, total: Int): RemoteViews =
        newChip().apply {
            setTextViewText(R.id.chip_text, "$done/$total")
            setImageViewResource(R.id.chip_icon, R.drawable.ic_outline_check_box_24px)
        }

    fun getStartDateChip(
        task: TaskContainer,
        showFullDate: Boolean,
        sortByStartDate: Boolean,
        dateFormatter: DateFormatter,
    ): RemoteViews? {
        return if (task.task.isHidden) {
            val time = if (sortByStartDate && task.sortGroup?.startOfDay() == task.task.hideUntil.startOfDay()) {
                task.task.hideUntil
                    .takeIf { Task.hasDueTime(it) }
                    ?.let { dateFormatter.time(it) }
                    ?: return null
            } else {
                dateFormatter.relativeDateTime(
                    task.task.hideUntil,
                    alwaysDisplayFullDate = showFullDate
                )
            }
            newChip().apply {
                setTextViewText(R.id.chip_text, time)
                setImageViewResource(R.id.chip_icon, R.drawable.ic_pending_actions_24px)
            }
        } else {
            null
        }
    }

    fun getListChip(filter: Filter, task: TaskContainer): RemoteViews? = null

    fun getPlaceChip(filter: Filter, task: TaskContainer): RemoteViews? {
        task.location
                ?.takeIf { filter !is PlaceFilter || it.place != filter.place}
                ?.let { return newChip(PlaceFilter(it.place), R.drawable.ic_outline_place_24px) }
        return null
    }

    fun getTagChips(filter: Filter, task: TaskContainer): List<RemoteViews> {
        val tags = task.tagsString?.split(",")?.toHashSet() ?: return emptyList()
        if (filter is TagFilter) {
            tags.remove(filter.uuid)
        }
        return tags
                .mapNotNull(chipListCache::getTag)
                .sortedBy(TagFilter::title)
                .map { newChip(it, R.drawable.ic_outline_label_24px) }
    }

    private fun newChip(filter: Filter, defaultIcon: Int) =
        newChip(filter.tint).apply {
            setTextViewText(R.id.chip_text, filter.title)
            filter
                .getIcon(inventory)
                ?.let { iconName ->
                    try {
                        val iconUri = WidgetIconProvider.getIconUri(
                            iconName = iconName,
                        )
                        setImageViewUri(R.id.chip_icon, iconUri)
                    } catch (_: Exception) {
                        setImageViewResource(R.id.chip_icon, defaultIcon)
                    }
                }
                ?: setImageViewResource(R.id.chip_icon, defaultIcon)
        }

    private fun newChip(@ColorInt color: Int = 0) = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_chip).apply {
        val seedColor = if (color == 0) {
            context.getColor(org.tasks.kmp.R.color.grey_300)
        } else {
            color
        }
        val colors = chipColors(seedColor, isDark)
        setColorFilter(R.id.chip_icon, colors.contentColor)
        setColorFilter(R.id.chip_background, colors.backgroundColor)
        setTextColor(R.id.chip_text, colors.contentColor)
    }
}
