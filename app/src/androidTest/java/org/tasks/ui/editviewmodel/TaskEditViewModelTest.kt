package org.tasks.ui.editviewmodel

import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.data.entity.Task
import org.tasks.makers.TaskMaker.newTask
import org.tasks.ui.TaskEditViewModel.MemoConvertBlock

@HiltAndroidTest
class TaskEditViewModelTest : BaseTaskEditViewModelTest() {
    @Test
    fun noChangesForNewTask() {
        setup(newTask())

        assertFalse(viewModel.hasChanges())
    }

    @Test
    fun dontSaveTaskWithoutChanges() = runBlocking {
        setup(newTask())

        assertFalse(save())

        assertTrue(taskDao.getAll().isEmpty())
    }

    @Test
    fun dontSaveTaskTwice() = runBlocking {
        setup(newTask())

        viewModel.setPriority(Task.Priority.HIGH)

        assertTrue(save())

        assertFalse(viewModel.save())
    }

    @Test
    fun convertToMemoPersistsUnsavedFields() = runBlocking(Dispatchers.Main) {
        val task = newTask()
        task.title = "stored"
        taskDao.createNew(task)
        setup(task)
        viewModel.setTitle("edited title")
        viewModel.setDescription("edited notes")
        viewModel.setPriority(Task.Priority.HIGH)

        assertEquals(MemoConvertBlock.NONE, viewModel.convertToMemo())

        val saved = taskDao.fetch(task.id)!!
        assertTrue(saved.memo)
        assertEquals("edited title", saved.title)
        assertEquals("edited notes", saved.notes)
        assertEquals(Task.Priority.HIGH, saved.priority)
    }

    @Test
    fun convertToTaskPersistsUnsavedFields() = runBlocking(Dispatchers.Main) {
        val task = Task(title = "memo", memo = true, notes = "old")
        taskDao.createNew(task)
        setup(task)
        viewModel.setTitle("edited title")
        viewModel.setDescription("edited notes")

        viewModel.convertToTask()

        val saved = taskDao.fetch(task.id)!!
        assertFalse(saved.memo)
        assertEquals("edited title", saved.title)
        assertEquals("edited notes", saved.notes)
    }
}