package org.tasks.memos

import com.todoroo.astrid.alarms.AlarmCalculator
import com.todoroo.astrid.alarms.AlarmService
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
import org.tasks.constructor.Constructor
import org.tasks.data.TaskMover
import org.tasks.data.TaskSaver
import org.tasks.data.UUIDHelper
import org.tasks.data.createDueDate
import org.tasks.data.dao.CaldavDao
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_GOOGLE_TASKS
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_LOCAL
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Geofence
import org.tasks.data.entity.Place
import org.tasks.data.entity.Task
import org.tasks.filters.CaldavFilter
import org.tasks.location.LocationService
import org.tasks.preferences.AppPreferences
import org.tasks.reminders.Random
import org.tasks.service.TaskDeleter
import org.tasks.service.TaskCleanup

class MemoConvertTest : DatabaseTest() {
    private val taskDao = db.taskDao()
    private val caldavDao: CaldavDao = db.caldavDao()
    private val alarmDao = db.alarmDao()
    private val locationDao = db.locationDao()

    private lateinit var googleCalendarUuid: String
    private lateinit var localCalendarUuid: String
    private lateinit var defaultList: CaldavFilter

    private val appPreferences: AppPreferences = mock {
        onBlocking { isDefaultDueTimeEnabled() } doReturn true
        onBlocking { defaultAlarms() } doReturn listOf(Alarm.whenDue(0))
        onBlocking { defaultRingMode() } doReturn 0
        onBlocking { addTasksToTop() } doReturn false
    }

    private val locationService: LocationService = mock {
        on { locationDao } doReturn db.locationDao()
        on { appPreferences } doReturn appPreferences
    }

    private val taskDeleter: TaskDeleter by lazy {
        TaskDeleter(
            deletionDao = db.deletionDao(),
            taskDao = taskDao,
            caldavDao = caldavDao,
            refreshBroadcaster = mock(),
            vtodoCache = mock(),
            tasksPreferences = mock(),
            taskCleanup = object : TaskCleanup {
                override suspend fun cleanup(tasks: List<Long>) = Unit
            },
        )
    }

    private val taskSaver: TaskSaver by lazy {
        TaskSaver(
        taskDao = taskDao,
        refreshBroadcaster = mock(),
        notifier = mock(),
        locationService = locationService,
        backgroundWork = mock(),
        caldavDao = caldavDao,
        locationDao = db.locationDao(),
    )
    }

    private val taskMover: TaskMover by lazy {
        TaskMover(
            taskDao = taskDao,
            caldavDao = caldavDao,
            googleTaskDao = db.googleTaskDao(),
            dirtyDao = db.dirtyDao(),
            appPreferences = appPreferences,
            refreshBroadcaster = mock(),
            taskDeleter = taskDeleter,
        )
    }

    private val alarmService: AlarmService by lazy {
        AlarmService(
            alarmDao = alarmDao,
            taskDao = taskDao,
            dirtyDao = db.dirtyDao(),
            refreshBroadcaster = mock(),
            notifier = mock(),
            alarmCalculator = AlarmCalculator(Random()),
            preferences = appPreferences,
        )
    }

    private lateinit var converter: MemoConverter

    @Before
    fun seedLists() = runBlocking {
        val googleAccount = CaldavAccount(uuid = UUIDHelper.newUUID(), accountType = TYPE_GOOGLE_TASKS)
        caldavDao.insert(googleAccount)
        val googleCal = CaldavCalendar(uuid = UUIDHelper.newUUID(), account = googleAccount.uuid, name = "Google")
        caldavDao.insert(googleCal)
        googleCalendarUuid = googleCal.uuid!!

        val localAccount = CaldavAccount(uuid = UUIDHelper.newUUID(), accountType = TYPE_LOCAL)
        caldavDao.insert(localAccount)
        val localCal = CaldavCalendar(uuid = UUIDHelper.newUUID(), account = localAccount.uuid, name = "Local")
        caldavDao.insert(localCal)
        localCalendarUuid = localCal.uuid!!

        defaultList = CaldavFilter(
            calendar = localCal,
            account = localAccount,
        )

        converter = MemoConverter(
            taskDao = taskDao,
            caldavDao = caldavDao,
            alarmDao = alarmDao,
            locationDao = locationDao,
            taskDeleter = taskDeleter,
            taskMover = taskMover,
            taskSaver = taskSaver,
            alarmService = alarmService,
            appPreferences = appPreferences,
            locationService = locationService,
            features = { Constructor() },
            defaultList = { defaultList },
        )
    }

