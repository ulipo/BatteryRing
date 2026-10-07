package io.github.ulipo.batteryring

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
        val defaultXdp = if (width > 0) width / density / 2f else 180f
        val centerXdp = prefs.getFloat(Prefs.KEY_CENTER_X_DP, defaultXdp)
        val centerYdp = prefs.getFloat(Prefs.KEY_CENTER_Y_DP, 16f)
        val diameterDp = prefs.getFloat(Prefs.KEY_DIAMETER_DP, Prefs.DEFAULT_DIAMETER_DP)
        val thicknessDp = prefs.getFloat(Prefs.KEY_THICKNESS_DP, Prefs.DEFAULT_THICKNESS_DP)

        val cx = centerXdp * density
        val cy = centerYdp * density
        val radius = diameterDp * density / 2f

        paint.strokeWidth = thicknessDp * density
        paint.color = prefs.getInt(Prefs.KEY_COLOR, Prefs.DEFAULT_COLOR)

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val sweep = 360f * (batteryPercent / 100f)
        if (sweep > 0f) {
            canvas.drawArc(arcBounds, -90f, sweep, false, paint)
        }
    }
}
