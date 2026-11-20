package com.todoroo.astrid.adapter

import com.todoroo.astrid.core.SortHelper
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.tasks.data.TaskContainer
import org.tasks.data.TaskSaver
import org.tasks.data.entity.Task
import org.tasks.tasklist.SectionedDataSource

class TaskAdapterSortGroupTest {
    private val taskSaver: TaskSaver = mock()

    private val row = TaskContainer(task = Task(id = 1, dueDate = 1_800_000_000_000L, priority = Task.Priority.LOW))

    private fun adapter(mode: Int, header: Long) = TaskAdapter(
        newTasksOnTop = false,
        googleTaskDao = mock(),
        caldavDao = mock(),
        taskDao = mock(),
        taskSaver = taskSaver,
        dirtyDao = mock(),
        refreshBroadcaster = mock(),
        taskMover = mock(),
    ).apply {
        setDataSource(object : TaskAdapterDataSource {
            override fun getItem(position: Int) = row

            override fun getTaskCount() = 1

            override fun nearestHeader(position: Int) = header

            override val sortMode = mode
        })
    }

    @Test
    fun droppingUnderThePinnedHeaderKeepsTheDueDate() = runTest {
        adapter(SortHelper.SORT_DUE, SectionedDataSource.HEADER_PINNED).moved(0, 2, 0)

        verifyBlocking(taskSaver, never()) { save(any(), anyOrNull(), any(), any()) }
    }

    @Test
    fun droppingUnderThePinnedHeaderKeepsThePriority() = runTest {
        adapter(SortHelper.SORT_IMPORTANCE, SectionedDataSource.HEADER_PINNED).moved(0, 2, 0)

        verifyBlocking(taskSaver, never()) { save(any(), anyOrNull(), any(), any()) }
    }
}
