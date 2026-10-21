package org.streamarchiv.tv.live

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Live läuft in der offiziellen Twitch-App (dort Chat, Werbung, Anmeldung); die versteht twitch://stream/<kanal>. */
fun twitchIntent(login: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("twitch://stream/$login"))

/**
 * Für die Benachrichtigung, die beim Tippen keinen Fehler mehr melden kann: ohne Twitch-App die Kanalseite im Browser.
 * Sehen kann die App die Twitch-App nur dank des queries-Eintrags im Manifest.
 */
fun Context.twitchOrWebIntent(login: String): Intent =
    twitchIntent(login).takeIf { it.resolveActivity(packageManager) != null }
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.twitch.tv/$login"))
