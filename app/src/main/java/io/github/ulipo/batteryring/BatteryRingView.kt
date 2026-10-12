// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.ulipo.batteryring

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.roundToInt

class BatteryRingView(context: Context) : View(context) {
    private val prefs = Prefs.get(context)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val chargingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    private var alertMix = 0f
    private var alertPulseScheduled = false
    private var activeAlertStyle: AlertStyle? = null

    private val alertRestartRunnable = Runnable {
        alertPulseScheduled = false
        val alert = currentAlertStyle()
        if (alert == null || !isAttachedToWindow || windowVisibility != VISIBLE) {
            stopAlertAnimation()
        } else {
            activeAlertStyle = alert
            startAlertPulse(alert)
        }
    }

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

        val sameAlert = activeAlertStyle == alert
        val alreadyActive = alertAnimator?.isRunning == true || alertPulseScheduled
        if (!forceRestart && sameAlert && alreadyActive) return

        stopAlertAnimation()
        activeAlertStyle = alert
        startAlertPulse(alert)
    }

    private fun startAlertPulse(alert: AlertStyle) {
        if (!isAttachedToWindow || windowVisibility != VISIBLE) return

        var cancelled = false
        alertMix = 1f
        postInvalidateOnAnimation()

        alertAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (alert.kind) {
                AlertKind.WARNING -> WARNING_FADE_MS
                AlertKind.CRITICAL -> CRITICAL_EFFECT_MS
            }
            interpolator = LinearInterpolator()
            addUpdateListener {
                val elapsedMs = it.animatedFraction * duration.toFloat()
                alertMix = when (alert.kind) {
                    AlertKind.WARNING -> {
                        // Warning: alert color appears immediately, then fades to normal.
                        (1f - elapsedMs / WARNING_FADE_MS).coerceIn(0f, 1f)
                    }
                    AlertKind.CRITICAL -> {
                        // Critical: first short flash, tiny gap, second abrupt flash,
                        // then a 300 ms fade back to the normal ring color.
                        when {
                            elapsedMs < CRITICAL_FIRST_FLASH_MS -> 1f
                            elapsedMs < CRITICAL_SECOND_FLASH_START_MS -> 0f
                            else -> (
                                1f -
                                    (elapsedMs - CRITICAL_SECOND_FLASH_START_MS) /
                                    CRITICAL_FADE_MS
                                ).coerceIn(0f, 1f)
                        }
                    }
                }
                postInvalidateOnAnimation()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    alertAnimator = null
                    alertMix = 0f
                    postInvalidateOnAnimation()

                    if (!cancelled &&
                        currentAlertStyle() != null &&
                        isAttachedToWindow &&
                        windowVisibility == VISIBLE
                    ) {
                        alertPulseScheduled = true
                        postDelayed(alertRestartRunnable, alert.intervalMs)
                    }
                }
            })
            start()
        }
    }

    private fun stopAlertAnimation() {
        removeCallbacks(alertRestartRunnable)
        alertPulseScheduled = false
        activeAlertStyle = null

        val animator = alertAnimator
        alertAnimator = null
        animator?.cancel()

        alertMix = 0f
        invalidate()
    }

    private fun currentAlertStyle(): AlertStyle? {
        if (!prefs.getBoolean(Prefs.KEY_LOW_ALERT_ENABLED, Prefs.DEFAULT_LOW_ALERT_ENABLED)) {
            return null
        }

        val warningThreshold = prefs.getFloat(
            Prefs.KEY_WARNING_THRESHOLD,
            Prefs.DEFAULT_WARNING_THRESHOLD
        ).coerceIn(1f, 100f)
        val criticalThreshold = prefs.getFloat(
            Prefs.KEY_CRITICAL_THRESHOLD,
            Prefs.DEFAULT_CRITICAL_THRESHOLD
        ).coerceIn(1f, warningThreshold)

        return when {
            batteryPercent <= criticalThreshold -> AlertStyle(
                kind = AlertKind.CRITICAL,
                color = prefs.getInt(
                    Prefs.KEY_CRITICAL_COLOR,
                    Prefs.DEFAULT_CRITICAL_COLOR
                ),
                intervalMs = (
                    prefs.getFloat(
                        Prefs.KEY_CRITICAL_INTERVAL_SECONDS,
                        Prefs.DEFAULT_CRITICAL_INTERVAL_SECONDS
                    ).coerceIn(MIN_ALERT_INTERVAL_SECONDS, MAX_ALERT_INTERVAL_SECONDS) * 1000f
                ).roundToInt().toLong()
            )

            batteryPercent <= warningThreshold -> AlertStyle(
                kind = AlertKind.WARNING,
                color = prefs.getInt(
                    Prefs.KEY_WARNING_COLOR,
                    Prefs.DEFAULT_WARNING_COLOR
                ),
                intervalMs = (
                    prefs.getFloat(
                        Prefs.KEY_WARNING_INTERVAL_SECONDS,
                        Prefs.DEFAULT_WARNING_INTERVAL_SECONDS
                    ).coerceIn(MIN_ALERT_INTERVAL_SECONDS, MAX_ALERT_INTERVAL_SECONDS) * 1000f
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
        val animatedFillColor = currentAnimatedFillColor(fillColor)
        fillPaint.color = animatedFillColor
        chargingPaint.color = animatedFillColor

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

    private fun currentAnimatedFillColor(baseColor: Int): Int {
        val alert = currentAlertStyle() ?: return baseColor
        if (alertAnimator?.isRunning != true) return baseColor

        return ColorUtils.blendARGB(baseColor, alert.color, alertMix)
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

    private enum class AlertKind {
        WARNING,
        CRITICAL
    }

    private data class AlertStyle(
        val kind: AlertKind,
        val color: Int,
        val intervalMs: Long
    )

    companion object {
        private const val CHARGING_CYCLE_MS = 1700L
        private const val CHARGING_REVEAL_PHASE = 0.70f
        private const val CHARGING_BASE_ALPHA = 96

        private const val WARNING_FADE_MS = 300L

        private const val CRITICAL_FIRST_FLASH_MS = 60L
        private const val CRITICAL_SECOND_FLASH_START_MS = 120L
        private const val CRITICAL_FADE_MS = 300L
        private const val CRITICAL_EFFECT_MS =
            CRITICAL_SECOND_FLASH_START_MS + CRITICAL_FADE_MS

        private const val MIN_ALERT_INTERVAL_SECONDS = 0.5f
        private const val MAX_ALERT_INTERVAL_SECONDS = 10f
    }
}
