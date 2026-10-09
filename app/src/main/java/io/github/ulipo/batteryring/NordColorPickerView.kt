// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.ulipo.batteryring

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class NordColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val squareRect = RectF()
    private val hueRect = RectF()

    private var hue = 140f
    private var saturation = 1f
    private var value = 0.9f

    var onColorChanged: ((Int) -> Unit)? = null

    val selectedColor: Int
        get() = Color.HSVToColor(floatArrayOf(hue, saturation, value))

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = dp(14f)
        val hueHeight = dp(28f)
        val gap = dp(18f)
        val availableWidth = (width - padding * 2f).coerceAtLeast(1f)
        val squareSize = min(availableWidth, height - padding * 2f - hueHeight - gap)
            .coerceAtLeast(dp(80f))

        squareRect.set(padding, padding, padding + squareSize, padding + squareSize)
        hueRect.set(
            padding,
            squareRect.bottom + gap,
            padding + availableWidth,
            squareRect.bottom + gap + hueHeight
        )

        val pureHue = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        paint.shader = LinearGradient(
            squareRect.left,
            squareRect.top,
            squareRect.right,
            squareRect.top,
            Color.WHITE,
            pureHue,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(squareRect, dp(10f), dp(10f), paint)

        paint.shader = LinearGradient(
            squareRect.left,
            squareRect.top,
            squareRect.left,
            squareRect.bottom,
            Color.TRANSPARENT,
            Color.BLACK,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(squareRect, dp(10f), dp(10f), paint)
        paint.shader = null

        val hueColors = intArrayOf(
            Color.RED,
            Color.YELLOW,
            Color.GREEN,
            Color.CYAN,
            Color.BLUE,
            Color.MAGENTA,
            Color.RED
        )
        paint.shader = LinearGradient(
            hueRect.left,
            hueRect.top,
            hueRect.right,
            hueRect.top,
            hueColors,
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(hueRect, hueHeight / 2f, hueHeight / 2f, paint)
        paint.shader = null

        val markerX = squareRect.left + saturation * squareRect.width()
        val markerY = squareRect.top + (1f - value) * squareRect.height()
        markerPaint.color = if (value > 0.55f) Color.BLACK else Color.WHITE
        canvas.drawCircle(markerX, markerY, dp(7f), markerPaint)

        val hueX = hueRect.left + (hue / 360f) * hueRect.width()
        markerPaint.color = Color.WHITE
        markerPaint.strokeWidth = dp(3f)
        canvas.drawCircle(hueX, hueRect.centerY(), hueRect.height() / 2f + dp(3f), markerPaint)
        markerPaint.strokeWidth = dp(2f)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = NordPalette.Nord3
        canvas.drawRoundRect(squareRect, dp(10f), dp(10f), paint)
        canvas.drawRoundRect(hueRect, hueHeight / 2f, hueHeight / 2f, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE) {
            return true
        }

        when {
            squareRect.contains(event.x, event.y) || event.action == MotionEvent.ACTION_MOVE && event.y <= squareRect.bottom -> {
                saturation = ((event.x - squareRect.left) / squareRect.width()).coerceIn(0f, 1f)
                value = (1f - (event.y - squareRect.top) / squareRect.height()).coerceIn(0f, 1f)
            }
            event.y >= hueRect.top - dp(10f) && event.y <= hueRect.bottom + dp(10f) -> {
                hue = (((event.x - hueRect.left) / hueRect.width()).coerceIn(0f, 1f) * 360f)
                    .coerceIn(0f, 359.999f)
            }
            else -> return true
        }

        invalidate()
        onColorChanged?.invoke(selectedColor)
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (width * 0.9f).toInt().coerceAtLeast(dp(260f).toInt())
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
