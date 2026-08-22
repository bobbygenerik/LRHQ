package com.livingroomhq.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType

internal data class PublicPlaylist(val name: String, val url: String)

@Composable
internal fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(HqDimens.CornerSm)
    val focusManager = LocalFocusManager.current

    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) HqColors.FieldFillFocused else HqColors.FieldFill)
            .border(1.dp, if (focused) HqColors.Accent.value else HqColors.GlassSheenTop, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                if (newValue.text != value) {
                    onValueChange(newValue.text)
                }
            },
            textStyle = HqType.Body.copy(color = HqColors.TextPrimary),
            cursorBrush = SolidColor(HqColors.Accent.value),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val selection = textFieldValue.selection
                    val collapsed = selection.collapsed
                    val cursor = selection.start
                    val direction = when (event.key) {
                        Key.DirectionLeft -> if (collapsed && cursor == 0) FocusDirection.Left else null
                        Key.DirectionRight -> if (collapsed && cursor == textFieldValue.text.length) FocusDirection.Right else null
                        Key.DirectionUp -> FocusDirection.Up
                        Key.DirectionDown -> FocusDirection.Down
                        else -> null
                    }
                    if (direction != null) {
                        runCatching { focusManager.moveFocus(direction) }.getOrDefault(false)
                    } else {
                        false
                    }
                },
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(placeholder, style = HqType.Body.copy(color = HqColors.TextTertiary))
                }
                innerTextField()
            },
        )
    }
}

@Composable
internal fun CustomButtonToggle(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(HqDimens.CornerSm))
            .background(HqColors.GlassSheenTop)
            .border(1.dp, HqColors.GlassSheenTop, RoundedCornerShape(HqDimens.CornerSm))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            isSelected -> HqColors.Accent.value.copy(alpha = 0.8f)
                            focused -> HqColors.Track
                            else -> Color.Transparent
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (focused) HqColors.TextPrimary else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelected(option) }
                    .focusable()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = option,
                    style = HqType.Label.copy(
                        color = if (isSelected) HqColors.OnAccent else HqColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}
