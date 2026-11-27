package com.example.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import com.example.sensor.GForceData
import kotlin.math.hypot
import kotlin.math.min

/**
 * Lightweight native Android Canvas view rendering a precision circular G-Force meter
 * inside the floating overlay window (zero ComposeView overhead).
 */
class GForceMeterView(context: Context) : View(context) {

    private var data: GForceData = GForceData()
    private var isDark: Boolean = true
    private var maxScaleG: Float = 1.5f

    private val density = resources.displayMetrics.density

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }

    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        pathEffect = DashPathEffect(floatArrayOf(3f * density, 3f * density), 0f)
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.85f * density
    }

    private val fillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun updateState(gForce: GForceData, dark: Boolean) {
        this.data = gForce
        this.isDark = dark
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (min(width, height) / 2f) - 2.5f * density
        if (radius <= 0f) return

        // Palette aligned with Google Material 3 Dark / Light overlay tokens
        fillBgPaint.color = if (isDark) 0x33000000 else 0x12000000
        outerRingPaint.color = if (isDark) 0x44FFFFFF else 0x33000000
        innerRingPaint.color = if (isDark) 0x29FFFFFF else 0x22000000
        crosshairPaint.color = if (isDark) 0x26FFFFFF else 0x1E000000

        // Subtle circular dial face
        canvas.drawCircle(cx, cy, radius, fillBgPaint)
        // Outer ring (1.5G) & inner ring (0.75G)
        canvas.drawCircle(cx, cy, radius, outerRingPaint)
        canvas.drawCircle(cx, cy, radius * 0.5f, innerRingPaint)

        // Orthogonal crosshairs
        canvas.drawLine(cx - radius, cy, cx + radius, cy, crosshairPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, crosshairPaint)

        val dotColor = when {
            data.totalG >= 1.0f -> if (isDark) 0xFFF28B82.toInt() else 0xFFD93025.toInt()
            data.totalG >= 0.5f -> if (isDark) 0xFFFDD663.toInt() else 0xFFE37400.toInt()
            else -> if (isDark) 0xFFA8C7FA.toInt() else 0xFF1A73E8.toInt()
        }

        val maxDotTravel = radius - 3.5f * density
        val trail = data.trail
        if (trail.size > 1) {
            trail.forEachIndexed { index, sample ->
                val fraction = (index + 1).toFloat() / trail.size.toFloat()
                val (px, py) = mapGToPoint(sample.lateralG, sample.longitudinalG, cx, cy, maxDotTravel)
                val alpha = (fraction * 95f).toInt().coerceIn(10, 110)
                trailPaint.color = (dotColor and 0x00FFFFFF) or (alpha shl 24)
                val trailRadius = (1.2f + 1.4f * fraction) * density
                canvas.drawCircle(px, py, trailRadius, trailPaint)
            }
        }

        val (dotX, dotY) = mapGToPoint(data.lateralG, data.longitudinalG, cx, cy, maxDotTravel)
        val dotSize = (if (width < 46 * density) 3.4f else 4.4f) * density

        // Soft halo + crisp core dot
        dotHaloPaint.color = (dotColor and 0x00FFFFFF) or (0x44 shl 24)
        canvas.drawCircle(dotX, dotY, dotSize * 1.65f, dotHaloPaint)

        dotCorePaint.color = dotColor
        canvas.drawCircle(dotX, dotY, dotSize, dotCorePaint)
    }

    private fun mapGToPoint(
        latG: Float,
        longG: Float,
        cx: Float,
        cy: Float,
        maxTravelPx: Float
    ): Pair<Float, Float> {
        val normX = latG / maxScaleG
        val normY = -longG / maxScaleG // Up = forward/braking G, Down = acceleration G
        val mag = hypot(normX, normY)
        val scale = if (mag > 1f) 1f / mag else 1f
        return (cx + normX * scale * maxTravelPx) to (cy + normY * scale * maxTravelPx)
    }
}
