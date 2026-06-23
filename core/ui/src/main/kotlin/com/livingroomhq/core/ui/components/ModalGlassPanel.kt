package com.livingroomhq.core.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType

/**
 * Centered modal surface for confirmations and action menus — frosted glass
 * panel over the standard scrim so dialogs match the rest of the launcher.
 */
@Composable
fun ModalGlassPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        contentPadding = contentPadding,
        sheenOnFocus = false,
        content = content,
    )
}

@Composable
fun ModalTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = HqType.Headline.copy(
            color = HqColors.TextPrimary,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
fun ModalMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(top = 4.dp),
        style = HqType.Body.copy(color = HqColors.TextSecondary),
    )
}
