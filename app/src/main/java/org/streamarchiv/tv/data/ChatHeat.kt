package org.streamarchiv.tv.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private class Chunk(val messages: List<Message> = emptyList())
    @Serializable private class Message(val offset: Int)

    /** Null, wenn das Video kein Chat-Replay oder keine Länge hat. */
    suspend fun perMinute(cacheDir: File, video: Video): IntArray? {
        val folder = video.urls.chat ?: return null
        if (video.meta.duration <= 0) return null
        val file = File(cacheDir, "chat-heat/${video.id}")
        return withContext(Dispatchers.IO) {
            read(file) ?: count(folder, video.meta.duration).also { save(file, it) }
        }
    }

    private suspend fun count(folder: String, seconds: Long): IntArray = coroutineScope {
        val perMinute = IntArray((seconds / 60 + 1).toInt())
        var next = 0
        // Die Grenze schützt nur vor einer Endlosschleife, falls das 404 ausbleibt; der Chat läuft etwas über das Video hinaus.
        while (next * CHUNK_SECONDS < seconds + 3600) {
            val chunks = (next until next + PARALLEL).map { i ->
                async { GronkhApi.cdnFile(folder + i)?.let { json.decodeFromString<Chunk>(it.decodeToString()) } }
            }.awaitAll()
            for (message in chunks.filterNotNull().flatMap { it.messages }) {
                val minute = message.offset / 60
                if (minute in perMinute.indices) perMinute[minute]++
            }
            if (chunks.any { it == null }) break
            next += PARALLEL
        }
        perMinute
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
