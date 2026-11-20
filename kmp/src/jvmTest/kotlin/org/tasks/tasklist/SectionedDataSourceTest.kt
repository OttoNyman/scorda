package org.tasks.tasklist

import com.todoroo.astrid.core.SortHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tasks.data.TaskContainer
import org.tasks.data.entity.Task
import org.tasks.tasklist.SectionedDataSource.Companion.HEADER_COMPLETED
import org.tasks.tasklist.SectionedDataSource.Companion.HEADER_PINNED
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.time.startOfDay

class SectionedDataSourceTest {
    private val today = currentTimeMillis().startOfDay()
    private val tomorrow = today + 86_400_000L
    private val dayAfter = today + 2 * 86_400_000L

    private fun task(title: String, completed: Boolean = false, group: Long = today, pinned: Long = 0) =
        TaskContainer(
            task = Task(id = title.hashCode().toLong(), title = title, pinned = pinned),
            parentComplete = completed,
            sortGroup = group,
        )

    private fun dataSource(
        vararg tasks: TaskContainer,
        collapsed: Set<Long> = emptySet(),
        groupMode: Int = SortHelper.SORT_DUE,
        pinning: Boolean = true,
    ) = SectionedDataSource(
        tasks = tasks.toList(),
        disableHeaders = false,
        groupMode = groupMode,
        collapsed = collapsed,
        completedAtBottom = true,
        pinning = pinning,
    )

    @Test
    fun noPinnedSectionWhenPinningIsOff() {
        val source = dataSource(task(ACTIVE), task(PINNED, pinned = 1), pinning = false)

        assertEquals(listOf("group", ACTIVE, PINNED), source.render())
    }

    private fun SectionedDataSource.render(): List<String> = (0 until size).map {
        if (isHeader(it)) {
            when (getHeaderValue(it)) {
                HEADER_COMPLETED -> "completed"
                HEADER_PINNED -> "pinned"
                else -> "group"
            }
        } else {
            getItem(it).title!!
        }
    }

    @Test
    fun collapsingCompletedTakesAwayTheWholeBlock() {
        val source = dataSource(
            task(ACTIVE),
            task(DONE, completed = true),
            task(DONE_SUB, completed = true, group = tomorrow),
            collapsed = setOf(HEADER_COMPLETED),
        )

        assertEquals(
            listOf("group", ACTIVE, "completed"),
            source.render(),
        )
    }

    @Test
    fun pinnedTasksGetTheirOwnSectionFirst() {
        val source = dataSource(task(PINNED, group = tomorrow, pinned = 1), task(ACTIVE))

        assertEquals(listOf("pinned", PINNED, "group", ACTIVE), source.render())
    }

    @Test
    fun pinnedSectionPrecedesPriorityGroups() {
        val source = dataSource(
            task(PINNED, group = 0, pinned = 1),
            task(ACTIVE, group = 0),
            groupMode = SortHelper.SORT_IMPORTANCE,
        )

        assertEquals(listOf("pinned", PINNED, "group", ACTIVE), source.render())
    }

    @Test
    fun noHeadersWithoutGrouping() {
        val source = dataSource(
            task(PINNED, pinned = 1),
            task(ACTIVE),
            groupMode = SortHelper.GROUP_NONE,
        )

        assertEquals(listOf(PINNED, ACTIVE), source.render())
    }

    @Test
    fun theCompletedSectionCoversItsHeaderAndItsRows() {
        val source = dataSource(task(ACTIVE), task(DONE, completed = true))

        assertEquals(listOf("group", ACTIVE, "completed", DONE), source.render())
    }

    @Test
    fun thereIsNoCompletedSectionWhenCompletedSortInPlace() {
        val source = SectionedDataSource(
            tasks = listOf(task(ACTIVE), task(DONE, completed = true)),
            disableHeaders = false,
            groupMode = SortHelper.SORT_DUE,
            completedAtBottom = false,
        )

        assertEquals(listOf("group", ACTIVE, DONE), source.render())
    }

    @Test
    fun sectionValuesStayInPositionOrderAfterMovingASection() {
        val source = dataSource(
            task(ACTIVE, group = today),
            task(DONE, group = tomorrow),
            task(DONE_SUB, group = dayAfter),
        )
        assertEquals(listOf(today, tomorrow, dayAfter), source.getSectionValues())

        source.moveSection(2, -1)

        assertEquals(listOf(today, tomorrow, dayAfter), source.getSectionValues())
    }

    @Test
    fun rowsStayAddressableAfterMovingASection() {
        val source = dataSource(
            task(ACTIVE, group = today),
            task(DONE, group = tomorrow),
            task(DONE_SUB, group = dayAfter),
        )

        source.moveSection(2, -1)

        assertEquals(
            listOf(ACTIVE, DONE, DONE_SUB),
            (0 until source.size).filterNot { source.isHeader(it) }.map { source.getItem(it).title },
        )
    }

    companion object {
        private const val ACTIVE = "active"
        private const val PINNED = "pinned-task"
        private const val DONE = "done"
        private const val DONE_SUB = "done-sub"
    }
}
