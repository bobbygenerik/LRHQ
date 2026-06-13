package com.livingroomhq.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/**
 * Painted sunset-skyline fallback shown in the hero when no live preview is
 * available, so Home never renders a dead black rectangle.
 */
@Composable
fun SunsetCitySkyline(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0F081D),
                    Color(0xFF2E112D),
                    Color(0xFF8B2635),
                    Color(0xFFE2583E),
                    Color(0xFFF09D51),
                ),
                startY = 0f,
                endY = height,
            ),
        )

        val random = java.util.Random(42L)
        for (i in 0 until 30) {
            val sx = random.nextFloat() * width
            val sy = random.nextFloat() * (height * 0.45f)
            val sa = random.nextFloat() * 0.7f + 0.3f
            drawCircle(Color.White.copy(alpha = sa), radius = random.nextFloat() * 1.5f + 0.5f, center = Offset(sx, sy))
        }

        val sunCenter = Offset(width * 0.25f, height * 0.75f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFEEB2).copy(alpha = 0.5f), Color.Transparent),
                center = sunCenter,
                radius = 100.dp.toPx(),
            ),
            radius = 100.dp.toPx(),
            center = sunCenter,
        )
        drawCircle(Color(0xFFFFEEB2), radius = 18.dp.toPx(), center = sunCenter)

        val path = Path()
        path.moveTo(0f, height)
        val buildings = listOf(
            0.08f to 0.4f, 0.12f to 0.35f, 0.15f to 0.5f, 0.18f to 0.32f,
            0.22f to 0.6f, 0.25f to 0.45f, 0.28f to 0.38f, 0.32f to 0.52f,
            0.35f to 0.2f, 0.38f to 0.48f, 0.42f to 0.42f, 0.45f to 0.55f,
            0.48f to 0.3f, 0.52f to 0.62f, 0.55f to 0.45f, 0.58f to 0.38f,
            0.62f to 0.5f, 0.65f to 0.28f, 0.68f to 0.42f, 0.72f to 0.58f,
            0.75f to 0.35f, 0.78f to 0.48f, 0.82f to 0.65f, 0.85f to 0.5f,
            0.88f to 0.4f, 0.92f to 0.55f, 0.96f to 0.3f, 1.0f to 0.45f,
        )
        var prevX = 0f
        for ((pctX, pctH) in buildings) {
            val x = pctX * width
            val h = height * (1f - (pctH * 0.45f))
            path.lineTo(prevX, h)
            path.lineTo(x, h)
            prevX = x
        }
        path.lineTo(width, height)
        path.close()
        drawPath(path = path, color = Color(0xFF04070D))

        val lightRandom = java.util.Random(1337L)
        for (i in 0 until 30) {
            val lx = lightRandom.nextFloat() * width
            val ly = height * (0.6f + lightRandom.nextFloat() * 0.35f)
            drawRect(
                color = Color(0xFFFFD166).copy(alpha = lightRandom.nextFloat() * 0.8f + 0.2f),
                topLeft = Offset(lx, ly),
                size = Size(2.dp.toPx(), 4.dp.toPx()),
            )
        }
    }
}
