package org.tasks.ui.editviewmodel

import androidx.lifecycle.SavedStateHandle
import com.todoroo.astrid.activity.TaskEditFragment
import com.todoroo.astrid.alarms.AlarmService
import com.todoroo.astrid.service.TaskCreator
import org.tasks.data.dao.TaskDao
import org.tasks.data.TaskSaver
import org.tasks.service.TaskCompleter
import org.tasks.service.TaskDeleter
import org.tasks.data.TaskMover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.tasks.data.dao.AlarmDao
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.TagDataDao
import org.tasks.data.dao.UserActivityDao
import org.tasks.data.db.Database
import org.tasks.R
import org.tasks.data.entity.Task
import org.tasks.data.newLocalAccount
import org.tasks.injection.InjectingTestCase
import org.tasks.location.LocationService
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.preferences.Preferences
import org.tasks.memos.MemoConverter
import org.tasks.ui.TaskEditViewModel
import javax.inject.Inject

open class BaseTaskEditViewModelTest : InjectingTestCase() {
    @Inject lateinit var db: Database
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var taskSaver: TaskSaver
    @Inject lateinit var taskDeleter: TaskDeleter
    @Inject lateinit var taskMover: TaskMover
    @Inject lateinit var featureGate: org.tasks.constructor.FeatureGate
    @Inject lateinit var seriesScheduler: org.tasks.repeats.SeriesScheduler
    @Inject lateinit var locationService: LocationService
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var taskCompleter: TaskCompleter
    @Inject lateinit var alarmService: AlarmService
    @Inject lateinit var defaultFilterProvider: DefaultFilterProvider
    @Inject lateinit var locationDao: LocationDao
    @Inject lateinit var tagDataDao: TagDataDao
    @Inject lateinit var alarmDao: AlarmDao
    @Inject lateinit var userActivityDao: UserActivityDao
    @Inject lateinit var caldavDao: CaldavDao
    @Inject lateinit var taskCreator: TaskCreator
    @Inject lateinit var memoConverter: MemoConverter

    protected lateinit var viewModel: TaskEditViewModel

    @Before
    override fun setUp() {
        runBlocking {
            super.setUp()
            caldavDao.newLocalAccount()
        }
        preferences.setBoolean(R.string.p_rmd_time_enabled, true)
    }

    protected fun setup(task: Task) = runBlocking {
        viewModel = TaskEditViewModel(
            context,
            SavedStateHandle().apply {
                set(TaskEditFragment.EXTRA_TASK, task)
            },
            taskDao,
            taskSaver,
            taskDeleter,
            taskMover,
            db.locationDao(),
            locationService,
            db.tagDao(),
            db.tagDataDao(),
            preferences,
            db.googleTaskDao(),
            db.caldavDao(),
            taskCompleter,
            alarmService,
            MutableSharedFlow(),
            userActivityDao = userActivityDao,
            taskAttachmentDao = db.taskAttachmentDao(),
            alarmDao = db.alarmDao(),
            defaultFilterProvider = defaultFilterProvider,
            featureGate = featureGate,
            seriesDao = db.seriesDao(),
            seriesScheduler = seriesScheduler,
            taskCreator = taskCreator,
            memoConverter = memoConverter,
        )
    }

    protected fun save(): Boolean = runBlocking(Dispatchers.Main) {
        viewModel.save()
    }
}