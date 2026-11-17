package org.tasks.compose.components

import org.tasks.themes.TasksIcons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun Chevron(
    collapsed: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val rotation by animateFloatAsState(
        targetValue = if (!collapsed) 0f else if (rtl) 90f else -90f,
        animationSpec = tween(250),
        label = "arrow rotation",
    )
    SymbolIcon(
        modifier = Modifier.rotate(rotation),
        name = TasksIcons.EXPAND_MORE,
        contentDescription = null,
        tint = tint,
    )
}