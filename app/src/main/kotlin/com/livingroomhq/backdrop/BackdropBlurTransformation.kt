package com.livingroomhq.backdrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.max

/**
 * Gaussian blur for backdrop fills. Uses RenderScript when available so blur works
 * on API 24–30 devices where Compose [androidx.compose.ui.draw.blur] is unavailable.
 */
@Suppress("DEPRECATION")
class BackdropBlurTransformation(
    private val context: Context,
    private val radius: Float = 22f,
    private val sampling: Float = 2f,
) : Transformation {

    init {
        require(radius in 0f..25f) { "radius must be in [0, 25]." }
        require(sampling > 0f) { "sampling must be > 0." }
    }

    override val cacheKey: String = "${javaClass.name}-$radius-$sampling"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scaledWidth = max(1, (input.width / sampling).toInt())
        val scaledHeight = max(1, (input.height / sampling).toInt())
        val output = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            scale(1f / sampling, 1f / sampling)
            drawBitmap(input, 0f, 0f, paint)
        }

        var script: RenderScript? = null
        var inputAlloc: Allocation? = null
        var outputAlloc: Allocation? = null
        var blur: ScriptIntrinsicBlur? = null
        try {
            script = RenderScript.create(context)
            inputAlloc = Allocation.createFromBitmap(
                script,
                output,
                Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT,
            )
            outputAlloc = Allocation.createTyped(script, inputAlloc.type)
            blur = ScriptIntrinsicBlur.create(script, Element.U8_4(script))
            blur.setRadius(radius)
            blur.setInput(inputAlloc)
            blur.forEach(outputAlloc)
            outputAlloc.copyTo(output)
        } finally {
            blur?.destroy()
            inputAlloc?.destroy()
            outputAlloc?.destroy()
            script?.destroy()
        }
        return output
    }
}
