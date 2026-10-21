package org.streamarchiv.tv.live

import android.content.Intent
import android.net.Uri

/** Live läuft in der offiziellen Twitch-App (dort Chat, Werbung, Anmeldung); die versteht twitch://stream/<kanal>. */
fun twitchIntent(login: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("twitch://stream/$login"))
