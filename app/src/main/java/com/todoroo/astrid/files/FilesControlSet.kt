/*
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.files

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tasks.R
import org.tasks.Strings
import org.tasks.activities.CameraActivity
import org.tasks.compose.edit.AttachmentRow
import org.tasks.data.dao.TaskAttachmentDao
import org.tasks.data.entity.TaskAttachment
import org.tasks.dialogs.AddAttachmentDialog
import org.tasks.extensions.Context.takePersistableUriPermission
import org.tasks.extensions.Fragment.safeStartActivityForResult
import org.tasks.files.FileHelper
import org.tasks.files.FileHelper.newFilePickerIntent
import org.tasks.preferences.Device
import org.tasks.preferences.Preferences
import org.tasks.ui.TaskEditControlFragment
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class FilesControlSet : TaskEditControlFragment() {
    @Inject lateinit var taskAttachmentDao: TaskAttachmentDao
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var device: Device

    @Composable
    override fun Content() {
        val viewState = viewModel.viewState.collectAsStateWithLifecycle().value
        LaunchedEffect(Unit) {
            if (viewState.task.hasTransitory(TaskAttachment.KEY)) {
                for (uri in (viewState.task.getTransitory<ArrayList<Uri>>(TaskAttachment.KEY))!!) {
                    newAttachment(uri)
                }
            }
        }
        val context = LocalContext.current
        var menu by remember { mutableStateOf(false) }
        Box {
            AttachmentRow(
                attachments = viewState.attachments,
                openAttachment = {
                    Timber.d("Clicked open $it")
                    FileHelper.startActionView(
                        context,
                        if (Strings.isNullOrEmpty(it.uri)) null else Uri.parse(it.uri)
                    )
                },
                deleteAttachment = {
                    Timber.d("Clicked delete $it")
                    viewModel.setAttachments(viewState.attachments - it)
                },
                addAttachment = {
                    Timber.d("Add attachment clicked")
                    menu = true
                },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (device.hasCamera()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.take_a_picture)) },
                        onClick = {
                            menu = false
                            takePicture()
                        },
                    )
                }
                if (device.hasMicrophone()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.premium_record_audio)) },
                        onClick = {
                            menu = false
                            recordNote()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pick_from_gallery)) },
                    onClick = {
                        menu = false
                        pickFromGallery()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pick_from_storage)) },
                    onClick = {
                        menu = false
                        pickFromStorage()
                    },
                )
            }
        }
    }

    private fun takePicture() {
        startActivityForResult(
            Intent(requireContext(), CameraActivity::class.java),
            AddAttachmentDialog.REQUEST_CAMERA,
        )
    }

    private fun recordNote() {
        safeStartActivityForResult(
            Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION),
            AddAttachmentDialog.REQUEST_AUDIO,
        )
    }

    private fun pickFromGallery() {
        safeStartActivityForResult(
            Intent(Intent.ACTION_PICK).apply {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            },
            AddAttachmentDialog.REQUEST_GALLERY,
        )
    }

    private fun pickFromStorage() {
        safeStartActivityForResult(
            newFilePickerIntent(
                activity = activity,
                initial = null,
                allowMultiple = true,
                persistPermissions = true,
            ),
            AddAttachmentDialog.REQUEST_STORAGE,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == AddAttachmentDialog.REQUEST_CAMERA || requestCode == AddAttachmentDialog.REQUEST_AUDIO) {
            if (resultCode == Activity.RESULT_OK) {
                lifecycleScope.launch {
                    val uri = data!!.data
                    copyToAttachmentDirectory(uri)
                    FileHelper.delete(requireContext(), uri)
                }
            }
        } else if (requestCode == AddAttachmentDialog.REQUEST_STORAGE || requestCode == AddAttachmentDialog.REQUEST_GALLERY) {
            if (resultCode == Activity.RESULT_OK) {
                lifecycleScope.launch {
                    val clip = data!!.clipData
                    if (clip != null) {
                        for (i in 0 until clip.itemCount) {
                            val item = clip.getItemAt(i)
                            requireContext().takePersistableUriPermission(item.uri)
                            copyToAttachmentDirectory(item.uri)
                        }
                    } else {
                        requireContext().takePersistableUriPermission(data.data!!)
                        copyToAttachmentDirectory(data.data)
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private suspend fun copyToAttachmentDirectory(input: Uri?) {
        val destination = preferences.attachmentsDirectory ?: return
        newAttachment(
            if (FileHelper.isInTree(requireContext(), destination, input!!)) {
                Timber.d("$input already exists in $destination")
                input
            } else {
                Timber.d("Copying $input to $destination")
                FileHelper.copyToUri(requireContext(), destination, input)
            }
        )
    }

    private suspend fun newAttachment(output: Uri) {
        val attachment = TaskAttachment(
            uri = output.toString(),
            name = FileHelper.getFilename(requireContext(), output)!!,
        )
        withContext(Dispatchers.IO) {
            taskAttachmentDao.insert(attachment)
            viewModel.setAttachments(
                viewModel.viewState.value.attachments +
                        (taskAttachmentDao.getAttachment(attachment.remoteId) ?: return@withContext))
        }
    }

    companion object {
        val TAG = R.string.TEA_ctrl_files_pref
    }
}
