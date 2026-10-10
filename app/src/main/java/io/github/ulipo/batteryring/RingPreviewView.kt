// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.ulipo.batteryring

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class RingPreviewView(context: Context) : View(context) {
    private val prefs = Prefs.get(context)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val bounds = RectF()

    var batteryPercent: Float = 100f
        set(value) {
            field = value.coerceIn(0f, 100f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(NordPalette.Nord0)

        val density = resources.displayMetrics.density
        val diameterDp = prefs.getFloat(Prefs.KEY_DIAMETER_DP, Prefs.DEFAULT_DIAMETER_DP)
        val borderThicknessDp = prefs.getFloat(
            Prefs.KEY_BORDER_THICKNESS_DP,
            Prefs.DEFAULT_BORDER_THICKNESS_DP
        )
        val originDeg = prefs.getFloat(Prefs.KEY_START_ANGLE_DEG, Prefs.DEFAULT_START_ANGLE_DEG)
        val clockwise = prefs.getBoolean(Prefs.KEY_CLOCKWISE, Prefs.DEFAULT_CLOCKWISE)
        val scale = 3.0f
        val radius = diameterDp * density * scale / 2f
        val borderWidth = borderThicknessDp * density * scale
        val effectiveRadius = (radius - borderWidth / 2f).coerceAtLeast(1f)
        val cx = width / 2f
        val cy = height / 2f

        fillPaint.color = prefs.getInt(Prefs.KEY_COLOR, Prefs.DEFAULT_COLOR)
        borderPaint.strokeWidth = borderWidth
        borderPaint.color = prefs.getInt(Prefs.KEY_BORDER_COLOR, Prefs.DEFAULT_BORDER_COLOR)

        bounds.set(
            cx - effectiveRadius,
            cy - effectiveRadius,
            cx + effectiveRadius,
            cy + effectiveRadius
        )

        val sweep = 360f * batteryPercent / 100f
        val signedSweep = if (clockwise) sweep else -sweep
        if (sweep >= 360f) {
            canvas.drawCircle(cx, cy, effectiveRadius, fillPaint)
        } else if (sweep > 0f) {
            canvas.drawArc(bounds, originDeg - 90f, signedSweep, true, fillPaint)
        }

        if (borderWidth > 0f && sweep > 0f) {
            if (sweep >= 360f) {
                canvas.drawCircle(cx, cy, effectiveRadius, borderPaint)
            } else {
                canvas.drawArc(bounds, originDeg - 90f, signedSweep, false, borderPaint)
            }
        }
    }
}
