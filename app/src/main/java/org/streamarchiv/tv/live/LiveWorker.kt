package org.streamarchiv.tv.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.streamarchiv.tv.R
import org.streamarchiv.tv.data.GronkhApi
import org.streamarchiv.tv.data.LiveStream
import org.streamarchiv.tv.ui.runCatchingIo
import java.util.concurrent.TimeUnit

/**
 * Fragt im Hintergrund, ob Gronkh live ist, und meldet nur den Wechsel von nicht live auf live. Der letzte Stand liegt
 * gespeichert, so kommt pro Stream eine Benachrichtigung statt eine alle 15 Minuten.
 */
class LiveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val stream = runCatchingIo { GronkhApi.gronkhLive() }
            .onFailure { Log.e(TAG, "Live-Status nicht geladen, nächster Versuch im nächsten Takt", it) }
            // Der gespeicherte Stand bleibt; ein Netzfehler mitten im Stream löst später keine zweite Meldung aus.
            .getOrElse { return Result.success() }
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (stream != null && !prefs.getBoolean(KEY_LIVE, false)) notify(stream)
        // commit statt apply: nach doWork darf das System den Prozess beenden, ein aufgeschobenes Schreiben ginge dann
        // verloren und der nächste Takt meldete denselben Stream nochmal.
        if (!prefs.edit().putBoolean(KEY_LIVE, stream != null).commit()) Log.e(TAG, "Live-Stand nicht gespeichert")
        return Result.success()
    }

    private fun notify(stream: LiveStream) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Benachrichtigungen sind abgeschaltet, der Live-Hinweis entfällt")
            return
        }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Gronkh ist live", NotificationManager.IMPORTANCE_HIGH))
        // Außerhalb einer Activity gestartet, deshalb als neue Aufgabe.
        val open = applicationContext.twitchOrWebIntent(stream.login).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notification = Notification.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Gronkh ist live: ${stream.title}")
            .setContentText(stream.game)
            .setContentIntent(PendingIntent.getActivity(applicationContext, 0, open, PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "GronkhLive"
        private const val PREFS = "live"
        private const val KEY_LIVE = "was_live"
        private const val CHANNEL = "live"
        private const val NOTIFICATION_ID = 1
        private const val WORK_NAME = "live-check"

        /** Beim App-Start; KEEP lässt einen schon geplanten Job samt Takt in Ruhe. 15 Minuten ist das Minimum von WorkManager. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LiveWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
