package org.streamarchiv.tv.dream

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.service.dreams.DreamService
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.animation.doOnEnd
import org.streamarchiv.tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/**
 * Bildschirmschoner: die acht Keyarts in zufälliger Folge, weich überblendet, mit langsamem Ken-Burns-Zoom und
 * dezenter Uhr unten links.
 *
 * Klassische Views statt Compose: ein DreamService ist kein LifecycleOwner, Compose bräuchte dafür eigenes Gerüst.
 * Zwei ImageViews und Animatoren tragen dasselbe ohne das.
 */
class GalleryDream : DreamService() {
    private val scope = MainScope()
    private lateinit var images: ViewGroup
    private lateinit var clock: View
    private lateinit var time: TextView
    private lateinit var date: TextView
    private var fade: ObjectAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        // Kein Dauerhell: der Schoner läuft stundenlang, das System soll das Panel wie sonst dimmen dürfen.
        isScreenBright = false
        setContentView(R.layout.dream_gallery)
        images = findViewById(R.id.images)
        clock = findViewById(R.id.clock)
        time = findViewById(R.id.time)
        date = findViewById(R.id.date)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        scope.launch { runClock() }
        scope.launch { runSlideshow() }
    }

    // Ob onDetachedFromWindow bei einem Schoner in der DreamActivity sicher kommt, ist nicht belegt; onDreamingStopped
    // kommt vor dem Entfernen des Fensters. Ohne Aufräumen liefe jede Bildschleife nach Schonerende weiter.
    override fun onDreamingStopped() {
        stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun stop() {
        scope.cancel()
        for (i in 0 until images.childCount) images.getChildAt(i).animate().cancel()
        fade?.cancel()
    }

    private suspend fun runClock() {
        val shift = SHIFT_DP * resources.displayMetrics.density
        while (true) {
            val now = LocalDateTime.now()
            time.text = now.format(timeFormat)
            date.text = now.format(dateFormat)
            // Gegen Einbrennen wandert die Uhr gelegentlich um ein paar Pixel; jede Minute wäre unruhig.
            if (now.minute % SHIFT_EVERY_MINUTES == 0) {
                clock.translationX = Random.nextFloat() * shift
                clock.translationY = -Random.nextFloat() * shift
            }
            delay(MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
        }
    }

    private suspend fun runSlideshow() {
        var last = -1
        while (true) {
            val next = KEYARTS.indices.filter { it != last }.random()
            last = next
            crossfadeTo(withContext(Dispatchers.Default) { decode(KEYARTS[next]) })
            delay(HOLD_MS)
        }
    }

    /** Das hintere Bild bekommt das neue Motiv und blendet über dem vorderen ein; danach gibt das alte sein Bitmap frei. */
    private fun crossfadeTo(bitmap: Bitmap) {
        val outgoing = images.getChildAt(1) as ImageView
        val incoming = images.getChildAt(0) as ImageView
        incoming.setImageBitmap(bitmap)
        incoming.bringToFront()
        kenBurns(incoming)
        fade = ObjectAnimator.ofFloat(incoming, View.ALPHA, 0f, 1f).apply {
            duration = FADE_MS
            doOnEnd { outgoing.setImageDrawable(null) }
            start()
        }
    }

    /** Zoom um einen zufälligen Punkt im Bild wirkt wie ein Schwenk und deckt bei Maßstab ≥ 1 immer das ganze Bild. */
    private fun kenBurns(view: ImageView) {
        val (from, to) = if (Random.nextBoolean()) 1f to ZOOM else ZOOM to 1f
        view.pivotX = view.width * Random.nextFloat()
        view.pivotY = view.height * Random.nextFloat()
        view.scaleX = from
        view.scaleY = from
        // Läuft durch die eigene Ausblendung weiter, sonst stünde das Bild beim Überblenden still.
        view.animate().scaleX(to).scaleY(to).setDuration(HOLD_MS + FADE_MS).setInterpolator(LinearInterpolator())
    }

    /**
     * Dekodiert auf Bildschirmbreite statt auf 2560 px: bei 1920×1080 sind das 8,3 statt 14,7 MB je Bild.
     * Gleichzeitig im Speicher liegen höchstens zwei Bilder, während der Überblendung.
     */
    private fun decode(res: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, res, bounds)
        val options = BitmapFactory.Options().apply {
            // Die Dichte-Skalierung von BitmapFactory ist der einzige Weg, beim Dekodieren auf eine beliebige Breite zu kommen.
            inDensity = bounds.outWidth
            inTargetDensity = minOf(resources.displayMetrics.widthPixels, bounds.outWidth)
        }
        // Ohne DENSITY_NONE rechnet die ImageView die künstliche Dichte zurück in eine falsche Eigengröße.
        return BitmapFactory.decodeResource(resources, res, options).apply { density = Bitmap.DENSITY_NONE }
    }

    private companion object {
        val KEYARTS = intArrayOf(
            R.drawable.dream_01, R.drawable.dream_02, R.drawable.dream_03, R.drawable.dream_04,
            R.drawable.dream_05, R.drawable.dream_06, R.drawable.dream_07, R.drawable.dream_08,
        )
        const val HOLD_MS = 40_000L
        const val FADE_MS = 2_500L
        const val ZOOM = 1.08f
        const val MINUTE_MS = 60_000L
        const val SHIFT_EVERY_MINUTES = 3
        const val SHIFT_DP = 6f
        val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
        val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)
    }
}
