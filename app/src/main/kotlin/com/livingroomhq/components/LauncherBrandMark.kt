package com.livingroomhq.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.livingroomhq.R
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens

/** Hex mark only (top crop of full wordmark) at the top of the collapsed sidebar rail. */
@Composable
fun LauncherBrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(HqDimens.CornerSm))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, HqColors.GlassStroke, RoundedCornerShape(HqDimens.CornerSm))
            .padding(5.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Image(
            painter = painterResource(R.drawable.lrhq_logo_transparent),
            contentDescription = "LivingRoom HQ",
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.size(30.dp),
        )
    }
}
