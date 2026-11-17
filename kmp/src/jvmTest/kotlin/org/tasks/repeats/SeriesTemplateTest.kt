package org.tasks.repeats

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.tasks.DatabaseTest
import org.tasks.data.TaskSaver
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_GOOGLE_TASKS
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_LOCAL
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Geofence
import org.tasks.data.entity.Series
import org.tasks.data.entity.TagData
import org.tasks.data.entity.Task
import org.tasks.preferences.AppPreferences

class SeriesTemplateTest : DatabaseTest() {
    private val taskDao = db.taskDao()
    private val caldavDao = db.caldavDao()
    private val alarmDao = db.alarmDao()
    private val tagDao = db.tagDao()
    private val tagDataDao = db.tagDataDao()
    private val locationDao = db.locationDao()

    private val preferences: AppPreferences = mock {
        onBlocking { addTasksToTop() } doReturn true
    }

    private val taskSaver = TaskSaver(
        taskDao = taskDao,
        refreshBroadcaster = mock(),
        notifier = mock(),
        locationService = mock(),
        backgroundWork = mock(),
        caldavDao = caldavDao,
    )

    private val templates = SeriesTemplates(
        taskDao = taskDao,
        taskSaver = taskSaver,
        alarmDao = alarmDao,
        tagDao = tagDao,
        tagDataDao = tagDataDao,
        locationDao = locationDao,
        caldavDao = caldavDao,
        googleTaskDao = db.googleTaskDao(),
        preferences = preferences,
    )

    private val series = Series(
        uuid = "series",
        recurrence = "RRULE:FREQ=DAILY",
        anchor = DUE,
        template = "{}",
        created = 1,
        modified = 1,
    )

    @Before
    fun setUp() = runBlocking {
        caldavDao.insert(CaldavAccount(uuid = "local", accountType = TYPE_LOCAL))
        caldavDao.insert(CaldavCalendar(uuid = LOCAL_LIST, account = "local"))
        caldavDao.insert(CaldavAccount(uuid = "google", accountType = TYPE_GOOGLE_TASKS))
        caldavDao.insert(CaldavCalendar(uuid = GOOGLE_LIST, account = "google"))
    }

    private suspend fun newTask(
        title: String,
        list: String,
        parent: Long = 0,
        due: Long = DUE,
        hideUntil: Long = 0,
        completed: Long = 0,
    ): Task {
        val task = Task(
            title = title,
            parent = parent,
            dueDate = due,
            hideUntil = hideUntil,
            completionDate = completed,
        )
        taskDao.createNew(task)
        caldavDao.insert(
            task = task,
            caldavTask = CaldavTask(task = task.id, calendar = list, remoteId = null),
            addToTop = false,
        )
        return task
    }

