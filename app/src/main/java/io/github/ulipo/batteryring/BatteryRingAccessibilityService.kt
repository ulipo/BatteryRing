// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.ulipo.batteryring

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.PowerManager
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class BatteryRingAccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var windowManager: WindowManager
    private lateinit var powerManager: PowerManager

    private var ringView: BatteryRingView? = null
    private var ringLayoutParams: WindowManager.LayoutParams? = null
    private var overlayAttached = false
    private var batteryReceiverRegistered = false
    private var screenReceiverRegistered = false
    private var screenInteractive = true
    private var lastBatteryPercent = -1f

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                updateBatteryFromIntent(intent)
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF -> {
                    // Read the authoritative state instead of trusting the broadcast alone.
                    screenInteractive = powerManager.isInteractive
                    refreshActiveState()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs.get(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager

        // BatteryRing never consumes accessibility events. Explicitly request none so
        // Android does not perform needless event delivery/IPC while the service lives.
        serviceInfo = serviceInfo.apply {
            eventTypes = 0
            notificationTimeout = 0
        }

        prefs.registerOnSharedPreferenceChangeListener(this)
        registerScreenReceiver()
        screenInteractive = powerManager.isInteractive
        refreshActiveState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Wait until the attached display/window metrics reflect the new rotation.
        ringView?.post {
            if (isActive()) updateCompactOverlayLayout()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Prefs.KEY_ENABLED -> refreshActiveState()
            Prefs.KEY_DETECT_REQUEST_ID -> detectCameraCutoutWithTemporaryOverlay()
            Prefs.KEY_CENTER_X_DP,
            Prefs.KEY_CENTER_Y_DP,
            Prefs.KEY_DIAMETER_DP -> {
                if (isActive()) updateCompactOverlayLayout()
                ringView?.invalidate()
            }
            Prefs.KEY_BORDER_THICKNESS_DP,
            Prefs.KEY_START_ANGLE_DEG,
            Prefs.KEY_CLOCKWISE,
            Prefs.KEY_COLOR,
            Prefs.KEY_BORDER_COLOR -> ringView?.invalidate()
            // Detection diagnostics and unrelated preferences do not affect drawing.
        }
    }

    private fun isActive(): Boolean =
        screenInteractive && prefs.getBoolean(Prefs.KEY_ENABLED, true)

    private fun refreshActiveState() {
        if (isActive()) {
            registerBatteryReceiver()
            showCompactOverlay()
        } else {
            hideOverlay()
            unregisterBatteryReceiver()
        }
    }

    private fun showCompactOverlay() {
        if (overlayAttached) {
            updateCompactOverlayLayout()
            return
        }

        val view = BatteryRingView(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }

        val params = createOverlayLayoutParams(1, 1)
        applyCompactGeometry(params)

        try {
            view.batteryPercent = if (lastBatteryPercent >= 0f) lastBatteryPercent else 100f
            windowManager.addView(view, params)
            ringView = view
            ringLayoutParams = params
            overlayAttached = true
        } catch (_: Exception) {
            ringView = null
            ringLayoutParams = null
            overlayAttached = false
        }
    }

    private fun updateCompactOverlayLayout() {
        val view = ringView ?: return
        val params = ringLayoutParams ?: return
        if (!overlayAttached) return

        applyCompactGeometry(params)
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
            // If the system detached the view during a configuration transition,
            // recreate it on the next state refresh.
            ringView = null
            ringLayoutParams = null
            overlayAttached = false
            if (isActive()) showCompactOverlay()
        }
    }

    private fun applyCompactGeometry(params: WindowManager.LayoutParams) {
        val density = resources.displayMetrics.density
        val diameterPx = prefs.getFloat(Prefs.KEY_DIAMETER_DP, Prefs.DEFAULT_DIAMETER_DP) * density
        val paddingPx = COMPACT_PADDING_DP * density
        val sizePx = (diameterPx + 2f * paddingPx).roundToInt().coerceAtLeast(1)

        val geometry = currentDisplayGeometry()
        val naturalWidthPx = when (geometry.rotation) {
            Surface.ROTATION_90, Surface.ROTATION_270 -> geometry.height.toFloat()
            else -> geometry.width.toFloat()
        }
        val defaultXdp = naturalWidthPx / density / 2f
        val naturalX = prefs.getFloat(Prefs.KEY_CENTER_X_DP, defaultXdp) * density
        val naturalY = prefs.getFloat(Prefs.KEY_CENTER_Y_DP, 16f) * density
        val (centerX, centerY) = naturalToCurrentPoint(
            x = naturalX,
            y = naturalY,
            rotation = geometry.rotation,
            currentWidth = geometry.width.toFloat(),
            currentHeight = geometry.height.toFloat()
        )

        params.width = sizePx
        params.height = sizePx
        params.x = (centerX - sizePx / 2f).roundToInt()
        params.y = (centerY - sizePx / 2f).roundToInt()
    }

    private fun hideOverlay() {
        val view = ringView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
        ringView = null
        ringLayoutParams = null
        overlayAttached = false
    }

    /**
     * Automatic detection gets a full-screen accessibility overlay only for the few
     * milliseconds needed to read DisplayCutout. Normal operation keeps the tiny
     * camera-sized overlay instead.
     */
    private fun detectCameraCutoutWithTemporaryOverlay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            writeDetectionFailure("Rilevamento non disponibile: serve Android 9 o successivo.")
            return
        }
        if (!screenInteractive) {
            writeDetectionFailure("Accendi lo schermo e riprova il rilevamento.")
            return
        }
        if (!prefs.getBoolean(Prefs.KEY_ENABLED, true)) {
            writeDetectionFailure("Attiva l'indicatore e riprova il rilevamento.")
            return
        }

        val detector = View(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val params = createOverlayLayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        var handled = false
        detector.setOnApplyWindowInsetsListener { view, insets ->
            if (!handled) {
                handled = true
                try {
                    processCutoutFromDetector(view, insets)
                } finally {
                    view.post { removeTemporaryDetector(view) }
                }
            }
            insets
        }

        try {
            windowManager.addView(detector, params)
            detector.requestApplyInsets()
        } catch (_: Exception) {
            writeDetectionFailure("Impossibile creare l'overlay temporaneo di rilevamento.")
            removeTemporaryDetector(detector)
        }
    }

    private fun processCutoutFromDetector(view: View, insets: WindowInsets) {
        val cutout = insets.displayCutout
        val rawRects = cutout?.boundingRects.orEmpty()
        if (rawRects.isEmpty()) {
            writeDetectionFailure(
                "Android non espone rettangoli DisplayCutout per questa finestra. " +
                    "Usa la calibrazione manuale."
            )
            return
        }

        val locationOnScreen = IntArray(2)
        view.getLocationOnScreen(locationOnScreen)
        val originX = locationOnScreen[0]
        val originY = locationOnScreen[1]
        val rects = rawRects.map { raw ->
            Rect(
                raw.left - originX,
                raw.top - originY,
                raw.right - originX,
                raw.bottom - originY
            )
        }

        val target = chooseLikelyCameraRect(rects, view.width, view.height)
        val density = resources.displayMetrics.density
        val rotation = currentDisplayGeometry().rotation
        val currentCenterX = target.exactCenterX()
        val currentCenterY = target.exactCenterY()

        val (naturalCenterX, naturalCenterY) = currentToNaturalPoint(
            x = currentCenterX,
            y = currentCenterY,
            rotation = rotation,
            currentWidth = view.width.toFloat(),
            currentHeight = view.height.toFloat()
        )

        val naturalWidthPx = when (rotation) {
            Surface.ROTATION_90, Surface.ROTATION_270 -> view.height.toFloat()
            else -> view.width.toFloat()
        }
        val naturalDisplayCenterX = naturalWidthPx / 2f
        val centerSnapTolerancePx = max(24f * density, naturalWidthPx * 0.08f)
        val snapToDisplayCenter =
            abs(naturalCenterX - naturalDisplayCenterX) <= centerSnapTolerancePx
        val savedCenterXPx = if (snapToDisplayCenter) naturalDisplayCenterX else naturalCenterX

        val centerXdp = savedCenterXPx / density
        val centerYdp = (naturalCenterY / density).coerceIn(0f, MAX_VERTICAL_DP)
        val detectedDiameterDp = max(target.width(), target.height()) / density
        val ringDiameterDp = (detectedDiameterDp + 2.5f).coerceIn(4f, 80f)

        val rectDump = rawRects.joinToString(separator = " ; ") {
            "[${it.left},${it.top},${it.right},${it.bottom}]"
        }
        val info = buildString {
            append("Rilevamento overlay: OK\n")
            append("overlay temporaneo=${view.width}x${view.height}px, density=${"%.3f".format(density)}\n")
            append("rotazione=${rotationDegrees(rotation)}°\n")
            append("origine schermo=($originX,$originY)px\n")
            append("cutout=$rectDump\n")
            append("scelto=[${target.left},${target.top},${target.right},${target.bottom}]px\n")
            append("centro corrente=${"%.1f".format(currentCenterX)}, ${"%.1f".format(currentCenterY)} px\n")
            append("centro naturale=${"%.2f".format(centerXdp)}, ${"%.2f".format(centerYdp)} dp")
            if (snapToDisplayCenter) append(" (X agganciata al centro display)")
        }

        prefs.edit()
            .putFloat(Prefs.KEY_CENTER_X_DP, centerXdp)
            .putFloat(Prefs.KEY_CENTER_Y_DP, centerYdp)
            .putFloat(Prefs.KEY_DIAMETER_DP, ringDiameterDp)
            .putString(Prefs.KEY_DETECTION_INFO, info)
            .apply()
    }

    private fun removeTemporaryDetector(view: View) {
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
    }

    private fun chooseLikelyCameraRect(rects: List<Rect>, width: Int, height: Int): Rect {
        val density = resources.displayMetrics.density
        val minDimension = minOf(width, height).coerceAtLeast(1)
        val maxPunchHoleSizePx = max(120f * density, minDimension * 0.35f)

        val valid = rects.filter { it.width() > 0 && it.height() > 0 }.ifEmpty { rects }
        val plausiblePunchHoles = valid.filter {
            max(it.width(), it.height()) <= maxPunchHoleSizePx
        }.ifEmpty { valid }

        fun edgeDistance(rect: Rect): Int = minOf(
            rect.left.coerceAtLeast(0),
            rect.top.coerceAtLeast(0),
            (width - rect.right).coerceAtLeast(0),
            (height - rect.bottom).coerceAtLeast(0)
        )

        return plausiblePunchHoles.minWithOrNull(
            compareBy<Rect> { edgeDistance(it) }
                .thenBy { abs(it.width() - it.height()) }
                .thenBy { max(it.width(), it.height()) }
        ) ?: rects.first()
    }

    private fun createOverlayLayoutParams(width: Int, height: Int): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private data class DisplayGeometry(val width: Int, val height: Int, val rotation: Int)

    @Suppress("DEPRECATION")
    private fun currentDisplayGeometry(): DisplayGeometry {
        val display = windowManager.defaultDisplay
        val rotation = display.rotation
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            DisplayGeometry(bounds.width(), bounds.height(), rotation)
        } else {
            val point = Point()
            display.getRealSize(point)
            DisplayGeometry(point.x, point.y, rotation)
        }
    }

    private fun naturalToCurrentPoint(
        x: Float,
        y: Float,
        rotation: Int,
        currentWidth: Float,
        currentHeight: Float
    ): Pair<Float, Float> = when (rotation) {
        Surface.ROTATION_90 -> Pair(y, currentHeight - x)
        Surface.ROTATION_180 -> Pair(currentWidth - x, currentHeight - y)
        Surface.ROTATION_270 -> Pair(currentWidth - y, x)
        else -> Pair(x, y)
    }

    private fun currentToNaturalPoint(
        x: Float,
        y: Float,
        rotation: Int,
        currentWidth: Float,
        currentHeight: Float
    ): Pair<Float, Float> = when (rotation) {
        Surface.ROTATION_90 -> Pair(currentHeight - y, x)
        Surface.ROTATION_180 -> Pair(currentWidth - x, currentHeight - y)
        Surface.ROTATION_270 -> Pair(y, currentWidth - x)
        else -> Pair(x, y)
    }

    private fun rotationDegrees(rotation: Int): Int = when (rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    private fun writeDetectionFailure(message: String) {
        prefs.edit().putString(Prefs.KEY_DETECTION_INFO, message).apply()
    }

    private fun updateBatteryFromIntent(intent: Intent) {
        val level = intent.getIntExtra("level", -1)
        val scale = intent.getIntExtra("scale", -1)
        if (level < 0 || scale <= 0) return

        val percent = level * 100f / scale.toFloat()
        if (abs(percent - lastBatteryPercent) < 0.001f) return
        lastBatteryPercent = percent
        ringView?.batteryPercent = percent
    }

    private fun registerBatteryReceiver() {
        if (batteryReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(batteryReceiver, filter)
        }
        batteryReceiverRegistered = true
        if (sticky != null) updateBatteryFromIntent(sticky)
    }

    private fun unregisterBatteryReceiver() {
        if (!batteryReceiverRegistered) return
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
        batteryReceiverRegistered = false
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        screenReceiverRegistered = false
    }

    override fun onDestroy() {
        hideOverlay()
        unregisterBatteryReceiver()
        unregisterScreenReceiver()
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
        }
        super.onDestroy()
    }

    companion object {
        private const val MAX_VERTICAL_DP = 120f
        private const val COMPACT_PADDING_DP = 2f
    }
}
