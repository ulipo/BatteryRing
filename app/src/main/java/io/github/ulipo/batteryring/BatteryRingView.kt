// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.ulipo.batteryring

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.abs

class BatteryRingView(context: Context) : View(context) {
    private val prefs = Prefs.get(context)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val arcBounds = RectF()

    var batteryPercent: Float = 100f
        set(value) {
            val clamped = value.coerceIn(0f, 100f)
            if (abs(field - clamped) < 0.001f) return
            field = clamped
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // The WindowManager positions this small overlay directly around the physical
        // camera. The view itself only needs to draw at its local centre.
        val cx = width / 2f
        val cy = height / 2f
        val density = resources.displayMetrics.density

        val diameterDp = prefs.getFloat(Prefs.KEY_DIAMETER_DP, Prefs.DEFAULT_DIAMETER_DP)
        val borderThicknessDp = prefs.getFloat(
            Prefs.KEY_BORDER_THICKNESS_DP,
            Prefs.DEFAULT_BORDER_THICKNESS_DP
        )
        val originDeg = prefs.getFloat(Prefs.KEY_START_ANGLE_DEG, Prefs.DEFAULT_START_ANGLE_DEG)
        val clockwise = prefs.getBoolean(Prefs.KEY_CLOCKWISE, Prefs.DEFAULT_CLOCKWISE)
        val radius = diameterDp * density / 2f
        val borderWidthPx = borderThicknessDp * density
        val effectiveRadius = (radius - borderWidthPx / 2f).coerceAtLeast(1f)

        fillPaint.color = prefs.getInt(Prefs.KEY_COLOR, Prefs.DEFAULT_COLOR)
        borderPaint.strokeWidth = borderWidthPx
        borderPaint.color = prefs.getInt(Prefs.KEY_BORDER_COLOR, Prefs.DEFAULT_BORDER_COLOR)

        arcBounds.set(
            cx - effectiveRadius,
            cy - effectiveRadius,
            cx + effectiveRadius,
            cy + effectiveRadius
        )

        val sweep = 360f * (batteryPercent / 100f)
        val signedSweep = if (clockwise) sweep else -sweep
        if (sweep >= 360f) {
            canvas.drawCircle(cx, cy, effectiveRadius, fillPaint)
        } else if (sweep > 0f) {
            canvas.drawArc(arcBounds, originDeg - 90f, signedSweep, true, fillPaint)
        }

        if (borderWidthPx > 0f && sweep > 0f) {
            if (sweep >= 360f) {
                canvas.drawCircle(cx, cy, effectiveRadius, borderPaint)
            } else {
                canvas.drawArc(arcBounds, originDeg - 90f, signedSweep, false, borderPaint)
            }
        }
    }
}
