package com.app.nisisiafrica.Utils

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class EdgeBlurImageView1 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var blurRadius = 80f // Increased for better effect

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null) // Use hardware for better performance
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        updateMask(w, h)
    }

    private fun updateMask(w: Int, h: Int) {
        val centerX = w / 2f
        val centerY = h / 2f

        // Calculate radius where sharp area ends and blur begins
        val maxRadius = minOf(w, h) / 2f
        val sharpRadius = maxRadius - blurRadius

        // Create gradient: fully opaque in center, transparent at edges
        val gradient = RadialGradient(
            centerX, centerY, maxRadius,
            intArrayOf(
                0xFFFFFFFF.toInt(), // Fully opaque (sharp center)
                0xFFFFFFFF.toInt(), // Stay sharp until sharpRadius
                0x00FFFFFF          // Fully transparent at edges
            ),
            floatArrayOf(
                0f,
                sharpRadius / maxRadius, // Sharp until here
                1f                       // Fade to transparent
            ),
            Shader.TileMode.CLAMP
        )

        maskPaint.shader = gradient
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) // Inverted mask
    }

    override fun onDraw(canvas: Canvas) {
        if (drawable == null) return

        val saveCount = canvas.saveLayer(bounds, null)

        // Draw image sharp
        super.onDraw(canvas)

        // Apply edge fade (inverted mask removes edges)
        canvas.drawRect(bounds, maskPaint)

        canvas.restoreToCount(saveCount)
    }

    fun setBlurRadius(radius: Float) {
        blurRadius = radius
        updateMask(width, height)
        invalidate()
    }
}