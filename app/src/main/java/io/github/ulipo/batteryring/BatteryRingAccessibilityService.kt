package io.github.ulipo.batteryring

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs
import kotlin.math.max

class BatteryRingAccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var windowManager: WindowManager
    private var ringView: BatteryRingView? = null
    private var overlayAttached = false
    private var receiverRegistered = false
    private var lastBatteryPercent = 100f

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra("level", -1)
            val scale = intent.getIntExtra("scale", -1)
            if (level >= 0 && scale > 0) {
                lastBatteryPercent = level * 100f / scale.toFloat()
                ringView?.batteryPercent = lastBatteryPercent
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs.get(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs.registerOnSharedPreferenceChangeListener(this)
        registerBatteryReceiver()
        refreshOverlayState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Deliberately ignored. BatteryRing never inspects app or window contents.
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Recreate the full-screen overlay so its bounds and DisplayCutout belong
        // to the new rotation. BatteryRingView then transforms the saved physical
        // camera position from natural coordinates to the new screen coordinates.
        if (overlayAttached) {
            hideOverlay()
            showOverlay()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Prefs.KEY_ENABLED -> refreshOverlayState()
            Prefs.KEY_DETECT_REQUEST_ID -> detectCameraCutoutFromOverlay()
            else -> ringView?.invalidate()
        }
    }

    private fun refreshOverlayState() {
        if (prefs.getBoolean(Prefs.KEY_ENABLED, true)) {
            showOverlay()
        } else {
            hideOverlay()
        }
    }

    private fun showOverlay() {
        if (overlayAttached) return

        val view = BatteryRingView(this).apply {
            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
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

        try {
            view.batteryPercent = lastBatteryPercent
            windowManager.addView(view, params)
            ringView = view
            overlayAttached = true
        } catch (_: Exception) {
            ringView = null
            overlayAttached = false
        }
    }

    private fun hideOverlay() {
        val view = ringView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
            // The system may already have detached it while the service was stopping.
        }
        ringView = null
        overlayAttached = false
    }

    /**
     * Detects the physical cutout from the same accessibility-overlay window used
     * to draw the ring. This avoids mixing Activity coordinates with overlay
     * coordinates, which can introduce an offset on some devices.
     */
    private fun detectCameraCutoutFromOverlay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            writeDetectionFailure("Rilevamento non disponibile: serve Android 9 o successivo.")
            return
        }

        val view = ringView
        if (view == null || !overlayAttached) {
            writeDetectionFailure("Overlay non attivo. Attiva 'Indicatore attivo' e riprova.")
            return
        }

        view.post {
            val cutout = view.rootWindowInsets?.displayCutout
            val rawRects = cutout?.boundingRects.orEmpty()
            if (rawRects.isEmpty()) {
                writeDetectionFailure(
                    "Android non espone rettangoli DisplayCutout per questa finestra. " +
                        "Usa la calibrazione manuale."
                )
                return@post
            }

            val locationOnScreen = IntArray(2)
            view.getLocationOnScreen(locationOnScreen)
            val originX = locationOnScreen[0]
            val originY = locationOnScreen[1]

            // DisplayCutout uses screen/window coordinates. Convert every rectangle
            // to this overlay View's local coordinate system before using it.
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
            val rotation = view.display?.rotation ?: Surface.ROTATION_0
            val currentCenterX = target.exactCenterX()
            val currentCenterY = target.exactCenterY()

            // Convert the point back to natural-display coordinates before storing
            // it. The same saved calibration can then be transformed correctly at
            // ROTATION_0/90/180/270 by BatteryRingView.
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

            // OEMs sometimes expose a protected cutout rectangle that is slightly
            // asymmetric around a centred punch-hole. Snap X only when the detected
            // natural X is already close enough to the physical display centre.
            val centerSnapTolerancePx = max(24f * density, naturalWidthPx * 0.08f)
            val snapToDisplayCenter =
                abs(naturalCenterX - naturalDisplayCenterX) <= centerSnapTolerancePx
            val savedCenterXPx =
                if (snapToDisplayCenter) naturalDisplayCenterX else naturalCenterX

            val centerXdp = savedCenterXPx / density
            val centerYdp = (naturalCenterY / density).coerceIn(0f, MAX_VERTICAL_DP)
            val detectedDiameterDp = max(target.width(), target.height()) / density
            val ringDiameterDp = (detectedDiameterDp + 2.5f).coerceIn(4f, 80f)

            val rectDump = rawRects.joinToString(separator = " ; ") {
                "[${it.left},${it.top},${it.right},${it.bottom}]"
            }
            val info = buildString {
                append("Rilevamento overlay: OK\n")
                append("overlay=${view.width}x${view.height}px, density=${"%.3f".format(density)}\n")
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
    }

    private fun chooseLikelyCameraRect(rects: List<Rect>, width: Int, height: Int): Rect {
        val density = resources.displayMetrics.density
        val minDimension = minOf(width, height).coerceAtLeast(1)
        val maxPunchHoleSizePx = max(120f * density, minDimension * 0.35f)

        val valid = rects.filter { it.width() > 0 && it.height() > 0 }
            .ifEmpty { rects }

        val plausiblePunchHoles = valid.filter {
            max(it.width(), it.height()) <= maxPunchHoleSizePx
        }.ifEmpty { valid }

        fun edgeDistance(rect: Rect): Int = minOf(
            rect.left.coerceAtLeast(0),
            rect.top.coerceAtLeast(0),
            (width - rect.right).coerceAtLeast(0),
            (height - rect.bottom).coerceAtLeast(0)
        )

        // Works in both portrait and landscape: the camera cutout remains near one
        // of the physical display edges after rotation, not necessarily the top.
        return plausiblePunchHoles.minWithOrNull(
            compareBy<Rect> { edgeDistance(it) }
                .thenBy { abs(it.width() - it.height()) }
                .thenBy { max(it.width(), it.height()) }
        ) ?: rects.first()
    }

    private fun currentToNaturalPoint(
        x: Float,
        y: Float,
        rotation: Int,
        currentWidth: Float,
        currentHeight: Float
    ): Pair<Float, Float> = when (rotation) {
        // Inverse of BatteryRingView.naturalToCurrentPoint().
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

    private fun registerBatteryReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(batteryReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onDestroy() {
        hideOverlay()
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
        }
        if (receiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    companion object {
        private const val MAX_VERTICAL_DP = 120f
    }
}
