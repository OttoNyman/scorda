package org.tasks.jobs

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.drive.model.File
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.tasks.LocalBroadcastManager
import org.tasks.R
import org.tasks.Strings.isNullOrEmpty
import org.tasks.analytics.Firebase
import org.tasks.backup.BackupConstants
import org.tasks.googleapis.InvokerFactory
import org.tasks.injection.BaseWorker
import org.tasks.preferences.Preferences
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

@HiltWorker
class DriveUploader @AssistedInject constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        firebase: Firebase,
        invokers: InvokerFactory,
        private val preferences: Preferences,
        private val localBroadcastManager: LocalBroadcastManager
) : BaseWorker(context, workerParams, firebase) {
    private val drive = invokers.getDriveInvoker()

    override suspend fun run(): Result {
        val inputData = inputData
        val uri = Uri.parse(inputData.getString(EXTRA_URI))
        return try {
            val folder = getFolder() ?: return Result.failure()
            preferences.setString(R.string.p_google_drive_backup_folder, folder.id)
            drive.createFile(folder.id, uri)
                    ?.let(BackupConstants::getTimestamp)
                    ?.let { preferences.setLong(R.string.p_backups_drive_last, it) }
            localBroadcastManager.broadcastPreferenceRefresh()
            if (inputData.getBoolean(EXTRA_PURGE, false)) {
                val files = drive.getFilesByPrefix(folder.id, "auto.")
                val drop = BackupConstants.autoFilesToDelete(
                    files.mapNotNull { it.name },
                    BackupWork.DAYS_TO_KEEP_BACKUP,
                ).toSet()
                files.filter { it.name in drop }.forEach { drive.delete(it) }
            }
            Result.success()
        } catch (e: FileNotFoundException) {
            fail(e)
        } catch (e: SocketException) {
            retry(e)
        } catch (e: SocketTimeoutException) {
            retry(e)
        } catch (e: SSLException) {
            retry(e)
        } catch (e: ConnectException) {
            retry(e)
        } catch (e: UnknownHostException) {
            retry(e)
        } catch (e: GoogleJsonResponseException) {
            when (e.statusCode) {
                401 -> {
                    notifyExpired(R.string.google_access_expired)
                    fail(e)
                }
                403 -> {
                    notifyExpired(R.string.google_access_denied)
                    fail(e)
                }
                503 -> retry(e)
                else -> retry(e, report = true)
            }
        } catch (e: IOException) {
            fail(e, report = true)
        }
    }

    private fun fail(e: Throwable, report: Boolean = false): Result {
        if (report) {
            firebase.reportException(e)
        } else {
            Timber.e(e)
        }
        return Result.failure()
    }

    private fun retry(e: Throwable, report: Boolean = false): Result {
        if (report) {
            firebase.reportException(e)
        } else {
            Timber.e(e)
        }
        return Result.retry()
    }

    @Throws(IOException::class)
    private suspend fun getFolder(): File? {
        val folderId = preferences.getStringValue(R.string.p_google_drive_backup_folder)
        var file: File? = null
        if (!isNullOrEmpty(folderId)) {
            file = drive.getFile(folderId)
        }
        if (file == null || file.trashed) {
            file = drive.findFolder(FOLDER_NAME)
        }
        return if (file == null || file.trashed == true) drive.createFolder(FOLDER_NAME) else file
    }

    private fun notifyExpired(message: Int) {
        val text = context.getString(message)
        val notification = androidx.core.app.NotificationCompat.Builder(
            context,
            org.tasks.notifications.NotificationManager.NOTIFICATION_CHANNEL_MISCELLANEOUS,
        )
            .setSmallIcon(R.drawable.ic_check_white_24dp)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        androidx.core.app.NotificationManagerCompat.from(context).notify(EXPIRED_NOTIFICATION, notification)
    }

    companion object {
        private const val FOLDER_NAME = "Scorda Backups"
        private const val EXPIRED_NOTIFICATION = 4817
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_PURGE = "extra_purge"
    }
}