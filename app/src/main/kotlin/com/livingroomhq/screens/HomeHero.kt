package com.livingroomhq.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType

@Composable
internal fun HomeHeroContent(
    channel: Channel?,
    clockTime: String,
    clockDate: String,
    temperatureF: Int?,
    showWeather: Boolean,
    nowTitle: String?,
    nowDescription: String?,
    progress: Float?,
    nextTitle: String?,
    backdrop: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        backdrop()

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color(0xE605070D),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            LiveChannelBadge(channel)
            if (showWeather) {
                ClockWeather(clockTime = clockTime, clockDate = clockDate, temperatureF = temperatureF)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 28.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            NowPlayingSummary(
                channel = channel,
                nowTitle = nowTitle,
                nowDescription = nowDescription,
                progress = progress,
                modifier = Modifier.weight(1f),
            )

            if (nextTitle != null) {
                Spacer(Modifier.width(18.dp))
                UpNextPanel(nextTitle)
            }
        }
    }
}

@Composable
private fun LiveChannelBadge(channel: Channel?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE53E3E))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                "LIVE",
                style = HqType.Label.copy(
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            channel?.name ?: "No channel",
            style = HqType.Body.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun ClockWeather(
    clockTime: String,
    clockDate: String,
    temperatureF: Int?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.End) {
            Text(clockTime, style = HqType.Headline.copy(color = Color.White, fontWeight = FontWeight.Bold))
            Text(clockDate, style = HqType.Label.copy(color = Color.White.copy(alpha = 0.7f)))
        }
        Spacer(Modifier.width(14.dp))
        Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            temperatureF?.let { "$it°F" } ?: "—",
            style = HqType.Headline.copy(color = Color.White, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun NowPlayingSummary(
    channel: Channel?,
    nowTitle: String?,
    nowDescription: String?,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            "NOW PLAYING",
            style = HqType.Label.copy(color = HqColors.Accent, fontWeight = FontWeight.Bold, fontSize = 10.sp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            nowTitle ?: (channel?.name ?: "No live TV loaded"),
            style = HqType.Title.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
        Text(
            nowDescription ?: "Add an M3U playlist in Settings to stream live channels.",
            style = HqType.Body.copy(color = Color.White.copy(alpha = 0.72f)),
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress ?: 0f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(HqColors.Accent),
            )
        }
    }
}

@Composable
private fun UpNextPanel(nextTitle: String) {
    Column(
        Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x33FFFFFF))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text("NEXT", style = HqType.Label.copy(color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp))
        Spacer(Modifier.height(3.dp))
        Text(
            nextTitle,
            style = HqType.Body.copy(color = Color.White, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}
