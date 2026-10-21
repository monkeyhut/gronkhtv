package org.streamarchiv.tv.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.media3.ui.PlayerView

/**
 * Wie das Handy gehalten wird, aus den Grad des OrientationEventListener: true quer, false hochkant, null flach liegend
 * oder schräg dazwischen. Die 30-Grad-Lücken verhindern, dass ein leicht schräg gehaltenes Gerät hin und her springt.
 */
fun heldLandscape(degrees: Int): Boolean? {
    if (degrees < 0) return null
    val fromUpright = degrees % 180
    return when {
        fromUpright in 60..120 -> true
        fromUpright <= 30 || fromUpright >= 150 -> false
        else -> null
    }
}

/**
 * Aufteilung des Player-Bildschirms. Am Fernseher, im Querformat und im kleinen Fenster nur das Bild bis an den Rand,
 * Leisten weg; Steuerung und Overlay rücken vom Kameraeinschnitt ab. Im Hochformat auf dem Handy das Bild in 16:9 unter
 * der Statusleiste, darunter [details]. Die Activity baut sich dabei nie neu auf (configChanges), der Player läuft durch.
 */
class PlayerLayout(
    private val activity: ComponentActivity,
    private val view: PlayerView,
    overlay: View,
    private val details: View,
    private val touchUi: Boolean,
) {
    private val video = FrameLayout(activity).apply { addView(view); addView(overlay) }
    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(video, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(details, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
    }
    private val controls = listOf(view.findViewById<View>(androidx.media3.ui.R.id.exo_controller), overlay)
    private val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    /** Hochformat auf dem Handy: Bild oben, Videoseite darunter. */
    var inline = false; private set

    /** Die per Knopf erzwungene Ausrichtung (true quer), bis das Gerät so gehalten wird; null = frei. */
    private var wanted: Boolean? = null
    private val sensor = object : OrientationEventListener(activity) {
        override fun onOrientationChanged(degrees: Int) {
            if (heldLandscape(degrees) != wanted) return
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            wanted = null
            disable()
        }
    }

    init {
        // Helle Symbole: der Grund unter der Statusleiste ist im Hochformat immer dunkel.
        val dark = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(dark, dark)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            if (inline) {
                root.setPadding(0, system.top, 0, system.bottom)
                controls.forEach { it.setPadding(0, 0, 0, 0) }
            } else {
                root.setPadding(0, 0, 0, 0)
                controls.forEach { it.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom) }
            }
            insets
        }
        if (touchUi) view.setFullscreenButtonClickListener(::lockOrientation)
    }

    /** Nach jedem Wechsel von Ausrichtung oder Fenster-Modus. */
    fun apply(inPictureInPicture: Boolean) {
        inline = touchUi && !inPictureInPicture && activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        details.isVisible = inline
        video.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, if (inline) activity.resources.displayMetrics.widthPixels * 9 / 16 else MATCH_PARENT)
        if (inline) bars.show(WindowInsetsCompat.Type.systemBars()) else bars.hide(WindowInsetsCompat.Type.systemBars())
        if (touchUi) view.setFullscreenButtonState(!inline)
        ViewCompat.requestApplyInsets(root)
    }

    /**
     * Vollbild-Knopf: dreht ins Quer- bzw. Hochformat. Hält man das Gerät danach so, folgt die Ansicht wieder dem Drehen.
     * Ist das automatische Drehen aus, bleibt es beim Knopf; die Sperre zu lösen, drehte sonst gleich zurück.
     */
    private fun lockOrientation(landscape: Boolean) {
        activity.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        if (Settings.System.getInt(activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1) {
            wanted = landscape
            sensor.enable()
        }
    }

    fun release() = sensor.disable()
}
