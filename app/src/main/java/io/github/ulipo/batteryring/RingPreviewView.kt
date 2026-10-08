package io.github.ulipo.batteryring

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class RingPreviewView(context: Context) : View(context) {
    private val prefs = Prefs.get(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()

    var batteryPercent: Float = 100f
        set(value) {
            field = value.coerceIn(0f, 100f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(Color.rgb(24, 26, 31))

        val density = resources.displayMetrics.density
        val diameterDp = prefs.getFloat(Prefs.KEY_DIAMETER_DP, Prefs.DEFAULT_DIAMETER_DP)
        val thicknessDp = prefs.getFloat(Prefs.KEY_THICKNESS_DP, Prefs.DEFAULT_THICKNESS_DP)
        val originDeg = prefs.getFloat(Prefs.KEY_START_ANGLE_DEG, Prefs.DEFAULT_START_ANGLE_DEG)
        val scale = 3.0f
        val radius = diameterDp * density * scale / 2f
        val stroke = thicknessDp * density * scale
        val cx = width / 2f
        val cy = height / 2f

        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        canvas.drawCircle(cx, cy, (radius - stroke / 2f).coerceAtLeast(2f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeWidth = stroke
        paint.color = prefs.getInt(Prefs.KEY_COLOR, Prefs.DEFAULT_COLOR)
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(
            bounds,
            originDeg - 90f,
            -360f * batteryPercent / 100f,
            false,
            paint
        )
    }
}
