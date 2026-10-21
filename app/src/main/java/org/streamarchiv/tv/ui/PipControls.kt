package org.streamarchiv.tv.ui

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.core.content.ContextCompat
import androidx.media3.common.Player

/**
 * Bild im Bild: das kleine Fenster, in dem das Video weiterläuft, während man das Gerät anders nutzt. Eigene Knöpfe
 * −10 s, Pause/Weiter, +30 s; ohne sie zeigte das System die Knöpfe der MediaSession, und deren „Zurück“ spränge an
 * den Anfang eines stundenlangen Streams. Geräte ohne Bild im Bild (viele Fernseher) bekommen nichts davon zu sehen.
 */
class PipControls(private val activity: Activity, private val player: Player, private val togglePause: () -> Unit) {
    val supported = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(EXTRA_CONTROL, -1)) {
                CONTROL_BACK -> player.seekBack()
                CONTROL_TOGGLE -> togglePause()
                CONTROL_FORWARD -> player.seekForward()
            }
        }
    }

    init {
        // Nicht exportiert: die Knöpfe sendet das System mit der Kennung dieser App (PendingIntent), sonst niemand.
        if (supported) ContextCompat.registerReceiver(activity, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Nach jedem Wechsel zwischen Wiedergabe und Pause: das Pause-Symbol tauscht, und ab Android 12 geht das Video beim
     * Verlassen (Home, Wischgeste) nur dann von selbst ins kleine Fenster, wenn es läuft.
     */
    fun update() {
        if (supported) activity.setPictureInPictureParams(params())
    }

    /** Für Android 8 bis 11, die das Verlassen nur über onUserLeaveHint melden. */
    fun enter() {
        if (supported) activity.enterPictureInPictureMode(params())
    }

    fun release() {
        if (supported) activity.unregisterReceiver(receiver)
    }

    private fun params(): PictureInPictureParams {
        val playing = player.playWhenReady
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(
                listOf(
                    action(android.R.drawable.ic_media_rew, "10 s zurück", CONTROL_BACK),
                    if (playing) action(android.R.drawable.ic_media_pause, "Pause", CONTROL_TOGGLE)
                    else action(android.R.drawable.ic_media_play, "Weiter", CONTROL_TOGGLE),
                    action(android.R.drawable.ic_media_ff, "30 s vor", CONTROL_FORWARD),
                ),
            )
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(playing) }
            .build()
    }

    private fun action(icon: Int, title: String, control: Int): RemoteAction {
        val intent = Intent(ACTION).setPackage(activity.packageName).putExtra(EXTRA_CONTROL, control)
        val pending = PendingIntent.getBroadcast(activity, control, intent, PendingIntent.FLAG_IMMUTABLE)
        return RemoteAction(Icon.createWithResource(activity, icon), title, title, pending)
    }

    private companion object {
        const val ACTION = "org.streamarchiv.tv.PIP_CONTROL"
        const val EXTRA_CONTROL = "control"
        const val CONTROL_BACK = 1
        const val CONTROL_TOGGLE = 2
        const val CONTROL_FORWARD = 3
    }
}
