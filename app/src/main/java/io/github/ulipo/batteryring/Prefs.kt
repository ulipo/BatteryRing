package io.github.ulipo.batteryring

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

object Prefs {
    const val FILE = "battery_ring_preferences"

    const val KEY_ENABLED = "enabled"
    const val KEY_CENTER_X_DP = "center_x_dp"
    const val KEY_CENTER_Y_DP = "center_y_dp"
    const val KEY_DIAMETER_DP = "diameter_dp"
    const val KEY_THICKNESS_DP = "thickness_dp"
    const val KEY_COLOR = "color"
    const val KEY_START_ANGLE_DEG = "start_angle_deg"

    // The Activity writes a new value here to ask the already-running
    // AccessibilityService to detect the cutout in the overlay's own coordinates.
    const val KEY_DETECT_REQUEST_ID = "detect_request_id"
    const val KEY_DETECTION_INFO = "detection_info"

    const val DEFAULT_DIAMETER_DP = 13f
    const val DEFAULT_THICKNESS_DP = 2.5f
    const val DEFAULT_START_ANGLE_DEG = 0f
    val DEFAULT_COLOR: Int = Color.rgb(0, 230, 118)

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
