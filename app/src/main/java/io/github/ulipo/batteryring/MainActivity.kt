package io.github.ulipo.batteryring

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val positionStepDp = 0.25f
    private lateinit var prefs: SharedPreferences
    private lateinit var enabledSwitch: Switch
    private lateinit var serviceStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var preview: RingPreviewView
    private lateinit var colorInput: EditText

    private lateinit var xSeek: SeekBar
    private lateinit var ySeek: SeekBar
    private lateinit var diameterSeek: SeekBar
    private lateinit var thicknessSeek: SeekBar
    private lateinit var xLabel: TextView
    private lateinit var yLabel: TextView
    private lateinit var diameterLabel: TextView
    private lateinit var thicknessLabel: TextView

    private var batteryReceiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra("level", -1)
            val scale = intent.getIntExtra("scale", -1)
            if (level >= 0 && scale > 0) {
                val percent = level * 100f / scale.toFloat()
                batteryStatus.text = "Batteria: ${percent.toInt()}%  •  arco: ${"%.1f".format(360f * percent / 100f)}°"
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
        preview.invalidate()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        scroll.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        root.addView(TextView(this).apply {
            text = "BatteryRing"
            textSize = 30f
            setTextColor(Color.rgb(18, 20, 24))
        })
        root.addView(TextView(this).apply {
            text = "Anello della batteria attorno alla fotocamera frontale"
            textSize = 15f
            setPadding(0, dp(4), 0, dp(16))
        })

        serviceStatus = TextView(this).apply {
            textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(serviceStatus, matchWrap())

        root.addView(Button(this).apply {
            text = "Apri impostazioni Accessibilità"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }, matchWrap())

        enabledSwitch = Switch(this).apply {
            text = "Indicatore attivo"
            textSize = 17f
            setPadding(0, dp(10), 0, dp(10))
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(Prefs.KEY_ENABLED, checked).apply()
            }
        }
        root.addView(enabledSwitch, matchWrap())

        batteryStatus = TextView(this).apply {
            text = "Batteria: —"
            textSize = 16f
            setPadding(0, dp(6), 0, dp(8))
        }
        root.addView(batteryStatus, matchWrap())

        preview = RingPreviewView(this).apply {
            minimumHeight = dp(150)
        }
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)).apply {
            bottomMargin = dp(18)
        })

        root.addView(sectionTitle("Posizione e dimensioni"))

        val display = resources.displayMetrics
        val widthDp = (display.widthPixels / display.density).toInt().coerceAtLeast(1)
        val heightDp = (display.heightPixels / display.density).toInt().coerceAtLeast(1)

        xLabel = valueLabel("")
        root.addView(xLabel)
        xSeek = SeekBar(this).apply {
            max = (widthDp / positionStepDp).roundToInt().coerceAtLeast(1)
            setOnSeekBarChangeListener(simpleSeekListener { progress ->
                val value = progressToPositionDp(progress)
                prefs.edit().putFloat(Prefs.KEY_CENTER_X_DP, value).apply()
                xLabel.text = "Posizione X: ${formatDp(value)} dp"
            })
        }
        root.addView(xSeek, matchWrap())

        yLabel = valueLabel("")
        root.addView(yLabel)
        ySeek = SeekBar(this).apply {
            max = (heightDp / positionStepDp).roundToInt().coerceAtLeast(1)
            setOnSeekBarChangeListener(simpleSeekListener { progress ->
                val value = progressToPositionDp(progress)
                prefs.edit().putFloat(Prefs.KEY_CENTER_Y_DP, value).apply()
                yLabel.text = "Posizione Y: ${formatDp(value)} dp"
            })
        }
        root.addView(ySeek, matchWrap())

        diameterLabel = valueLabel("")
        root.addView(diameterLabel)
        diameterSeek = SeekBar(this).apply {
            max = 76
            setOnSeekBarChangeListener(simpleSeekListener { raw ->
                val value = raw + 4
                prefs.edit().putFloat(Prefs.KEY_DIAMETER_DP, value.toFloat()).apply()
                diameterLabel.text = "Diametro anello: ${value} dp"
            })
        }
        root.addView(diameterSeek, matchWrap())

        thicknessLabel = valueLabel("")
        root.addView(thicknessLabel)
        thicknessSeek = SeekBar(this).apply {
            max = 38
            setOnSeekBarChangeListener(simpleSeekListener { raw ->
                val value = (raw + 2) / 2f
                prefs.edit().putFloat(Prefs.KEY_THICKNESS_DP, value).apply()
                thicknessLabel.text = "Spessore: ${"%.1f".format(value)} dp"
            })
        }
        root.addView(thicknessSeek, matchWrap())

        root.addView(Button(this).apply {
            text = "Rileva automaticamente il foro"
            setOnClickListener { detectCameraCutout() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
            bottomMargin = dp(18)
        })

        root.addView(sectionTitle("Colore"))
        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        colorInput = EditText(this).apply {
            hint = "#00E676"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
        }
        colorRow.addView(colorInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        colorRow.addView(Button(this).apply {
            text = "Applica"
            setOnClickListener { applyTypedColor() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(8)
        })
        root.addView(colorRow, matchWrap())

        val presets = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, dp(14))
        }
        val presetColors = listOf(
            Color.rgb(0, 230, 118),
            Color.CYAN,
            Color.YELLOW,
            Color.rgb(255, 145, 0),
            Color.RED,
            Color.MAGENTA
        )
        presetColors.forEach { color ->
            presets.addView(Button(this).apply {
                text = "●"
                textSize = 24f
                setTextColor(color)
                setOnClickListener {
                    prefs.edit().putInt(Prefs.KEY_COLOR, color).apply()
                    colorInput.setText(String.format("#%06X", 0xFFFFFF and color))
                    preview.invalidate()
                }
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        root.addView(presets, matchWrap())

        root.addView(TextView(this).apply {
            text = "Il servizio di accessibilità viene usato esclusivamente per poter disegnare l'anello sopra la barra di stato. BatteryRing non legge il contenuto delle finestre e l'overlay non riceve tocchi."
            textSize = 13f
            setPadding(0, dp(12), 0, 0)
        })

        return scroll
    }

    private fun syncControlsFromPrefs() {
        val density = resources.displayMetrics.density
        val defaultX = resources.displayMetrics.widthPixels / density / 2f
        enabledSwitch.isChecked = prefs.getBoolean(Prefs.KEY_ENABLED, true)

        val x = prefs.getFloat(Prefs.KEY_CENTER_X_DP, defaultX)
        val y = prefs.getFloat(Prefs.KEY_CENTER_Y_DP, 16f)
        val diameter = prefs.getFloat(Prefs.KEY_DIAMETER_DP, Prefs.DEFAULT_DIAMETER_DP)
        val thickness = prefs.getFloat(Prefs.KEY_THICKNESS_DP, Prefs.DEFAULT_THICKNESS_DP)

        xSeek.progress = positionToProgress(x).coerceIn(0, xSeek.max)
        ySeek.progress = positionToProgress(y).coerceIn(0, ySeek.max)
        diameterSeek.progress = (diameter.toInt() - 4).coerceIn(0, diameterSeek.max)
        thicknessSeek.progress = (thickness * 2f - 2f).toInt().coerceIn(0, thicknessSeek.max)

        xLabel.text = "Posizione X: ${formatDp(x)} dp"
        yLabel.text = "Posizione Y: ${formatDp(y)} dp"
        diameterLabel.text = "Diametro anello: ${diameter.toInt()} dp"
        thicknessLabel.text = "Spessore: ${"%.1f".format(thickness)} dp"

        val color = prefs.getInt(Prefs.KEY_COLOR, Prefs.DEFAULT_COLOR)
        colorInput.setText(String.format("#%06X", 0xFFFFFF and color))
        preview.invalidate()
    }

    private fun detectCameraCutout() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            toast("Il rilevamento automatico richiede Android 9 o successivo.")
            return
        }

        val cutout = window.decorView.rootWindowInsets?.displayCutout
        val rects = cutout?.boundingRects.orEmpty()
        if (rects.isEmpty()) {
            toast("Android non espone un DisplayCutout su questo dispositivo. Usa gli slider per la calibrazione manuale.")
            return
        }

        val width = resources.displayMetrics.widthPixels
        val target = chooseLikelyCameraRect(rects, width)
        val density = resources.displayMetrics.density
        val centerXdp = target.exactCenterX() / density
        val centerYdp = target.exactCenterY() / density
        val detectedDiameterDp = max(target.width(), target.height()) / density
        val ringDiameterDp = (detectedDiameterDp + 2.5f).coerceIn(4f, 80f)

        prefs.edit()
            .putFloat(Prefs.KEY_CENTER_X_DP, centerXdp)
            .putFloat(Prefs.KEY_CENTER_Y_DP, centerYdp)
            .putFloat(Prefs.KEY_DIAMETER_DP, ringDiameterDp)
            .apply()

        syncControlsFromPrefs()
        toast("Foro rilevato. Regola gli slider se l'anello non è perfettamente centrato.")
    }

    private fun chooseLikelyCameraRect(rects: List<Rect>, displayWidth: Int): Rect {
        val highestTop = rects.minOf { it.top }
        val topCandidates = rects.filter { it.top <= highestTop + dp(12) }

        val plausiblePunchHoles = topCandidates.filter {
            it.width() < displayWidth * 0.35f && it.height() > 0
        }

    return plausiblePunchHoles.minWithOrNull(
        compareBy<Rect> { abs(it.exactCenterX() - displayWidth / 2f) }
            .thenBy { abs(it.width() - it.height()) }
            .thenBy { max(it.width(), it.height()) }
    ) ?: topCandidates.minWithOrNull(
        compareBy<Rect> { abs(it.exactCenterX() - displayWidth / 2f) }
            .thenBy { max(it.width(), it.height()) }
    ) ?: rects.first()
}

    private fun applyTypedColor() {
        val raw = colorInput.text.toString().trim()
        try {
            val normalized = if (raw.startsWith("#")) raw else "#$raw"
            val color = Color.parseColor(normalized)
            prefs.edit().putInt(Prefs.KEY_COLOR, color).apply()
            colorInput.setText(String.format("#%06X", 0xFFFFFF and color))
            preview.invalidate()
        } catch (_: IllegalArgumentException) {
            toast("Colore non valido. Usa il formato #RRGGBB, ad esempio #00E676.")
        }
    }

    private fun updateServiceStatus() {
        val enabled = isAccessibilityServiceEnabled()
        serviceStatus.text = if (enabled) {
            "Servizio Accessibilità: ATTIVO"
        } else {
            "Servizio Accessibilità: DISATTIVATO — abilitalo per mostrare l'anello sopra la barra di stato"
        }
        serviceStatus.setTextColor(if (enabled) Color.rgb(0, 120, 70) else Color.rgb(180, 45, 45))
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

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        setTextColor(Color.rgb(18, 20, 24))
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun valueLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setPadding(0, dp(7), 0, 0)
    }

    private fun simpleSeekListener(onChanged: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChanged(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun positionToProgress(valueDp: Float): Int = (valueDp / positionStepDp).roundToInt()

    private fun progressToPositionDp(progress: Int): Float = progress * positionStepDp

    private fun formatDp(value: Float): String = String.format("%.2f", value)

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
