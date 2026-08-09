package com.livingroomhq.core.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color

/**
 * Motion tokens — see MASTER.md Interaction thesis.
 * No bounce / elastic. Focus scale stays at 1.02.
 */
object HqMotion {
    const val FastMs = 180
    const val NormalMs = 280
    const val SlowMs = 400
    const val AmbientMs = 480
    const val ArtworkCycleMs = 1_200
    const val StaggerMs = 40

    /** Focus grow — thesis ≈1.02; never ≥1.04. */
    const val FocusScale = 1.02f

    val EaseOut = FastOutSlowInEasing

    fun <T> fast(): TweenSpec<T> = tween(durationMillis = FastMs, easing = EaseOut)
    fun <T> normal(): TweenSpec<T> = tween(durationMillis = NormalMs, easing = EaseOut)
    fun <T> slow(): TweenSpec<T> = tween(durationMillis = SlowMs, easing = EaseOut)
    fun <T> ambient(): TweenSpec<T> = tween(durationMillis = AmbientMs, easing = EaseOut)
    fun <T> artworkCycle(): TweenSpec<T> = tween(durationMillis = ArtworkCycleMs, easing = EaseOut)

    fun colorFast(): TweenSpec<Color> = tween(durationMillis = FastMs, easing = EaseOut)

    fun focusSpring() = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}
