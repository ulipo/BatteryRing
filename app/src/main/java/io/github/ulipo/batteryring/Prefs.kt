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

    const val DEFAULT_DIAMETER_DP = 13f
    const val DEFAULT_THICKNESS_DP = 2.5f
    val DEFAULT_COLOR: Int = Color.rgb(0, 230, 118)

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
