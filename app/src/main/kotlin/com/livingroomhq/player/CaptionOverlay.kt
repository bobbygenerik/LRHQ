package com.livingroomhq.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType

@Composable
fun CaptionOverlay(
    text: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 72.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = text.orEmpty(),
                style = HqType.Caption.copy(textAlign = TextAlign.Center),
                modifier = Modifier
                    .background(
                        HqColors.Void.copy(alpha = 0.72f),
                        RoundedCornerShape(HqDimens.CornerSm),
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
