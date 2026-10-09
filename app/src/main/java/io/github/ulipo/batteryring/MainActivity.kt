// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.ulipo.batteryring

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val positionStepDp = 0.25f
    private val diameterStepDp = 0.25f
    private val borderStepDp = 0.25f
    private val startAngleStep = 1f
    private val maxVerticalPositionDp = 120f
    private val minDiameterDp = 4f
    private val maxDiameterDp = 80f
    private val maxBorderThicknessDp = 16f

    private lateinit var prefs: SharedPreferences
    private lateinit var enabledSwitch: MaterialSwitch
    private lateinit var serviceStatus: MaterialTextView
    private lateinit var batteryStatus: MaterialTextView
    private lateinit var detectionStatus: MaterialTextView
    private lateinit var preview: RingPreviewView
    private lateinit var fillColorValue: MaterialTextView
    private lateinit var borderColorValue: MaterialTextView
    private lateinit var fillColorSwatch: MaterialCardView
    private lateinit var borderColorSwatch: MaterialCardView

    private lateinit var xSlider: Slider
    private lateinit var ySlider: Slider
    private lateinit var diameterSlider: Slider
    private lateinit var borderThicknessSlider: Slider
    private lateinit var startAngleSlider: Slider

    private lateinit var xLabel: MaterialTextView
    private lateinit var yLabel: MaterialTextView
    private lateinit var diameterLabel: MaterialTextView
    private lateinit var borderThicknessLabel: MaterialTextView
    private lateinit var startAngleLabel: MaterialTextView

    private var batteryReceiverRegistered = false
    private var naturalWidthDp = 1f

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra("level", -1)
            val scale = intent.getIntExtra("scale", -1)
            if (level >= 0 && scale > 0) {
                val percent = level * 100f / scale.toFloat()
                batteryStatus.text =
                    "Batteria: ${percent.toInt()}%  •  settore: ${"%.1f".format(360f * percent / 100f)}°"
                preview.batteryPercent = percent
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        setContentView(buildUi())
        syncControlsFromPrefs()
    }

    override fun onStart() {
        super.onStart()
        updateServiceStatus()
        registerBatteryReceiver()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        syncControlsFromPrefs()
    }

    override fun onStop() {
        if (batteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
            batteryReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (::preview.isInitialized) preview.invalidate()
        when (key) {
            Prefs.KEY_CENTER_X_DP,
            Prefs.KEY_CENTER_Y_DP,
            Prefs.KEY_DIAMETER_DP,
            Prefs.KEY_BORDER_THICKNESS_DP,
            Prefs.KEY_START_ANGLE_DEG,
            Prefs.KEY_COLOR,
            Prefs.KEY_BORDER_COLOR,
            Prefs.KEY_DETECTION_INFO,
            Prefs.KEY_ENABLED -> syncControlsFromPrefs()
        }
    }

    private fun buildUi(): View {
        naturalWidthDp = naturalDisplayWidthDp().coerceAtLeast(1f)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(NordPalette.Nord0)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(NordPalette.Nord0)
        }
        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(MaterialToolbar(this).apply {
            title = "BatteryRing"
            subtitle = "Indicatore batteria attorno alla fotocamera"
            setBackgroundColor(NordPalette.Nord0)
            setTitleTextColor(NordPalette.Nord6)
            setSubtitleTextColor(NordPalette.Nord4)
        }, matchWrap())

        serviceStatus = supportingText("")
        root.addView(sectionCard("Stato") {
            addView(serviceStatus)
        }, cardParams())

        root.addView(sectionCard("Controlli rapidi") {
            addView(MaterialButton(context).apply {
                text = "Apri impostazioni Accessibilità"
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }, matchWrap())

            enabledSwitch = MaterialSwitch(context).apply {
                text = "Indicatore attivo"
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(Prefs.KEY_ENABLED, checked).apply()
                }
            }
            addView(enabledSwitch, matchWrap())
        }, cardParams())

        root.addView(sectionCard("Anteprima") {
            batteryStatus = bodyText("Batteria: —")
            addView(batteryStatus)

            preview = RingPreviewView(context).apply { minimumHeight = dp(180) }
            addView(
                preview,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(180)
                ).apply { topMargin = dp(12) }
            )
        }, cardParams())

        root.addView(sectionCard("Posizione e rilevamento") {
            xLabel = labelText("")
            addView(xLabel)
            xSlider = createSlider(0f, naturalWidthDp, positionStepDp) {
                setFloatPref(Prefs.KEY_CENTER_X_DP, it.coerceIn(0f, naturalWidthDp))
            }
            addView(xSlider, matchWrap())
            addView(fineAdjustButtons("−0,25 dp", "+0,25 dp",
                onMinus = {
                    nudgeFloatPref(Prefs.KEY_CENTER_X_DP, -positionStepDp, 0f, naturalWidthDp)
                },
                onPlus = {
                    nudgeFloatPref(Prefs.KEY_CENTER_X_DP, positionStepDp, 0f, naturalWidthDp)
                }
            ))

            yLabel = labelText("")
            addView(yLabel)
            ySlider = createSlider(0f, maxVerticalPositionDp, positionStepDp) {
                setFloatPref(Prefs.KEY_CENTER_Y_DP, it.coerceIn(0f, maxVerticalPositionDp))
            }
            addView(ySlider, matchWrap())
            addView(fineAdjustButtons("−0,25 dp", "+0,25 dp",
                onMinus = {
                    nudgeFloatPref(Prefs.KEY_CENTER_Y_DP, -positionStepDp, 0f, maxVerticalPositionDp)
                },
                onPlus = {
                    nudgeFloatPref(Prefs.KEY_CENTER_Y_DP, positionStepDp, 0f, maxVerticalPositionDp)
                }
            ))

            addView(MaterialButton(context).apply {
                text = "Rileva automaticamente il foro"
                setOnClickListener { requestCameraCutoutDetection() }
            }, matchWrap())

            detectionStatus = supportingText("Nessun rilevamento automatico eseguito.").apply {
                setTextIsSelectable(true)
            }
            addView(detectionStatus)
        }, cardParams())

        root.addView(sectionCard("Aspetto") {
            diameterLabel = labelText("")
            addView(diameterLabel)
            diameterSlider = createSlider(minDiameterDp, maxDiameterDp, diameterStepDp) {
                setFloatPref(Prefs.KEY_DIAMETER_DP, it.coerceIn(minDiameterDp, maxDiameterDp))
            }
            addView(diameterSlider, matchWrap())
            addView(fineAdjustButtons("−0,25 dp", "+0,25 dp",
                onMinus = {
                    nudgeFloatPref(Prefs.KEY_DIAMETER_DP, -diameterStepDp, minDiameterDp, maxDiameterDp)
                },
                onPlus = {
                    nudgeFloatPref(Prefs.KEY_DIAMETER_DP, diameterStepDp, minDiameterDp, maxDiameterDp)
                }
            ))

            borderThicknessLabel = labelText("")
            addView(borderThicknessLabel)
            borderThicknessSlider = createSlider(0f, maxBorderThicknessDp, borderStepDp) {
                setFloatPref(Prefs.KEY_BORDER_THICKNESS_DP, it.coerceIn(0f, maxBorderThicknessDp))
            }
            addView(borderThicknessSlider, matchWrap())
            addView(fineAdjustButtons("−0,25 dp", "+0,25 dp",
                onMinus = {
                    nudgeFloatPref(
                        Prefs.KEY_BORDER_THICKNESS_DP,
                        -borderStepDp,
                        0f,
                        maxBorderThicknessDp
                    )
                },
                onPlus = {
                    nudgeFloatPref(
                        Prefs.KEY_BORDER_THICKNESS_DP,
                        borderStepDp,
                        0f,
                        maxBorderThicknessDp
                    )
                }
            ))

            startAngleLabel = labelText("")
            addView(startAngleLabel)
            startAngleSlider = createSlider(0f, 359f, startAngleStep) {
                setFloatPref(Prefs.KEY_START_ANGLE_DEG, it.coerceIn(0f, 359f))
            }
            addView(startAngleSlider, matchWrap())
            addView(fineAdjustButtons("−1°", "+1°",
                onMinus = { nudgeAngle(-startAngleStep) },
                onPlus = { nudgeAngle(startAngleStep) }
            ))
        }, cardParams())

        root.addView(colorCard(
            title = "Riempimento",
            prefKey = Prefs.KEY_COLOR,
            onReady = { swatch, value ->
                fillColorSwatch = swatch
                fillColorValue = value
            }
        ), cardParams())

        root.addView(colorCard(
            title = "Bordo",
            prefKey = Prefs.KEY_BORDER_COLOR,
            onReady = { swatch, value ->
                borderColorSwatch = swatch
                borderColorValue = value
            }
        ), cardParams())

        root.addView(sectionCard("Informazioni") {
            addView(supportingText(
                "Il servizio di accessibilità viene usato esclusivamente per disegnare " +
                    "l'indicatore sopra la barra di stato. BatteryRing non legge il contenuto " +
                    "delle finestre e l'overlay non riceve tocchi."
            ))
        }, cardParams())

        return scroll
    }

    private fun sectionCard(title: String, contentBuilder: LinearLayout.() -> Unit): MaterialCardView {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(MaterialTextView(context).apply {
                text = title
                textSize = 20f
                setTextColor(NordPalette.Nord6)
                setPadding(0, 0, 0, dp(12))
            })
            contentBuilder()
        }

        return MaterialCardView(this).apply {
            radius = dp(22).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = NordPalette.Nord3
            setCardBackgroundColor(NordPalette.Nord1)
            setContentPadding(dp(16), dp(16), dp(16), dp(16))
            addView(content, matchWrap())
        }
    }

    private fun colorCard(
        title: String,
        prefKey: String,
        onReady: (MaterialCardView, MaterialTextView) -> Unit
    ): MaterialCardView {
        val presetColors = listOf(
            Color.WHITE,
            NordPalette.Nord14,
            NordPalette.Nord8,
            NordPalette.Nord13,
            NordPalette.Nord12,
            NordPalette.Nord11,
            NordPalette.Nord15
        )

        return sectionCard(title) {
            val current = prefs.getInt(
                prefKey,
                if (prefKey == Prefs.KEY_COLOR) Prefs.DEFAULT_COLOR else Prefs.DEFAULT_BORDER_COLOR
            )

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val swatch = MaterialCardView(context).apply {
                radius = dp(22).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                strokeColor = NordPalette.Nord3
                setCardBackgroundColor(current)
            }
            row.addView(swatch, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginEnd = dp(12)
            })

            val valueText = MaterialTextView(context).apply {
                text = colorHex(current)
                textSize = 16f
                setTextColor(NordPalette.Nord5)
            }
            row.addView(valueText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            row.addView(MaterialButton(context).apply {
                text = "Scegli colore"
                setOnClickListener { showColorPicker(title, prefKey) }
            })
            addView(row, matchWrap())

            onReady(swatch, valueText)

            val scroller = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
            }
            val presetRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }
            presetColors.forEach { color ->
                presetRow.addView(MaterialButton(context).apply {
                    text = ""
                    minWidth = 0
                    minimumWidth = 0
                    minimumHeight = 0
                    insetTop = 0
                    insetBottom = 0
                    cornerRadius = dp(20)
                    strokeWidth = dp(1)
                    strokeColor = ColorStateList.valueOf(NordPalette.Nord3)
                    backgroundTintList = ColorStateList.valueOf(color)
                    setOnClickListener { prefs.edit().putInt(prefKey, color).apply() }
                }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) })
            }
            scroller.addView(
                presetRow,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(scroller, matchWrap())
        }
    }

    private fun showColorPicker(title: String, prefKey: String) {
        val current = prefs.getInt(
            prefKey,
            if (prefKey == Prefs.KEY_COLOR) Prefs.DEFAULT_COLOR else Prefs.DEFAULT_BORDER_COLOR
        )
        val picker = NordColorPickerView(this).apply {
            setColor(current)
        }
        val valueLabel = MaterialTextView(this).apply {
            text = colorHex(current)
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(NordPalette.Nord5)
            setPadding(0, dp(8), 0, 0)
        }
        picker.onColorChanged = { color -> valueLabel.text = colorHex(color) }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            addView(
                picker,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(300)
                )
            )
            addView(valueLabel, matchWrap())
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Colore $title")
            .setView(content)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Applica") { _, _ ->
                prefs.edit().putInt(prefKey, picker.selectedColor).apply()
            }
            .show()
    }

    private fun syncControlsFromPrefs() {
        naturalWidthDp = naturalDisplayWidthDp().coerceAtLeast(1f)
        if (::enabledSwitch.isInitialized) {
            enabledSwitch.isChecked = prefs.getBoolean(Prefs.KEY_ENABLED, true)
        }

        val x = prefs.getFloat(Prefs.KEY_CENTER_X_DP, naturalWidthDp / 2f).coerceIn(0f, naturalWidthDp)
        val y = prefs.getFloat(Prefs.KEY_CENTER_Y_DP, 16f).coerceIn(0f, maxVerticalPositionDp)
        val diameter = prefs.getFloat(Prefs.KEY_DIAMETER_DP, Prefs.DEFAULT_DIAMETER_DP)
            .coerceIn(minDiameterDp, maxDiameterDp)
        val borderThickness = prefs.getFloat(
            Prefs.KEY_BORDER_THICKNESS_DP,
            Prefs.DEFAULT_BORDER_THICKNESS_DP
        ).coerceIn(0f, maxBorderThicknessDp)
        val startAngle = prefs.getFloat(
            Prefs.KEY_START_ANGLE_DEG,
            Prefs.DEFAULT_START_ANGLE_DEG
        ).coerceIn(0f, 359f)

        if (::xSlider.isInitialized) {
            xSlider.valueTo = naturalWidthDp
            xSlider.value = x
            ySlider.value = y
            diameterSlider.value = diameter
            borderThicknessSlider.value = borderThickness
            startAngleSlider.value = startAngle
        }

        if (::xLabel.isInitialized) {
            xLabel.text = "Posizione X: ${formatDp(x)} dp"
            yLabel.text = "Posizione Y: ${formatDp(y)} dp (solo fascia superiore)"
            diameterLabel.text = "Diametro: ${formatDp(diameter)} dp"
            borderThicknessLabel.text = "Spessore bordo: ${formatDp(borderThickness)} dp"
            startAngleLabel.text = "Origine settore: ${startAngle.roundToInt()}° ${angleClockLabel(startAngle)}"
        }

        if (::fillColorValue.isInitialized) {
            val fillColor = prefs.getInt(Prefs.KEY_COLOR, Prefs.DEFAULT_COLOR)
            fillColorValue.text = colorHex(fillColor)
            fillColorSwatch.setCardBackgroundColor(fillColor)
        }
        if (::borderColorValue.isInitialized) {
            val borderColor = prefs.getInt(Prefs.KEY_BORDER_COLOR, Prefs.DEFAULT_BORDER_COLOR)
            borderColorValue.text = colorHex(borderColor)
            borderColorSwatch.setCardBackgroundColor(borderColor)
        }

        updateDetectionStatus()
        if (::preview.isInitialized) preview.invalidate()
    }

    private fun createSlider(
        valueFrom: Float,
        valueTo: Float,
        @Suppress("UNUSED_PARAMETER") step: Float,
        onChanged: (Float) -> Unit
    ) = Slider(this).apply {
        this.valueFrom = valueFrom
        this.valueTo = valueTo
        // Keep Material sliders continuous. Display dimensions and values detected from
        // DisplayCutout are often fractional dp values and therefore are not guaranteed
        // to align with a discrete 0.25 dp grid. Material validates discrete sliders at
        // runtime and rejects ranges/values that are not exact multiples of stepSize.
        // Fine adjustment remains deterministic through the +/- buttons.
        this.stepSize = 0f
        addOnChangeListener { _, value, fromUser ->
            if (fromUser) onChanged(value)
        }
    }

    private fun requestCameraCutoutDetection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            toast("Il rilevamento automatico richiede Android 9 o successivo.")
            return
        }
        if (!isAccessibilityServiceEnabled()) {
            toast("Abilita prima il servizio di accessibilità BatteryRing.")
            return
        }
        if (!prefs.getBoolean(Prefs.KEY_ENABLED, true)) {
            toast("Attiva prima l'indicatore: il rilevamento usa la finestra dell'overlay.")
            return
        }

        detectionStatus.text = "Rilevamento richiesto all'overlay…"
        prefs.edit()
            .putLong(Prefs.KEY_DETECT_REQUEST_ID, SystemClock.elapsedRealtimeNanos())
            .apply()
    }

    private fun updateDetectionStatus() {
        if (::detectionStatus.isInitialized) {
            detectionStatus.text = prefs.getString(Prefs.KEY_DETECTION_INFO, null)
                ?: "Nessun rilevamento automatico eseguito."
        }
    }

    private fun setFloatPref(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    private fun nudgeFloatPref(key: String, delta: Float, min: Float, max: Float) {
        val default = when (key) {
            Prefs.KEY_CENTER_X_DP -> naturalWidthDp / 2f
            Prefs.KEY_CENTER_Y_DP -> 16f
            Prefs.KEY_DIAMETER_DP -> Prefs.DEFAULT_DIAMETER_DP
            Prefs.KEY_BORDER_THICKNESS_DP -> Prefs.DEFAULT_BORDER_THICKNESS_DP
            else -> 0f
        }
        val newValue = (prefs.getFloat(key, default) + delta).coerceIn(min, max)
        prefs.edit().putFloat(key, newValue).apply()
    }

    private fun nudgeAngle(delta: Float) {
        val current = prefs.getFloat(Prefs.KEY_START_ANGLE_DEG, Prefs.DEFAULT_START_ANGLE_DEG)
        val value = (((current + delta) % 360f) + 360f) % 360f
        prefs.edit().putFloat(Prefs.KEY_START_ANGLE_DEG, value).apply()
    }

    private fun updateServiceStatus() {
        val enabled = isAccessibilityServiceEnabled()
        serviceStatus.text = if (enabled) {
            "Servizio Accessibilità: ATTIVO"
        } else {
            "Servizio Accessibilità: DISATTIVATO — abilitalo per mostrare l'indicatore sopra la barra di stato"
        }
        serviceStatus.setTextColor(if (enabled) NordPalette.Nord14 else NordPalette.Nord11)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, BatteryRingAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expected }
    }

    private fun registerBatteryReceiver() {
        if (batteryReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(batteryReceiver, filter)
        }
        batteryReceiverRegistered = true
    }

    private fun bodyText(text: String) = MaterialTextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(NordPalette.Nord5)
    }

    private fun supportingText(text: String) = MaterialTextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(NordPalette.Nord4)
    }

    private fun labelText(text: String) = MaterialTextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(NordPalette.Nord5)
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun fineAdjustButtons(
        minusLabel: String,
        plusLabel: String,
        onMinus: () -> Unit,
        onPlus: () -> Unit
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, 0, 0, dp(6))
        addView(
            MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = minusLabel
                setOnClickListener { onMinus() }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )
        addView(
            MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = plusLabel
                setOnClickListener { onPlus() }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        )
    }

    private fun angleClockLabel(degrees: Float): String = when (degrees.roundToInt()) {
        0 -> "(ore 12)"
        90 -> "(ore 3)"
        180 -> "(ore 6)"
        270 -> "(ore 9)"
        else -> ""
    }

    private fun naturalDisplayWidthDp(): Float {
        val metrics = resources.displayMetrics
        return min(metrics.widthPixels, metrics.heightPixels) / metrics.density
    }

    private fun colorHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    private fun formatDp(value: Float): String = String.format("%.2f", value)

    private fun matchWrap() = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun cardParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(12)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
