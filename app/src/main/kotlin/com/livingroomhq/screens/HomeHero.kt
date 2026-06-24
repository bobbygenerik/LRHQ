package com.livingroomhq.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.WeatherCondition
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType

private val HeroMetaShape = RoundedCornerShape(10.dp)
private val HeroMetaTint = Color(0x66081018)

@Composable
internal fun HomeHeroContent(
    channel: Channel?,
    clockTime: String,
    clockDate: String,
    temperatureF: Int?,
    weatherCondition: WeatherCondition?,
    showWeather: Boolean,
    nowTitle: String?,
    nowDescription: String?,
    progress: Float?,
    nextTitle: String?,
    overlayAlpha: Float,
    onSetupLiveTv: (() -> Unit)? = null,
    backdrop: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        backdrop()

        Box(Modifier.fillMaxSize().alpha(overlayAlpha)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = HqDimens.SafeHorizontal,
                        end = HqDimens.SafeHorizontal,
                        top = HqDimens.SafeVertical,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                LiveBadge()
                if (showWeather) {
                    ClockWeather(
                        clockTime = clockTime,
                        clockDate = clockDate,
                        temperatureF = temperatureF,
                        condition = weatherCondition,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = HqDimens.SafeHorizontal,
                        end = HqDimens.SafeHorizontal,
                        bottom = HqDimens.SafeVertical,
                    )
                    .wrapContentWidth()
                    .widthIn(max = 680.dp)
                    .clip(HeroMetaShape)
                    .background(HeroMetaTint)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Bottom,
            ) {
                NowPlayingSummary(
                    channel = channel,
                    nowTitle = nowTitle,
                    nowDescription = nowDescription,
                    progress = progress,
                    onSetupLiveTv = onSetupLiveTv,
                    modifier = Modifier.widthIn(max = 380.dp),
                )

                if (nextTitle != null) {
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.2f)),
                    )
                    Spacer(Modifier.width(12.dp))
                    UpNextSummary(
                        nextTitle = nextTitle,
                        modifier = Modifier.widthIn(max = 240.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFE53E3E))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            "LIVE",
            style = HqType.Badge.copy(color = Color.White, shadow = heroTextShadow()),
        )
    }
}

@Composable
private fun ClockWeather(
    clockTime: String,
    clockDate: String,
    temperatureF: Int?,
    condition: WeatherCondition?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                clockTime,
                style = HqType.Headline.copy(color = Color.White, fontWeight = FontWeight.Bold, shadow = heroTextShadow()),
            )
            Text(
                clockDate,
                style = HqType.Label.copy(color = Color.White.copy(alpha = 0.8f), shadow = heroTextShadow()),
            )
        }
        Spacer(Modifier.width(14.dp))
        Icon(weatherIcon(condition), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            temperatureF?.let { "$it°F" } ?: "—",
            style = HqType.Headline.copy(color = Color.White, fontWeight = FontWeight.Bold, shadow = heroTextShadow()),
        )
    }
}

@Composable
private fun NowPlayingSummary(
    channel: Channel?,
    nowTitle: String?,
    nowDescription: String?,
    progress: Float?,
    onSetupLiveTv: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            "NOW PLAYING",
            style = HqType.HeroSection.copy(color = HqColors.Accent, shadow = heroTextShadow()),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            channel?.name ?: "No live TV loaded",
            style = HqType.Title.copy(color = Color.White, fontWeight = FontWeight.SemiBold, shadow = heroTextShadow()),
            maxLines = 1,
        )
        Text(
            nowTitle ?: "No program info",
            style = HqType.Body.copy(color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Medium, shadow = heroTextShadow()),
            maxLines = 1,
        )
        nowDescription?.let { description ->
            Text(
                description,
                style = HqType.CardCaption.copy(color = Color.White.copy(alpha = 0.68f), shadow = heroTextShadow()),
                maxLines = 1,
            )
        }
        if (channel == null && onSetupLiveTv != null) {
            Spacer(Modifier.height(12.dp))
            FocusableGlassCard(
                onClick = onSetupLiveTv,
                modifier = Modifier.height(40.dp),
                cornerRadius = HqDimens.CornerSm,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) { _ ->
                Text("Set up Live TV", style = HqType.CardTitle.copy(color = HqColors.Accent))
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
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
}

@Composable
private fun UpNextSummary(nextTitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "UP NEXT",
            style = HqType.HeroSectionMuted.copy(
                color = Color.White.copy(alpha = 0.72f),
                shadow = heroTextShadow(),
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            nextTitle,
            style = HqType.Body.copy(
                color = Color.White.copy(alpha = 0.95f),
                fontWeight = FontWeight.SemiBold,
                shadow = heroTextShadow(),
            ),
            maxLines = 2,
        )
    }
}

private fun weatherIcon(condition: WeatherCondition?): ImageVector = when (condition) {
    WeatherCondition.CLEAR -> Icons.Default.WbSunny
    WeatherCondition.RAIN -> Icons.Default.Grain
    WeatherCondition.SNOW -> Icons.Default.AcUnit
    WeatherCondition.STORM -> Icons.Default.Bolt
    WeatherCondition.PARTLY_CLOUDY, WeatherCondition.CLOUDY, null -> Icons.Default.Cloud
}

/** Slightly stronger than default so labels stay readable on bright live video. */
private fun heroTextShadow(): Shadow =
    Shadow(color = Color.Black.copy(alpha = 0.92f), offset = Offset(0f, 2f), blurRadius = 12f)