    @Test
    fun captureThenEmitCopiesEverything() = runBlocking {
        val task = newTask("water", LOCAL_LIST, hideUntil = DUE - HOUR)
        task.notes = "plants"
        task.priority = Task.Priority.HIGH
        taskDao.update(task)
        alarmDao.insert(listOf(
            Alarm(task = task.id, type = Alarm.TYPE_REL_END, time = 0),
            Alarm(task = task.id, type = Alarm.TYPE_REL_START, time = 0),
            Alarm(task = task.id, type = Alarm.TYPE_DATE_TIME, time = DUE - 2 * HOUR),
            Alarm(task = task.id, type = Alarm.TYPE_SNOOZE, time = 5),
        ))
        val tag = TagData(name = "home").also { tagDataDao.insert(it) }
        tagDao.insert(task, listOf(tagDataDao.getByUuid(tag.remoteId!!)!!))
        locationDao.insert(Geofence(task = task.id, place = "place-1", isArrival = true))
        newTask("open", LOCAL_LIST, parent = task.id, due = 0)
        newTask("done", LOCAL_LIST, parent = task.id, due = DUE + HOUR, completed = 1)

        val template = templates.capture(task.id)
        val emitted = templates.emit(series, NEXT, template)

        val row = taskDao.fetch(emitted.id)!!
        assertEquals("water", row.title)
        assertEquals("plants", row.notes)
        assertEquals(Task.Priority.HIGH, row.priority)
        assertEquals("series", row.series)
        assertEquals(NEXT, row.scheduled)
        assertEquals(NEXT, row.dueDate)
        assertEquals(NEXT - HOUR, row.hideUntil)
        assertNull(row.recurrence)
        assertEquals(LOCAL_LIST, caldavDao.getTask(emitted.id)!!.calendar)
        assertEquals(
            setOf(Alarm.TYPE_REL_END, Alarm.TYPE_REL_START, Alarm.TYPE_DATE_TIME),
            alarmDao.getAlarms(emitted.id).map { it.type }.toSet(),
        )
        assertEquals(3, alarmDao.getAlarms(emitted.id).size)
        assertEquals(NEXT - 2 * HOUR, alarmDao.getAlarms(emitted.id).single { it.type == Alarm.TYPE_DATE_TIME }.time)
        assertEquals(listOf(tag.remoteId), tagDataDao.getTagDataForTask(emitted.id).map { it.remoteId })
        val geofence = locationDao.getGeofencesForTask(emitted.id).single()
        assertEquals("place-1", geofence.place)
        assertTrue(geofence.isArrival)
        assertFalse(geofence.isDeparture)

        val subtasks = taskDao.fetch(taskDao.getChildren(emitted.id))
        assertEquals(setOf("open", "done"), subtasks.map { it.title }.toSet())
        subtasks.forEach {
            assertEquals(0L, it.completionDate)
            assertNull(it.series)
            assertEquals(0L, it.scheduled)
            assertEquals(emitted.id, it.parent)
            assertEquals(LOCAL_LIST, caldavDao.getTask(it.id)!!.calendar)
        }
        assertEquals(0L, subtasks.single { it.title == "open" }.dueDate)
        assertEquals(NEXT + HOUR, subtasks.single { it.title == "done" }.dueDate)
    }

    @Test
    fun subtaskOfUndatedParentHasNoDueOffset() = runBlocking {
        val parent = newTask("p", LOCAL_LIST, due = 0)
        val sub = newTask("s", LOCAL_LIST, parent = parent.id, due = DUE)
        alarmDao.insert(Alarm(task = sub.id, type = Alarm.TYPE_DATE_TIME, time = DUE - HOUR))

        val template = templates.capture(parent.id)
        val emitted = templates.emit(series, NEXT, template)

        assertNull(template.subtasks.single().dueOffset)
        val emittedSub = taskDao.fetch(taskDao.getChildren(emitted.id)).single()
        assertEquals(0L, emittedSub.dueDate)
        assertEquals(DUE - HOUR, alarmDao.getAlarms(emittedSub.id).single().time)
    }

    @Test
    fun emitIntoGoogleListHasNoRemoteId() = runBlocking {
        val task = newTask("g", GOOGLE_LIST)

        val emitted = templates.emit(series, NEXT, templates.capture(task.id))

        val caldavTask = caldavDao.getTask(emitted.id)
        assertNotNull(caldavTask)
        assertEquals(GOOGLE_LIST, caldavTask!!.calendar)
        assertNull(caldavTask.remoteId)
    }

    @Test
    fun jsonRoundTrip() {
        val template = SeriesTemplate(
            title = "t",
            notes = "n",
            priority = 2,
            startOffset = -HOUR,
            alarms = listOf(AlarmTemplate(type = 1, time = 2, repeat = 3, interval = 4)),
            tags = listOf("tag"),
            place = "place",
            arrival = true,
            departure = false,
            list = LOCAL_LIST,
            subtasks = listOf(SeriesTemplate(title = "sub", dueOffset = HOUR)),
        )

        assertEquals(template, templates.decode(templates.encode(template)))
        assertEquals("x", templates.decode("""{"title":"x","unknown":1}""").title)
    }

    companion object {
        private const val LOCAL_LIST = "local-list"
        private const val GOOGLE_LIST = "google-list"
        private const val HOUR = 60L * 60 * 1000
        private const val DUE = 1_700_000_000_000L
        private const val NEXT = DUE + 24 * HOUR
    }
}
