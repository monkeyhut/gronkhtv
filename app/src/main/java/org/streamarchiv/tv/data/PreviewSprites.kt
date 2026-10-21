package org.streamarchiv.tv.data

import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Vorschaubilder eines Videos: die Spur plus das Sprite, aus dem je Spulziel nur der eine Ausschnitt entpackt wird.
 * Das ganze Sprite (bis 1600 × 15.000 px) entpackt wären bis zu 100 MB; komprimiert sind es 2 bis 3 MB.
 * Kein recycle(): ein noch laufendes decodeRegion träfe sonst einen freigegebenen Decoder; der GC räumt ihn weg.
 */
class PreviewSprites private constructor(private val cues: List<PreviewCue>, private val sprite: BitmapRegionDecoder) {
    fun frameAt(positionMs: Long): Bitmap? {
        val cue = cues.at(positionMs) ?: return null
        val rect = Rect(cue.x, cue.y, cue.x + cue.width, cue.y + cue.height)
        if (!rect.intersect(0, 0, sprite.width, sprite.height)) return null
        return sprite.decodeRegion(rect, null)
    }

    companion object {
        /**
         * Null, wenn das Video keine Vorschau hat. Gemessen nennt die Spur immer genau das eine Sprite aus urls.sprites;
         * shortcut: Dateinamen der Spur werden nicht ausgewertet, falls gronkh.tv je mehrere Sprites liefert, hier nachrüsten.
         */
        suspend fun load(urls: Urls): PreviewSprites? {
            val vtt = urls.vtt ?: return null
            val spriteUrl = urls.sprites ?: return null
            val cues = parsePreviewVtt(GronkhApi.cdnFile(vtt)?.decodeToString() ?: throw IOException("Vorschauspur fehlt (404)"))
            if (cues.isEmpty()) throw IOException("Vorschauspur ohne lesbare Einträge")
            val bytes = GronkhApi.cdnFile(spriteUrl) ?: throw IOException("Sprite fehlt (404)")
            @Suppress("DEPRECATION") // Der Ersatz newInstance(ByteArray, Int, Int) kam erst mit API 31, minSdk ist 26.
            val decoder = withContext(Dispatchers.Default) { BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false) }
            return PreviewSprites(cues, decoder)
        }
    }
}
