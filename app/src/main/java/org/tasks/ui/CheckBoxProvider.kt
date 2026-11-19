package org.tasks.ui

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import dagger.hilt.android.qualifiers.ActivityContext
import org.tasks.R
import org.tasks.data.entity.Task
import org.tasks.icons.MaterialSymbolsGlyphs
import org.tasks.themes.ColorProvider
import org.tasks.themes.TasksIcons
import javax.inject.Inject

class CheckBoxProvider @Inject constructor(
    @param:ActivityContext private val context: Context,
    private val colorProvider: ColorProvider
) {
    fun getCheckBox(task: Task, features: org.tasks.constructor.Constructor) = getDrawable(
        task.getCheckboxRes(),
        if (features.priority) task.priority else Task.Priority.LOW,
    )

    /** A memo shows a note icon where a task has its checkbox, in the same priority color. */
    fun getMemoIcon(task: Task, features: org.tasks.constructor.Constructor): Drawable? =
        MaterialSymbolsGlyphs.drawable(
            context,
            TasksIcons.NOTE,
            24,
            colorProvider.getPriorityColor(if (features.priority) task.priority else Task.Priority.LOW),
        )

    private fun getDrawable(@DrawableRes resId: Int, priority: Int): Drawable {
        val original = AppCompatResources.getDrawable(context, resId)
        val wrapped = original!!.mutate()
        wrapped.setTint(colorProvider.getPriorityColor(priority))
        return wrapped
    }

    companion object {
        fun Task.getCheckboxRes() = getCheckboxRes(isCompleted)

        // a repeating task gets a plain checkbox too; the repeat mark sits next to its due date
        fun getCheckboxRes(isCompleted: Boolean) =
            if (isCompleted) R.drawable.ic_outline_check_box_24px else R.drawable.ic_outline_check_box_outline_blank_24px
    }
}
