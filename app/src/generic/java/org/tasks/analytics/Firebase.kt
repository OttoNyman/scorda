package org.tasks.analytics

import android.content.Context
import org.tasks.fcm.FcmTokenProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tasks.R
import org.tasks.preferences.Preferences
import org.tasks.preferences.TasksPreferences
import org.tasks.viewmodel.TasksAccountViewModel.Companion.DEFAULT_TOS_VERSION
import timber.log.Timber
import javax.inject.Inject

@Suppress("UNUSED_PARAMETER")
class Firebase @Inject constructor(
    @param:ApplicationContext val context: Context,
    private val preferences: Preferences,
    override val tasksPreferences: TasksPreferences,
) : Reporting, FcmTokenProvider {
    override fun reportException(t: Throwable, fatal: Boolean) = Timber.e(t)

    fun reportIabResult(result: String, sku: String = "", state: String, orderId: String = "") {}

    fun updateRemoteConfig() {}

    override fun logEvent(event: String, vararg params: Pair<String, Any>) {
        Timber.d("$event -> ${params.toMap()}")
    }

    fun logEvent(event: Int, vararg params: Pair<Int, Any>) {
        logEvent(
            event = context.getString(event),
            params = params.map { context.getString(it.first) to it.second }.toTypedArray()
        )
    }

    fun logEventOncePerDay(event: Int, vararg params: Pair<Int, Any>) {
        logEvent(event, *params)
    }

    override fun addTask(source: String) =
        logEvent(R.string.event_add_task, R.string.param_type to source)

    override fun completeTask(source: String) =
        logEvent(R.string.event_complete_task, R.string.param_type to source)

    override fun setSku(sku: String?) {
        Timber.d("sku -> $sku")
    }

    fun getTosVersion(): Int = DEFAULT_TOS_VERSION

    fun registerPrefChangeListener() {}

    fun unregisterPrefChangeListener() {}

    override suspend fun getToken(): String? = null
}
