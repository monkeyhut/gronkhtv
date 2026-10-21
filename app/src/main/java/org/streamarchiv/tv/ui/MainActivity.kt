package org.streamarchiv.tv.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.darkColorScheme
import org.streamarchiv.tv.data.LiveStream
import org.streamarchiv.tv.data.Video
import org.streamarchiv.tv.live.LiveWorker
import org.streamarchiv.tv.live.twitchIntent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveWorker.schedule(this)
        askForNotificationsOnce()
        setAppContent { App() }
    }

    @Composable
    private fun App() {
        var searching by rememberSaveable { mutableStateOf(false) }
        val home: HomeViewModel = viewModel()
        val search: SearchViewModel = viewModel()
        val openAt = { video: Video, startMs: Long? -> startActivity(PlayerActivity.intent(this, video, startMs)) }
        val openVideo = { video: Video -> openAt(video, null) }
        val markVideo = { video: Video -> showMarked(home.toggleWatchlist(video)) }

        if (searching) {
            BackHandler { searching = false }
            SearchScreen(search, onOpenVideo = openVideo, onMark = markVideo)
        } else {
            HomeScreen(
                home,
                onOpenVideo = openVideo,
                onOpenAt = openAt,
                onMark = markVideo,
                onOpenLive = ::openTwitch,
                onOpenGame = { startActivity(GameActivity.intent(this, it)) },
                onSearch = { searching = true },
            )
        }
    }

    private fun openTwitch(stream: LiveStream) {
        try {
            startActivity(twitchIntent(stream.login))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Twitch-App fehlt, bitte aus dem Play Store installieren", Toast.LENGTH_LONG).show()
        }
    }

    /** Ab Android 13 braucht der Live-Hinweis die Erlaubnis. Nur beim ersten Start fragen, danach gilt die Systemeinstellung. */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATIONS_ASKED, false)) return
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ASKED, true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
    }

    private companion object {
        const val KEY_NOTIFICATIONS_ASKED = "notifications_asked"
    }
}

/**
 * Inhalt jeder Compose-Activity. Randlos: der dunkle Grund reicht auf dem Handy hinter Status- und Navigationsleiste
 * (helle Symbole), der Inhalt bleibt davor, neben dem Kameraeinschnitt und über der Tastatur. Am Fernseher sind diese
 * Abstände null.
 */
fun ComponentActivity.setAppContent(content: @Composable () -> Unit) {
    val bars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    enableEdgeToEdge(bars, bars)
    setContent { AppSurface(content) }
}

/** Farben und Hintergrund. Surface setzt auch die Textfarbe; ohne sie wären Überschriften schwarz auf Dunkel. */
@Composable
private fun AppSurface(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    ) {
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) { content() }
    }
}

fun Context.showMarked(marked: Boolean) =
    Toast.makeText(this, if (marked) "Gemerkt" else "Von der Merkliste genommen", Toast.LENGTH_SHORT).show()

internal val Colors = darkColorScheme(
    primary = Color(0xFFB083FF),
    onPrimary = Color(0xFF1A0B33),
    background = Color(0xFF0E0B16),
    surface = Color(0xFF0E0B16),
    onSurface = Color(0xFFF1ECFA),
    surfaceVariant = Color(0xFF221C30),
    onSurfaceVariant = Color(0xFFB3AAC4),
    error = Color(0xFFFF8A80),
)
