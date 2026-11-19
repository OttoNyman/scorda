package org.tasks.compose.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.tasks.compose.Constants
import org.tasks.themes.ThemeColor

@Composable
fun ColumnScope.ListSettingsContent(
    hasPro: Boolean,
    color: Int,
    colors: List<ThemeColor>,
    text: String,
    error: String,
    requestKeyboard: Boolean,
    setText: (String) -> Unit,
    setColor: (Int) -> Unit,
    headerContent: @Composable ColumnScope.() -> Unit = {},
    extensionContent: @Composable ColumnScope.() -> Unit,
    purchase: () -> Unit,
) {
    headerContent()
    TitleInput(
        text = text,
        error = error,
        requestKeyboard = requestKeyboard,
        modifier = Modifier.padding(horizontal = Constants.KEYLINE_FIRST),
        setText = { setText(it) },
    )
    SelectColorRow(
        hasPro = hasPro,
        color = color,
        colors = colors,
        selectColor = { setColor(it) },
        purchase = { purchase() },
    )
    extensionContent()
}
