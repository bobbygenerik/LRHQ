package com.livingroomhq.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.livingroomhq.R

/** LRHQ mark in the content top-left, clear of the collapsed sidebar rail. */
@Composable
fun LauncherBrandMark(modifier: Modifier = Modifier) {
    Box(modifier) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = "LivingRoom HQ",
            modifier = Modifier.size(36.dp),
        )
    }
}
