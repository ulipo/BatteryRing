package io.github.ulipo.batteryring

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Surface
import android.view.View

class BatteryRingView(context: Context) : View(context) {
    private val prefs = Prefs.get(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val arcBounds = RectF()

    var batteryPercent: Float = 100f
        set(value) {
            field = value.coerceIn(0f, 100f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val rotation = display?.rotation ?: Surface.ROTATION_0

        // X/Y are always stored in the display's natural orientation. This makes a
        // manual calibration survive screen rotations: the physical camera point is
        // transformed into the current window coordinates at draw time.
        val naturalWidthPx = when (rotation) {
            Surface.ROTATION_90, Surface.ROTATION_270 -> height.toFloat()
            else -> width.toFloat()
        }
        val defaultXdp = if (naturalWidthPx > 0f) naturalWidthPx / density / 2f else 180f
        val naturalX = prefs.getFloat(Prefs.KEY_CENTER_X_DP, defaultXdp) * density
        val naturalY = prefs.getFloat(Prefs.KEY_CENTER_Y_DP, 16f) * density

        val (cx, cy) = naturalToCurrentPoint(
            x = naturalX,
            y = naturalY,
            rotation = rotation,
            currentWidth = width.toFloat(),
            currentHeight = height.toFloat()
        )

        val diameterDp = prefs.getFloat(Prefs.KEY_DIAMETER_DP, Prefs.DEFAULT_DIAMETER_DP)
        val thicknessDp = prefs.getFloat(Prefs.KEY_THICKNESS_DP, Prefs.DEFAULT_THICKNESS_DP)
        val originDeg = prefs.getFloat(Prefs.KEY_START_ANGLE_DEG, Prefs.DEFAULT_START_ANGLE_DEG)
        val radius = diameterDp * density / 2f

        paint.strokeWidth = thicknessDp * density
        paint.color = prefs.getInt(Prefs.KEY_COLOR, Prefs.DEFAULT_COLOR)

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val sweep = 360f * (batteryPercent / 100f)
        if (sweep > 0f) {
            // Android's positive sweep is clockwise. BatteryRing intentionally runs
            // counter-clockwise, therefore the sweep is negative.
            canvas.drawArc(arcBounds, originDeg - 90f, -sweep, false, paint)
        }
    }

    private fun naturalToCurrentPoint(
        x: Float,
        y: Float,
        rotation: Int,
        currentWidth: Float,
        currentHeight: Float
    ): Pair<Float, Float> = when (rotation) {
        Surface.ROTATION_90 -> Pair(currentWidth - y, x)
        Surface.ROTATION_180 -> Pair(currentWidth - x, currentHeight - y)
        Surface.ROTATION_270 -> Pair(y, currentHeight - x)
        else -> Pair(x, y)
    }
}