    @Test
    fun taskToMemoStripsFieldsAndMarksGoogleDeleted() = runBlocking {
        val task = Task(
            title = "synced",
            dueDate = createDueDate(Task.URGENCY_TODAY, 0),
            recurrence = "FREQ=DAILY",
            parent = 5,
            order = 42L,
        )
        taskDao.createNew(task)
        caldavDao.insert(
            task = task,
            caldavTask = CaldavTask(task = task.id, calendar = googleCalendarUuid),
            addToTop = false,
        )
        alarmDao.insert(listOf(Alarm(task = task.id, type = Alarm.TYPE_REL_END)))

        assertTrue(converter.canConvertToMemo(task))

        converter.toMemo(task.id)

        val saved = taskDao.fetch(task.id)!!
        assertTrue(saved.memo)
        assertEquals(0, saved.dueDate)
        assertEquals(0, saved.hideUntil)
        assertNull(saved.recurrence)
        assertNull(saved.series)
        assertEquals(0, saved.parent)
        assertNull(saved.order)
        assertEquals(0, saved.completionDate)
        assertTrue(alarmDao.getAlarms(task.id).isEmpty())
        val cd = caldavDao.getTasks(task.id).single()
        assertTrue(cd.deleted > 0)
    }

    @Test
    fun taskToMemoRemovesLocalListRow() = runBlocking {
        val task = Task(title = "local", dueDate = createDueDate(Task.URGENCY_TODAY, 0))
        taskDao.createNew(task)
        caldavDao.insert(
            task = task,
            caldavTask = CaldavTask(task = task.id, calendar = localCalendarUuid),
            addToTop = false,
        )

        converter.toMemo(task.id)

        assertNull(caldavDao.getTask(task.id))
        assertTrue(taskDao.fetch(task.id)!!.memo)
    }

    @Test
    fun completedTaskToMemoClearsGeofences() = runBlocking {
        val task = Task(
            title = "done",
            completionDate = 1_700_000_000_000L,
        )
        taskDao.createNew(task)
        val place = Place(uid = "place-1", name = "Home")
        locationDao.insert(place)
        locationDao.insert(Geofence(task = task.id, place = place.uid, isArrival = true))
        assertTrue(locationDao.getGeofencesForTask(task.id).isNotEmpty())

        assertEquals(ToMemoResult.SUCCESS, converter.toMemo(task.id))

        assertTrue(taskDao.fetch(task.id)!!.memo)
        assertTrue(locationDao.getGeofencesForTask(task.id).isEmpty())
    }

    @Test
    fun toMemoDetachesTrashedChildren() = runBlocking {
        val parent = Task(title = "parent")
        taskDao.createNew(parent)
        val trashed = Task(title = "trashed", parent = parent.id, deletionDate = 5)
        taskDao.createNew(trashed)

        assertEquals(ToMemoResult.SUCCESS, converter.toMemo(parent.id))

        assertEquals(0L, taskDao.fetch(trashed.id)!!.parent)
        assertTrue(taskDao.fetch(parent.id)!!.memo)
    }

    @Test
    fun toMemoBlockedWhenChildrenOrSeries() = runBlocking {
        val seriesTask = Task(title = "occurrence", series = "series-uuid")
        taskDao.createNew(seriesTask)
        assertEquals(ToMemoResult.BLOCKED_SERIES, converter.toMemo(seriesTask.id))
        assertFalse(taskDao.fetch(seriesTask.id)!!.memo)

        val parent = Task(title = "parent")
        taskDao.createNew(parent)
        taskDao.createNew(Task(title = "child", parent = parent.id))
        assertEquals(ToMemoResult.BLOCKED_CHILDREN, converter.toMemo(parent.id))
        assertFalse(taskDao.fetch(parent.id)!!.memo)
    }

    @Test
    fun cannotConvertWhenSeriesOrChildren() = runBlocking {
        val seriesTask = Task(title = "occurrence", series = "series-uuid")
        taskDao.createNew(seriesTask)
        assertFalse(converter.canConvertToMemo(seriesTask))

        val parent = Task(title = "parent")
        taskDao.createNew(parent)
        taskDao.createNew(Task(title = "child", parent = parent.id))
        val parentRow = taskDao.fetch(parent.id)!!
        assertFalse(converter.canConvertToMemo(parentRow))
    }

    @Test
    fun memoToTaskGetsDueTodayListAndAlarm() = runBlocking {
        val memo = Task(title = "scratch", memo = true)
        taskDao.createNew(memo)

        converter.toTask(memo.id)

        val task = taskDao.fetch(memo.id)!!
        assertFalse(task.memo)
        assertTrue(task.dueDate > 0)
        assertNull(task.order)
        assertNotNull(caldavDao.getTask(task.id))
        assertEquals(localCalendarUuid, caldavDao.getTask(task.id)!!.calendar)
        assertTrue(alarmDao.getAlarms(task.id).isNotEmpty())
    }
}