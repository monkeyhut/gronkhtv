package org.streamarchiv.tv.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.streamarchiv.tv.ui.runCatchingIo
import java.io.File
import java.io.IOException

/**
 * Chat-Nachrichten je Minute aus dem Chat-Replay (gemessen 21.10.2026): Dateien `<chat>0`, `<chat>1`, … mit je
 * 5 Minuten, `offset` zählt Sekunden ab Videostart über alle Blöcke, nach dem letzten Block kommt 404.
 * Ein 12-h-Stream sind rund 140 Blöcke und 25 MB; deshalb wird nur gezählt und die Zählung je Video im Cache abgelegt.
 */
object ChatHeat {
    private const val TAG = "GronkhChat"
    private const val PARALLEL = 4
    private const val CHUNK_SECONDS = 300
    /** Die ID wird zum Dateinamen; alles außer diesen Zeichen könnte aus dem Cache-Ordner hinausführen. */
    private val safeId = Regex("[A-Za-z0-9-]+")

    @Serializable private class Chunk(val messages: List<Message> = emptyList())
    /** offset fehlt selten oder ist null; so eine Nachricht hat keine Zeit und zählt nicht. */
    @Serializable private class Message(val offset: Int? = null)

    /** Zählung je Minute; [complete], wenn unterwegs nichts scheiterte und sie in den Cache darf. */
    internal class Count(val perMinute: IntArray, val complete: Boolean, val failure: Throwable?)

    /** Null, wenn das Video kein Chat-Replay oder keine Länge hat. */
    suspend fun perMinute(cacheDir: File, video: Video): IntArray? {
        val folder = video.urls.chat ?: return null
        if (video.meta.duration <= 0) return null
        val file = File(cacheDir, "chat-heat/${video.id}").takeIf { safeId.matches(video.id) }
        return withContext(Dispatchers.IO) {
            file?.let(::read) ?: count(folder, video.meta.duration) { GronkhApi.cdnFile(it) }.let { result ->
                result.failure?.let { Log.w(TAG, "Chat-Replay von ${video.id} nur teilweise gezählt", it) }
                if (result.complete && file != null) save(file, result.perMinute)
                result.perMinute
            }
        }
    }

    /**
     * Holt die Blöcke zu viert parallel und zählt nur die lückenlose Folge ab Block 0. Ein 404 ist das sichere Ende,
     * auch vor dem Videoende oder schon bei Block 0 (Chat kürzer oder leer); so eine Zählung ist fertig und kommt in den
     * Cache, sonst lüde jedes Öffnen sie neu. Scheitert ein Block (Netz, 5xx), bleibt das bis dahin Gezählte gültig,
     * kommt aber nicht in den Cache; scheitert schon der erste, gibt es nichts zu zeigen.
     */
    internal suspend fun count(folder: String, seconds: Long, fetch: suspend (String) -> ByteArray?): Count = coroutineScope {
        val perMinute = IntArray((seconds / 60 + 1).toInt())
        var loaded = 0
        var failure: Throwable? = null
        var ended = false
        // Die Grenze schützt nur vor einer Endlosschleife, falls das 404 ausbleibt; der Chat läuft etwas über das Video hinaus.
        while (!ended && loaded * CHUNK_SECONDS < seconds + 3600) {
            val chunks = (loaded until loaded + PARALLEL).map { i ->
                async { runCatchingIo { fetch(folder + i)?.let { GronkhApi.json.decodeFromString<Chunk>(it.decodeToString()) } } }
            }.awaitAll()
            for (chunk in chunks) {
                val messages = chunk.getOrElse { failure = it; null }?.messages
                if (messages == null) { ended = true; break }
                for (message in messages) {
                    val minute = (message.offset ?: continue) / 60
                    if (minute in perMinute.indices) perMinute[minute]++
                }
                loaded++
            }
        }
        failure?.let { if (loaded == 0) throw it }
        Count(perMinute, complete = failure == null, failure)
    }

    private fun read(file: File): IntArray? =
        file.takeIf { it.exists() }?.readText()?.split(',')?.map { it.toIntOrNull() ?: return null }?.toIntArray()

    /** Der Cache spart nur das nächste Laden; scheitert das Schreiben (Speicher voll), gilt die fertige Zählung trotzdem. */
    private fun save(file: File, perMinute: IntArray) {
        try {
            file.parentFile?.mkdirs()
            // Erst ganz schreiben, dann umbenennen: ein Abbruch mittendrin hinterließe sonst eine halbe Zählung als „fertig“.
            val tmp = File(file.path + ".tmp")
            tmp.writeText(perMinute.joinToString(","))
            if (!tmp.renameTo(file)) throw IOException("Umbenennen von ${tmp.name} gescheitert")
        } catch (e: IOException) {
            Log.w(TAG, "Zählung nicht im Cache abgelegt: ${file.path}; beim nächsten Öffnen wird neu geladen", e)
        }
    }
}
