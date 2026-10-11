// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.ulipo.batteryring

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class BatteryRingView(context: Context) : View(context) {
    private val prefs = Prefs.get(context)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val chargingDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val arcBounds = RectF()
    private val chargingPathInterpolator = AccelerateInterpolator(1.35f)

    private var chargingAnimator: ValueAnimator? = null
    private var chargingCycleFraction = 0f

    var batteryPercent: Float = 100f
        set(value) {
            val clamped = value.coerceIn(0f, 100f)
            if (abs(field - clamped) < 0.001f) return
            field = clamped
            invalidate()
        }

    var isCharging: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updateChargingAnimation()
            invalidate()
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateChargingAnimation()
    }

    override fun onDetachedFromWindow() {
        stopChargingAnimation()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateChargingAnimation()
    }

    private fun updateChargingAnimation() {
        if (isCharging && isAttachedToWindow && windowVisibility == VISIBLE) {
            startChargingAnimation()
        } else {
            stopChargingAnimation()
        }
    }

    private fun startChargingAnimation() {
        if (chargingAnimator?.isRunning == true) return

        chargingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CHARGING_CYCLE_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                chargingCycleFraction = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    private fun stopChargingAnimation() {
        chargingAnimator?.cancel()
        chargingAnimator = null
        chargingCycleFraction = 0f
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
        chargingDotPaint.color = fillPaint.color

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

        // Draw charging animation last so it always sits above the optional border.
        drawChargingDot(
            canvas = canvas,
            cx = cx,
            cy = cy,
            density = density,
            diameterDp = diameterDp,
            radius = radius,
            originDeg = originDeg,
            signedSweep = signedSweep,
            sweep = sweep
        )
    }

    private fun drawChargingDot(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        density: Float,
        diameterDp: Float,
        radius: Float,
        originDeg: Float,
        signedSweep: Float,
        sweep: Float
    ) {
        if (!isCharging || sweep <= 0f) return

        // The moving part occupies most of the cycle. The last part is intentionally
        // blank so the dot visibly disappears before restarting from the origin.
        if (chargingCycleFraction >= CHARGING_MOTION_FRACTION) return

        val linearPathFraction =
            (chargingCycleFraction / CHARGING_MOTION_FRACTION).coerceIn(0f, 1f)
        val pathFraction = chargingPathInterpolator.getInterpolation(linearPathFraction)

        // Grow only during the first 10% of the travelled arc, then keep the final size.
        val growthFraction = (pathFraction / CHARGING_GROWTH_PATH_FRACTION).coerceIn(0f, 1f)
        val maxDotRadiusDp = (diameterDp * 0.09f).coerceIn(
            CHARGING_DOT_MIN_RADIUS_DP,
            CHARGING_DOT_MAX_RADIUS_DP
        )
        val maxDotRadius = maxDotRadiusDp * density
        val minDotRadius = CHARGING_DOT_START_RADIUS_DP * density
        val dotRadius = minDotRadius + (maxDotRadius - minDotRadius) * growthFraction

        // Keep the dot mostly on the ring perimeter, but let its outer edge protrude
        // slightly beyond the configured ring diameter at full size.
        val pathRadius = (radius - maxDotRadius * 0.40f).coerceAtLeast(0f)
        val angleDeg = originDeg - 90f + signedSweep * pathFraction
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val dotX = cx + cos(angleRad).toFloat() * pathRadius
        val dotY = cy + sin(angleRad).toFloat() * pathRadius

        canvas.drawCircle(dotX, dotY, dotRadius, chargingDotPaint)
    }

    companion object {
        private const val CHARGING_CYCLE_MS = 1500L
        private const val CHARGING_MOTION_FRACTION = 0.88f
        private const val CHARGING_GROWTH_PATH_FRACTION = 0.10f
        private const val CHARGING_DOT_START_RADIUS_DP = 0.12f
        private const val CHARGING_DOT_MIN_RADIUS_DP = 1.25f
        private const val CHARGING_DOT_MAX_RADIUS_DP = 2.25f
    }
}
