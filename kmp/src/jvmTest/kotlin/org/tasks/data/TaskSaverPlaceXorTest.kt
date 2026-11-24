package org.tasks.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.tasks.DatabaseTest
import org.tasks.constructor.Constructor
import org.tasks.constructor.FeatureGate
import org.tasks.data.entity.Geofence
import org.tasks.data.entity.Place
import org.tasks.data.entity.Task
import org.tasks.time.DateTimeUtils2.currentTimeMillis

class TaskSaverPlaceXorTest : DatabaseTest() {
    private val taskDao = db.taskDao()
    private val locationDao = db.locationDao()
    private val featureGate = FeatureGate(Constructor(geo = true))
    private val taskSaver = TaskSaver(
        taskDao = taskDao,
        refreshBroadcaster = mock(),
        notifier = mock(),
        locationService = mock(),
        backgroundWork = mock(),
        caldavDao = db.caldavDao(),
        locationDao = locationDao,
        featureGate = featureGate,
    )

    @Test
    fun newTaskWithPlaceAndDueKeepsDueAndDropsPlace() = runBlocking {
        val place = Place(uid = "p1", name = "Shop")
        locationDao.insert(place)
        val due = currentTimeMillis()
        val task = Task(title = "buy", dueDate = due)
        taskDao.createNew(task)
        locationDao.insert(Geofence(task = task.id, place = place.uid, isArrival = true))

        taskSaver.save(task, null)

        assertEquals(due, taskDao.fetch(task.id)!!.dueDate)
        assertNull(locationDao.getGeofences(task.id))
    }

    @Test
    fun changingDueOnExistingPlaceTaskDropsPlace() = runBlocking {
        val place = Place(uid = "p2", name = "Home")
        locationDao.insert(place)
        val original = Task(title = "home", dueDate = 0)
        taskDao.createNew(original)
        locationDao.insert(Geofence(task = original.id, place = place.uid, isArrival = true))
        val edited = original.copy(dueDate = currentTimeMillis())

        taskSaver.save(edited, original)

        assertNull(locationDao.getGeofences(edited.id))
        assertEquals(edited.dueDate, taskDao.fetch(edited.id)!!.dueDate)
    }

    @Test
    fun unrelatedSaveOnOldDualLeavesBoth() = runBlocking {
        val place = Place(uid = "p3", name = "Office")
        locationDao.insert(place)
        val due = currentTimeMillis()
        val original = Task(title = "dual", dueDate = due)
        taskDao.createNew(original)
        locationDao.insert(Geofence(task = original.id, place = place.uid, isArrival = true))
        val edited = original.copy(title = "dual renamed")

        taskSaver.save(edited, original)

        val saved = taskDao.fetch(edited.id)!!
        assertEquals(due, saved.dueDate)
        assertEquals("dual renamed", saved.title)
        assertNotNull(locationDao.getGeofences(edited.id))
    }

    @Test
    fun placeOnlyNewTaskKeepsPlace() = runBlocking {
        val place = Place(uid = "p4", name = "Gym")
        locationDao.insert(place)
        val task = Task(title = "train", dueDate = 0)
        taskDao.createNew(task)
        locationDao.insert(Geofence(task = task.id, place = place.uid, isArrival = true))

        taskSaver.save(task, null)

        assertTrue(taskDao.fetch(task.id)!!.dueDate == 0L)
        assertNotNull(locationDao.getGeofences(task.id))
    }

    @Test
    fun dateSetWithPlacesOffStillDropsHiddenPlace() = runBlocking {
        val place = Place(uid = "p7", name = "Bank")
        locationDao.insert(place)
        val original = Task(title = "pay", dueDate = 0)
        taskDao.createNew(original)
        locationDao.insert(Geofence(task = original.id, place = place.uid, isArrival = true))
        featureGate.current = Constructor(geo = false)
        val edited = original.copy(dueDate = currentTimeMillis())

        taskSaver.save(edited, original)

        assertNull(locationDao.getGeofences(edited.id))
        assertEquals(edited.dueDate, taskDao.fetch(edited.id)!!.dueDate)
    }

    @Test
    fun staleHideUntilIgnoredWhenStartDateOff() = runBlocking {
        val place = Place(uid = "p5", name = "Cafe")
        locationDao.insert(place)
        val original = Task(title = "coffee", dueDate = 0, hideUntil = currentTimeMillis())
        taskDao.createNew(original)
        locationDao.insert(Geofence(task = original.id, place = place.uid, isArrival = true))
        featureGate.current = Constructor(geo = true, startDate = false)

        taskSaver.save(original.copy(title = "latte"), original)

        assertNotNull(locationDao.getGeofences(original.id))
    }

    @Test
    fun hideUntilChangeDropsPlaceWhenStartDateOn() = runBlocking {
        val place = Place(uid = "p6", name = "Park")
        locationDao.insert(place)
        val original = Task(title = "walk", dueDate = 0, hideUntil = 0)
        taskDao.createNew(original)
        locationDao.insert(Geofence(task = original.id, place = place.uid, isArrival = true))
        featureGate.current = Constructor(geo = true, startDate = true)
        val edited = original.copy(hideUntil = currentTimeMillis())

        taskSaver.save(edited, original)

        assertNull(locationDao.getGeofences(edited.id))
    }
}
