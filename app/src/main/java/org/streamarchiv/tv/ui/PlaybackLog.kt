package org.streamarchiv.tv.ui

import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener

/**
 * Schreibt ins Log, in welcher Stufe und mit welchem Decoder ein Video läuft und wie viele Bilder verworfen werden.
 * Ruckeln ist sonst am Gerät nicht von einem langsamen Netz zu unterscheiden: `adb logcat -s GronkhPlayback`.
 */
@UnstableApi
object PlaybackLog : AnalyticsListener {
    private const val TAG = "GronkhPlayback"

    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        Log.i(TAG, "Stufe ${format.width}x${format.height} ${format.frameRate} fps ${format.bitrate / 1000} kbit/s")
    }

    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        Log.i(TAG, "Decoder $decoderName")
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        Log.w(TAG, "$droppedFrames Bilder verworfen in $elapsedMs ms")
    }
}
