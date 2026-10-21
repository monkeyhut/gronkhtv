package org.streamarchiv.tv.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.media3.ui.DefaultTimeBar
import org.streamarchiv.tv.data.Chapter
import org.streamarchiv.tv.data.startMs

/**
 * Weiße Striche an den Kapitelgrenzen, im Overlay der Zeitleiste, damit sie mit ihr ein- und ausblenden. Die eine Markierungsart der Leiste (Werbemarken) tragen schon die Höhepunkte. Die Striche ragen über die
 * Leiste hinaus, weil der gespielte Teil selbst weiß ist.
 */
class ChapterTicks(private val bar: DefaultTimeBar, private val durationMs: () -> Long) : Drawable() {
    private val density = bar.resources.displayMetrics.density
    private val paint = Paint().apply { color = 0xFFFFFFFF.toInt() }
    var chapters: List<Chapter> = emptyList()
        set(value) { field = value; invalidateSelf() }

    init {
        // Das Overlay zeichnet über die ganze Leiste; ohne Grenzen liefe invalidateSelf ins Leere.
        bar.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ -> setBounds(0, 0, right - left, bottom - top) }
        bar.overlay.add(this)
    }

    override fun draw(canvas: Canvas) {
        val duration = durationMs()
        if (duration <= 0) return
        // Wie DefaultTimeBar.onLayout: die Leiste beginnt und endet um den halben größten Regler eingerückt.
        val inset = (DefaultTimeBar.DEFAULT_SCRUBBER_DRAGGED_SIZE_DP * density + 1).toInt() / 2
        val left = bar.paddingLeft + inset
        val width = bar.width - bar.paddingRight - inset - left
        val centerY = bar.height / 2f
        val halfWidth = TICK_WIDTH_DP * density / 2
        val halfHeight = TICK_HEIGHT_DP * density / 2
        for (chapter in chapters) {
            if (chapter.start == 0L) continue
            val x = left + width * chapter.startMs.coerceAtMost(duration).toFloat() / duration
            canvas.drawRect(x - halfWidth, centerY - halfHeight, x + halfWidth, centerY + halfHeight, paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val TICK_WIDTH_DP = 2f
        const val TICK_HEIGHT_DP = 14f
    }
}
