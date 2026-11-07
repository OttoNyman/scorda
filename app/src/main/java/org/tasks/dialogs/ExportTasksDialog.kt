package org.tasks.dialogs

import android.app.Dialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import org.tasks.backup.TasksJsonExporter
import javax.inject.Inject

@AndroidEntryPoint
class ExportTasksDialog : DialogFragment() {
    @Inject lateinit var dialogBuilder: DialogBuilder
    @Inject lateinit var tasksJsonExporter: TasksJsonExporter
    private var saved: Uri? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progressDialog = dialogBuilder.newProgressDialog().apply {
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            progress = 0
            setCancelable(false)
            isIndeterminate = false
        }

        progressDialog.show()
        isCancelable = false
        lifecycleScope.launch(NonCancellable) {
            val type = if (requireArguments().getBoolean(ARG_DRIVE)) {
                TasksJsonExporter.ExportType.EXPORT_TYPE_DRIVE_NOW
            } else {
                TasksJsonExporter.ExportType.EXPORT_TYPE_MANUAL
            }
            val file = tasksJsonExporter.exportTasks(activity, type, progressDialog)
            if (type == TasksJsonExporter.ExportType.EXPORT_TYPE_MANUAL) saved = file
        }

        return progressDialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            Bundle().apply { saved?.let { putParcelable(EXTRA_FILE, it) } },
        )
    }

    companion object {
        const val REQUEST_KEY = "export_tasks_result"

        private const val ARG_DRIVE = "drive"
        /** The file a manual export wrote, so the caller can offer to send it. */
        const val EXTRA_FILE = "file"

        fun newExportTasksDialog(uploadToDrive: Boolean = false) = ExportTasksDialog().apply {
            arguments = Bundle().apply { putBoolean(ARG_DRIVE, uploadToDrive) }
        }
    }
}
