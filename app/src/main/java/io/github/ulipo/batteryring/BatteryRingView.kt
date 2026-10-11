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
import kotlin.math.roundToInt
import kotlin.math.sin

class BatteryRingView(context: Context) : View(context) {
    private val prefs = Prefs.get(context)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val chargingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val alertPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val arcBounds = RectF()
    private val chargingPathInterpolator = AccelerateInterpolator(1.35f)

    private var chargingAnimator: ValueAnimator? = null
    private var chargingCycleFraction = 0f

    private var alertAnimator: ValueAnimator? = null
    private var alertCycleFraction = 0f
    private var alertPeriodMs = 0L

    var batteryPercent: Float = 100f
        set(value) {
            val clamped = value.coerceIn(0f, 100f)
            if (abs(field - clamped) < 0.001f) return
            field = clamped
            updateAlertAnimation()
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
        updateAlertAnimation()
    }

    override fun onDetachedFromWindow() {
        stopChargingAnimation()
        stopAlertAnimation()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateChargingAnimation()
        updateAlertAnimation()
    }

    fun refreshAlertAnimation() {
        updateAlertAnimation(forceRestart = true)
        invalidate()
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

    private fun updateAlertAnimation(forceRestart: Boolean = false) {
        val alert = currentAlertStyle()
        if (alert == null || !isAttachedToWindow || windowVisibility != VISIBLE) {
            stopAlertAnimation()
            return
        }

        if (!forceRestart && alertAnimator?.isRunning == true && alertPeriodMs == alert.periodMs) {
            return
        }

        stopAlertAnimation()
        alertPeriodMs = alert.periodMs
        alertAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = alert.periodMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                alertCycleFraction = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    private fun stopAlertAnimation() {
        alertAnimator?.cancel()
        alertAnimator = null
        alertCycleFraction = 0f
        alertPeriodMs = 0L
    }

    private fun currentAlertStyle(): AlertStyle? {
        if (!prefs.getBoolean(Prefs.KEY_LOW_ALERT_ENABLED, Prefs.DEFAULT_LOW_ALERT_ENABLED)) {
            return null
        }

        val criticalThreshold = prefs.getFloat(
            Prefs.KEY_CRITICAL_THRESHOLD,
            Prefs.DEFAULT_CRITICAL_THRESHOLD
        ).coerceIn(1f, 100f)
        val warningThreshold = prefs.getFloat(
            Prefs.KEY_WARNING_THRESHOLD,
            Prefs.DEFAULT_WARNING_THRESHOLD
        ).coerceIn(1f, 100f)

        return when {
            batteryPercent <= criticalThreshold -> AlertStyle(
                color = prefs.getInt(
                    Prefs.KEY_CRITICAL_COLOR,
                    Prefs.DEFAULT_CRITICAL_COLOR
                ),
                periodMs = (
                    prefs.getFloat(
                        Prefs.KEY_CRITICAL_PERIOD_SECONDS,
                        Prefs.DEFAULT_CRITICAL_PERIOD_SECONDS
                    ).coerceIn(MIN_ALERT_PERIOD_SECONDS, MAX_ALERT_PERIOD_SECONDS) * 1000f
                ).roundToInt().toLong()
            )

            batteryPercent <= warningThreshold -> AlertStyle(
                color = prefs.getInt(
                    Prefs.KEY_WARNING_COLOR,
                    Prefs.DEFAULT_WARNING_COLOR
                ),
                periodMs = (
                    prefs.getFloat(
                        Prefs.KEY_WARNING_PERIOD_SECONDS,
                        Prefs.DEFAULT_WARNING_PERIOD_SECONDS
                    ).coerceIn(MIN_ALERT_PERIOD_SECONDS, MAX_ALERT_PERIOD_SECONDS) * 1000f
                ).roundToInt().toLong()
            )

            else -> null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val density = resources.displayMetrics.density

        val diameterDp = prefs.getFloat(Prefs.KEY_DIAMETER_DP, Prefs.DEFAULT_DIAMETER_DP)
        val borderThicknessDp = prefs.getFloat(
            Prefs.KEY_BORDER_THICKNESS_DP,
            Prefs.DEFAULT_BORDER_THICKNESS_DP
        )
        val originDeg = prefs.getFloat(
            Prefs.KEY_START_ANGLE_DEG,
            Prefs.DEFAULT_START_ANGLE_DEG
        )
        val clockwise = prefs.getBoolean(
            Prefs.KEY_CLOCKWISE,
            Prefs.DEFAULT_CLOCKWISE
        )

        val radius = diameterDp * density / 2f
        val borderWidthPx = borderThicknessDp * density
        val effectiveRadius = (radius - borderWidthPx / 2f).coerceAtLeast(1f)

        val fillColor = prefs.getInt(Prefs.KEY_COLOR, Prefs.DEFAULT_COLOR)
        fillPaint.color = fillColor
        chargingPaint.color = fillColor

        borderPaint.strokeWidth = borderWidthPx
        borderPaint.color = prefs.getInt(
            Prefs.KEY_BORDER_COLOR,
            Prefs.DEFAULT_BORDER_COLOR
        )

        arcBounds.set(
            cx - effectiveRadius,
            cy - effectiveRadius,
            cx + effectiveRadius,
            cy + effectiveRadius
        )

        val sweep = 360f * (batteryPercent / 100f)
        val signedSweep = if (clockwise) sweep else -sweep

        fillPaint.alpha = if (isCharging) CHARGING_BASE_ALPHA else 255
        drawSector(
            canvas = canvas,
            cx = cx,
            cy = cy,
            sweep = sweep,
            signedSweep = signedSweep,
            originDeg = originDeg,
            paint = fillPaint
        )

        if (isCharging && sweep > 0f) {
            drawChargingOpacityWave(
                canvas = canvas,
                cx = cx,
                cy = cy,
                sweep = sweep,
                signedSweep = signedSweep,
                originDeg = originDeg
            )
        }

        drawLowBatteryPulse(canvas, cx, cy, effectiveRadius)

        // Draw the border last so it remains crisp above both animations.
        if (borderWidthPx > 0f && sweep > 0f) {
            if (sweep >= 360f) {
                canvas.drawCircle(cx, cy, effectiveRadius, borderPaint)
            } else {
                canvas.drawArc(
                    arcBounds,
                    originDeg - 90f,
                    signedSweep,
                    false,
                    borderPaint
                )
            }
        }
    }

    private fun drawLowBatteryPulse(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        effectiveRadius: Float
    ) {
        val alert = currentAlertStyle() ?: return
        if (alertAnimator?.isRunning != true) return

        // One complete pulse per configured period: invisible -> translucent -> invisible.
        val wave = sin(Math.PI * alertCycleFraction.toDouble()).toFloat().coerceIn(0f, 1f)
        alertPaint.color = alert.color
        alertPaint.alpha = (ALERT_MAX_ALPHA * wave).roundToInt().coerceIn(0, 255)

        canvas.drawCircle(cx, cy, effectiveRadius, alertPaint)
    }

    private fun drawChargingOpacityWave(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sweep: Float,
        signedSweep: Float,
        originDeg: Float
    ) {
        if (chargingCycleFraction < CHARGING_REVEAL_PHASE) {
            val revealLinear =
                (chargingCycleFraction / CHARGING_REVEAL_PHASE).coerceIn(0f, 1f)
            val revealFraction =
                chargingPathInterpolator.getInterpolation(revealLinear)

            chargingPaint.alpha = 255
            val animatedSignedSweep = signedSweep * revealFraction
            val animatedSweep = sweep * revealFraction

            drawSector(
                canvas = canvas,
                cx = cx,
                cy = cy,
                sweep = animatedSweep,
                signedSweep = animatedSignedSweep,
                originDeg = originDeg,
                paint = chargingPaint
            )
        } else {
            val fadeFraction =
                ((chargingCycleFraction - CHARGING_REVEAL_PHASE) /
                    (1f - CHARGING_REVEAL_PHASE)).coerceIn(0f, 1f)

            chargingPaint.alpha = (255f * (1f - fadeFraction)).toInt()

            drawSector(
                canvas = canvas,
                cx = cx,
                cy = cy,
                sweep = sweep,
                signedSweep = signedSweep,
                originDeg = originDeg,
                paint = chargingPaint
            )
        }
    }

    private fun drawSector(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sweep: Float,
        signedSweep: Float,
        originDeg: Float,
        paint: Paint
    ) {
        if (sweep >= 360f) {
            canvas.drawCircle(cx, cy, arcBounds.width() / 2f, paint)
        } else if (sweep > 0f) {
            canvas.drawArc(
                arcBounds,
                originDeg - 90f,
                signedSweep,
                true,
                paint
            )
        }
    }

    private data class AlertStyle(
        val color: Int,
        val periodMs: Long
    )

    companion object {
        private const val CHARGING_CYCLE_MS = 1700L
        private const val CHARGING_REVEAL_PHASE = 0.70f
        private const val CHARGING_BASE_ALPHA = 96

        private const val ALERT_MAX_ALPHA = 128f
        private const val MIN_ALERT_PERIOD_SECONDS = 0.5f
        private const val MAX_ALERT_PERIOD_SECONDS = 10f
    }
}
