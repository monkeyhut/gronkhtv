package org.streamarchiv.tv.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import org.streamarchiv.tv.R
import org.streamarchiv.tv.data.Chapter
import org.streamarchiv.tv.data.ChatHeat
import org.streamarchiv.tv.data.chapterAt
import org.streamarchiv.tv.data.playerChapters
import org.streamarchiv.tv.data.startMs
import org.streamarchiv.tv.data.startPosition
import org.streamarchiv.tv.data.GronkhApi
import org.streamarchiv.tv.data.PreviewSprites
import org.streamarchiv.tv.data.ResumeStore
import org.streamarchiv.tv.data.Video
import org.streamarchiv.tv.data.WatchStats
import org.streamarchiv.tv.data.highlightMinutes
import org.streamarchiv.tv.data.nextHighlightTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PlayerActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var view: PlayerView
    private lateinit var resume: ResumeStore
    private lateinit var stats: WatchStats
    private lateinit var video: Video
    private val overlayState = PlayerOverlayState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val encoded = requireNotNull(intent.getStringExtra(EXTRA_VIDEO)) { "PlayerActivity ohne Video gestartet" }
        resume = ResumeStore(this)
        stats = WatchStats(this)

        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()
        // Immer die höchste Stufe, die der Decoder kann, auch im kleinen Fenster und bei schwankendem Netz: lieber kurz
        // nachladen als ein matschiges Bild. Gemessen 21.10.2026: jedes Video hat 360p30, 720p30 und 1080p60, auch alte
        // mit 720p-Quelle. Fehlt 1080p oder kann das Gerät es nicht, ist die höchste vorhandene Stufe die Wahl.
        // Der Puffer bleibt bei Media3s Vorgabe (50 s Ziel, Start nach 2,5 s), die liegt schon über 15–30 s.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setForceHighestSupportedBitrate(true)
            .clearViewportSizeConstraints()
            .build()
        player.addAnalyticsListener(PlaybackLog)
        // Die MediaSession meldet die Wiedergabe beim System an, damit Medientasten und Sprachbefehle sie steuern können.
        session = MediaSession.Builder(this, player).build()
        pip = PipControls(this, player) { if (player.playWhenReady) player.pause() else resume() }

        view = PlayerView(this).apply {
            player = this@PlayerActivity.player
            setShowNextButton(false)
            setShowPreviousButton(false)
            // isControllerFullyVisible ist während der Ein-/Ausblend-Animation falsch; am Gerät griff „runter“ deshalb nicht.
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener {
                overlayState.barShown = it == View.VISIBLE
                // Das Vorschaubild gehört zur Leiste und geht mit ihr, ebenso das „hoch springt weiter“.
                if (!overlayState.barShown) { previewJob?.cancel(); overlayState.preview = null; barFromJump = false }
                else updateChapter()
            })
        }
        val taps = OverlayTaps(
            openOptions = ::openOptions,
            pick = ::applyOption,
            closeOptions = { overlayState.optionsCursor = null },
            startNext = { (overlayState.upNext?.video ?: overlayState.next)?.let(::startNext) },
        )
        overlayView = ComposeView(this).apply {
            // Das Overlay zeigt nur; bekäme es den Fokus, kämen Steuerkreuz-Ereignisse nicht mehr bei der PlayerView an.
            // Berührungen ohne Ziel im Overlay fallen zur PlayerView durch.
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setContent { PlayerOverlay(overlayState, taps) }
        }
        val details = ComposeView(this).apply { setContent { PlayerDetails(overlayState, taps) } }
        layout = PlayerLayout(this, view, overlayView, details, touchUi)
        setContentView(layout.root)
        applyLayout()
        view.setOnTouchListener(touchGestures())
        timeBar?.run {
            setAdMarkerColor(HIGHLIGHT_COLOR); setPlayedAdMarkerColor(HIGHLIGHT_COLOR)
            ticks = ChapterTicks(this) { player.duration }
            // Beim Ziehen mit dem Finger zeigt das Bild die Stelle unter dem Finger; am Fernseher spult das Steuerkreuz
            // selbst und zieht nie an der Leiste.
            addListener(object : TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: TimeBar, position: Long) = showPreview(position)
                override fun onScrubMove(timeBar: TimeBar, position: Long) = showPreview(position)
                override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) = Unit
            })
        }
        player.addListener(object : Player.Listener {
            // Nur während der Wiedergabe wach halten; in der Pause sollen Bildschirmschoner und Ruhezustand greifen.
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                view.keepScreenOn = isPlaying
                if (isPlaying) playingSince = SystemClock.elapsedRealtime() else { bookWatchTime(); saveResume() }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = pip.update()

            // ExoPlayer hat dann schon selbst mehrfach neu versucht; ohne Hinweis stünde nur ein schwarzes Bild da.
            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Wiedergabe abgebrochen: ${video.id}", error)
                view.setCustomErrorMessage(getString(if (touchUi) R.string.player_error_touch else R.string.player_error))
            }

            // Läuft es wieder (OK, Medientaste der Fernbedienung, Sprachbefehl), muss der Hinweis weg.
            override fun onPlayerErrorChanged(error: PlaybackException?) {
                if (error == null) view.setCustomErrorMessage(null)
            }

            // Wer nach dem Ende zurückspult, will diesen Teil sehen, nicht den nächsten.
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) offerNext() else cancelUpNext()
            }
        })
        play(Json.decodeFromString(Video.serializer(), encoded), intent.getLongExtra(EXTRA_START, -1).takeIf { it >= 0 })
    }

    /**
     * Startet [next] im selben Player; so folgt am Ende der nächste Teil, ohne die Activity neu aufzubauen.
     * [startMs] ist ein Kapitelanfang (von der Spielseite); die gespeicherte Stelle gilt dann nur innerhalb des Kapitels.
     */
    private fun play(next: Video, startMs: Long? = null) {
        video = next
        extras?.cancel()
        highlights = null
        previews = null
        view.setExtraAdGroupMarkers(null, null)
        overlayState.video = next
        overlayState.next = null
        val chapters = next.playerChapters()
        overlayState.chapters = chapters
        overlayState.chapter = null
        ticks?.chapters = chapters
        // Die Playlist-URL hat keine Endung, deshalb HLS ausdrücklich angeben.
        val item = MediaItem.Builder().setUri(next.urls.playlist).setMimeType(MimeTypes.APPLICATION_M3U8).build()
        player.setMediaItem(item, startPosition(chapters, startMs, resume.position(next.id)))
        player.prepare()
        player.playWhenReady = true
        extras = lifecycleScope.launch {
            launch { loadHighlights(next) }
            launch { loadPreviews(next) }
            // Für „Als Nächstes“ auf der Videoseite im Hochformat; der Fernseher sucht erst am Ende.
            if (touchUi) launch {
                overlayState.next = runCatchingIo { GronkhApi.nextEpisode(next) }
                    .onFailure { Log.w(TAG, "Nächster Teil nach ${next.id} nicht gesucht", it) }
                    .getOrNull()
            }
        }
    }

    /** Hochformat oder Vollbild; die Activity baut sich beim Drehen nicht neu auf (configChanges). */
    private fun applyLayout() {
        layout.apply(isInPictureInPictureMode)
        overlayState.inline = layout.inline
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLayout()
    }

    /**
     * Tippen blendet die Leiste ein und aus (PlayerView.performClick), Doppeltippen links spult zurück, rechts vor.
     * Hier landen nur Berührungen, die kein Knopf und nicht die Zeitleiste genommen hat.
     */
    private fun touchGestures(): View.OnTouchListener {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent) = view.performClick()
            override fun onDoubleTap(e: MotionEvent): Boolean {
                act(if (e.x < view.width / 2) PlayerKey.SEEK_BACK else PlayerKey.SEEK_FORWARD)
                return true
            }
        })
        return View.OnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        // Auf dem Handy heißt Zurück verlassen; die Leiste kommt dort mit jedem Tippen, erst sie zu schließen nervte.
        if (touchUi && code == KeyEvent.KEYCODE_BACK && overlayState.optionsCursor == null && overlayState.upNext == null) {
            return super.dispatchKeyEvent(event)
        }
        // PlayerView blendet die Leiste bei jedem Steuerkreuz-Ereignis ein, auch beim Loslassen. Ohne das hier
        // ging sie nach „runter“ oder OK sofort wieder auf.
        if (event.action == KeyEvent.ACTION_UP && code == handledKey) {
            handledKey = null
            if (okPending) { okPending = false; act(key(code, longPress = false)) }
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        if (event.repeatCount > 0 && code == handledKey) {
            // Die erste Wiederholung kommt nach der Lang-Druck-Zeit des Systems (dort setzt Android FLAG_LONG_PRESS).
            if (okPending) { okPending = false; act(key(code, longPress = true)) }
            // Gedrückt halten wiederholt nur links/rechts; Runter oder OK würden sonst flackern bzw. die Leiste zurückholen.
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return true
        }
        // Eine neue Taste während OK noch gedrückt ist: das OK verfällt, sonst löste ihr Loslassen es aus.
        okPending = false
        val cursor = overlayState.optionsCursor
        if (cursor != null) {
            optionsKey(code, cursor)
        } else if (overlayState.upNext == null || !upNextKey(code)) {
            val key = key(code, longPress = false)
            if (key == PlayerKey.PASS) return super.dispatchKeyEvent(event)
            // OK wartet aufs Loslassen oder Halten, nur so ist langes OK von kurzem zu unterscheiden. Alles andere wirkt sofort.
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) okPending = true else act(key)
        }
        handledKey = code
        return true
    }

    private fun key(code: Int, longPress: Boolean): PlayerKey {
        val barShown = overlayState.barShown
        val onButtons = barShown && view.findFocus().let { it != null && it !== timeBar }
        return playerKey(code, barShown, onButtons, failed = player.playerError != null, longPress, afterJump = barFromJump)
    }

    private fun act(key: PlayerKey) {
        when (key) {
            PlayerKey.SEEK_BACK -> { player.seekBack(); showTimeBar(); showPreview() }
            PlayerKey.SEEK_FORWARD -> { player.seekForward(); showTimeBar(); showPreview() }
            PlayerKey.HIDE_BAR -> view.hideController()
            PlayerKey.RESUME_AND_HIDE -> { resume(); view.hideController() }
            PlayerKey.TOGGLE_PAUSE -> { if (player.playWhenReady) player.pause() else resume(); showTimeBar() }
            PlayerKey.RETRY -> resume()
            PlayerKey.NEXT_HIGHLIGHT -> jumpToHighlight()
            PlayerKey.OPTIONS -> openOptions()
            PlayerKey.PASS -> Unit
        }
    }

    /** Die Karte „Als Nächstes“ nimmt nur OK (sofort starten) und Zurück (abbrechen); alles andere wirkt wie sonst. */
    private fun upNextKey(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> overlayState.upNext?.let { startNext(it.video) }
            KeyEvent.KEYCODE_BACK -> cancelUpNext()
            else -> return false
        }
        return true
    }

    /** Am Ende: nächsten Teil suchen und nach [UP_NEXT_SECONDS] starten. Ohne nächsten Teil oder bei Netzfehler bleibt es still. */
    private fun offerNext() {
        val current = video
        nextJob?.cancel()
        nextJob = lifecycleScope.launch {
            val next = runCatchingIo { GronkhApi.nextEpisode(current) }
                .onFailure { Log.w(TAG, "Nächster Teil nach ${current.id} nicht gesucht", it) }
                .getOrNull() ?: return@launch
            for (s in UP_NEXT_SECONDS downTo 1) {
                overlayState.upNext = UpNext(next, s)
                delay(1_000)
            }
            startNext(next)
        }
    }

    private fun cancelUpNext() {
        nextJob?.cancel()
        overlayState.upNext = null
    }

    private fun startNext(next: Video) {
        cancelUpNext()
        saveResume()
        play(next)
    }

    /** Öffnet auf dem Einschlaf-Timer wie vor den Kapiteln, damit der eingeübte Griff bleibt; hoch führt zu den Kapiteln. */
    private fun openOptions() {
        view.hideController()
        updateChapter()
        overlayState.optionsCursor = OptionsCursor(
            OptionsRow.SLEEP,
            chapter = chapterAt(overlayState.chapters, player.currentPosition) ?: 0,
            sleep = SLEEP_CHOICES.indexOf(overlayState.sleepChoice).coerceAtLeast(0),
        )
    }

    /** Solange das Optionsfenster offen ist, gehören ihm alle Tasten: Steuerkreuz wählt, OK übernimmt, Zurück schließt. */
    private fun optionsKey(code: Int, cursor: OptionsCursor) {
        when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> applyOption(cursor)
            KeyEvent.KEYCODE_BACK -> overlayState.optionsCursor = null
            else -> overlayState.optionsCursor = cursor.move(code, overlayState.chapters.size)
        }
    }

    /** Übernimmt die Wahl unter [cursor] und schließt das Fenster; OK am Fernseher und Tippen auf dem Handy. */
    private fun applyOption(cursor: OptionsCursor) {
        overlayState.optionsCursor = null
        when (cursor.row) {
            OptionsRow.CHAPTERS -> jumpToChapter(overlayState.chapters[cursor.chapter])
            OptionsRow.SLEEP -> setSleepTimer(SLEEP_CHOICES[cursor.sleep])
        }
    }

    private fun jumpToChapter(chapter: Chapter) {
        player.seekTo(chapter.startMs)
        showTimeBar()
        showPreview()
    }

    private fun updateChapter() {
        val chapters = overlayState.chapters
        overlayState.chapter = chapterAt(chapters, player.currentPosition)?.let(chapters::get)
    }

    /**
     * Läuft der Timer ab: Stelle sichern, pausieren, Player schließen. Den Fernseher abschalten kann eine App nicht;
     * ohne Wiedergabe greifen danach Bildschirmschoner und Ruhezustand des Systems.
     */
    private fun setSleepTimer(minutes: Int) {
        overlayState.sleepChoice = minutes
        sleepJob?.cancel()
        overlayState.sleepLeftMin = null
        if (minutes == 0) return
        val end = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepJob = lifecycleScope.launch {
            while (true) {
                val left = end - SystemClock.elapsedRealtime()
                if (left <= 0) break
                overlayState.sleepLeftMin = ((left + 59_999) / 60_000).toInt()
                // Genau bis zum nächsten Minutenwechsel der Anzeige warten.
                delay((left - 1) % 60_000 + 1)
            }
            overlayState.sleepLeftMin = null
            saveResume()
            player.pause()
            finish()
        }
    }

    private var handledKey: Int? = null
    /** Die offene Leiste kam von einem Sprung zum Höhepunkt; dann springt hoch weiter, statt zu den Knöpfen zu gehen. */
    private var barFromJump = false
    /** Kurzes OK ist gedrückt, aber noch nicht ausgeführt; siehe dispatchKeyEvent. */
    private var okPending = false
    private var sleepJob: Job? = null
    private var nextJob: Job? = null
    /** Was zum laufenden Video nebenher lädt (Höhepunkte, Vorschau); wird beim Wechsel abgebrochen. */
    private var extras: Job? = null
    private var autosave: Job? = null
    /** Seit wann ungebucht gespielt wird (elapsedRealtime); 0 = steht. */
    private var playingSince = 0L
    private var hintJob: Job? = null
    private var previewJob: Job? = null
    private var previews: PreviewSprites? = null
    /** Höhepunkte in ms; null, solange der Chat noch gezählt wird. */
    private var highlights: List<Long>? = null
    private val timeBar by lazy { view.findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress) }
    private val touchUi by lazy { !resources.configuration.isTelevision() }
    private lateinit var overlayView: ComposeView
    private lateinit var pip: PipControls
    private lateinit var layout: PlayerLayout
    private var ticks: ChapterTicks? = null

    /** Spielt weiter, auch nach einem Fehler (prepare setzt an der letzten Stelle fort) oder am Ende (von vorn). */
    private fun resume() {
        when (player.playbackState) {
            Player.STATE_IDLE -> player.prepare()
            Player.STATE_ENDED -> player.seekToDefaultPosition()
        }
        player.play()
    }

    /** Zählt den Chat im Hintergrund; scheitert das, gibt es eben keine Markierungen, die Wiedergabe läuft weiter. */
    private suspend fun loadHighlights(video: Video) {
        val counts = runCatchingIo { ChatHeat.perMinute(cacheDir, video) }
            .onFailure { Log.w(TAG, "Höhepunkte: Chat-Replay von ${video.id} nicht geladen", it) }
            .getOrNull()
        val marks = counts?.let { highlightMinutes(it).map { minute -> minute * 60_000L } } ?: emptyList()
        highlights = marks
        view.setExtraAdGroupMarkers(marks.toLongArray(), BooleanArray(marks.size))
    }

    private fun jumpToHighlight() {
        val marks = highlights ?: return hint("Höhepunkte werden noch gesucht")
        val target = nextHighlightTarget(marks, player.currentPosition) ?: return hint("Kein weiterer Höhepunkt")
        player.seekTo(target)
        showTimeBar()
        barFromJump = true
        showPreview()
    }

    /** Wie die Höhepunkte: ohne Spur oder Sprite gibt es beim Spulen eben kein Bild. */
    private suspend fun loadPreviews(video: Video) {
        previews = runCatchingIo { PreviewSprites.load(video.urls) }
            .onFailure { Log.w(TAG, "Vorschaubilder von ${video.id} nicht geladen", it) }
            .getOrNull()
    }

    /**
     * Zeigt das Bild von [target] über der Zeitleiste, genau dort, wo der Regler jetzt steht. Beim ersten Einblenden
     * hat die Leiste noch keine Breite; post läuft erst nach der anstehenden Layout-Runde, dann einmal nachholen.
     */
    private fun showPreview(target: Long = player.currentPosition, retry: Boolean = true) {
        val sprites = previews ?: return
        val bar = timeBar ?: return
        val duration = player.duration
        if (duration <= 0) return
        if (bar.width == 0) { if (retry) bar.post { showPreview(target, retry = false) }; return }
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            val frame = withContext(Dispatchers.Default) { sprites.frameAt(target) } ?: return@launch
            val barAt = IntArray(2).also(bar::getLocationInWindow)
            // Ursprung des Overlay-Inhalts: das Overlay rückt auf dem Handy um den Kameraeinschnitt ein.
            val origin = IntArray(2).also(overlayView::getLocationInWindow).also { it[0] += overlayView.paddingLeft; it[1] += overlayView.paddingTop }
            val x = barAt[0] - origin[0] + (bar.width * target.coerceAtMost(duration) / duration).toInt()
            overlayState.preview = SeekPreview(frame.asImageBitmap(), formatClock(target), x, barAt[1] - origin[1])
        }
    }

    private fun hint(text: String) {
        overlayState.hint = text
        hintJob?.cancel()
        hintJob = lifecycleScope.launch { delay(HINT_MS); overlayState.hint = null }
    }

    private fun showTimeBar() {
        view.showController()
        timeBar?.requestFocus()
        updateChapter()
    }

    override fun onStart() {
        super.onStart()
        // onStop läuft nicht, wenn die App hart beendet wird (Absturz, Update, Strom weg); dann ginge die Stelle
        // eines stundenlangen Streams verloren. Deshalb auch während der Wiedergabe regelmäßig sichern.
        // Lief das Video im kleinen Fenster bei ausgeschaltetem Bildschirm weiter, sichert noch der alte Takt.
        autosave?.cancel()
        autosave = lifecycleScope.launch {
            while (true) {
                delay(AUTOSAVE_MS)
                // Das Kapitel nachführen, damit die Videoseite im Hochformat mitläuft; die Leiste macht das beim Einblenden.
                if (player.isPlaying) { saveResume(); bookWatchTime(); updateChapter() }
            }
        }
    }

    /** Im kleinen Fenster läuft alles weiter, auch das Sichern und der nächste Teil; gestoppt wird erst beim Schließen. */
    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) stopPlayback()
    }

    private fun stopPlayback() {
        autosave?.cancel()
        // Sonst startete der nächste Teil im Hintergrund.
        cancelUpNext()
        saveResume()
        player.pause()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Ab Android 12 tritt das System per setAutoEnterEnabled selbst ein, auch bei der Wischgeste nach Hause.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && player.playWhenReady) pip.enter()
    }

    /**
     * Im kleinen Fenster nur das Bild: Leiste und Overlay weg, beim Zurück ins Vollbild wieder da. Wird das Fenster
     * geschlossen, ist die Activity schon gestoppt; onStop hat dann noch im Fenster-Modus nicht pausiert, das holt dies nach.
     */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        view.useController = !isInPictureInPictureMode
        overlayView.isVisible = !isInPictureInPictureMode
        applyLayout()
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) stopPlayback()
    }

    private fun saveResume() = resume.save(video.id, player.currentPosition, player.duration)

    /** Bucht die Spielzeit seit dem letzten Mal; der Rest unter einer Sekunde wandert in die nächste Buchung. */
    private fun bookWatchTime() {
        if (playingSince == 0L) return
        val elapsed = SystemClock.elapsedRealtime() - playingSince
        stats.add(video, elapsed / 1000)
        playingSince = if (player.isPlaying) playingSince + elapsed / 1000 * 1000 else 0L
    }

    override fun onDestroy() {
        layout.release()
        pip.release()
        session.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GronkhPlayer"
        private const val EXTRA_VIDEO = "video"
        private const val EXTRA_START = "start"
        private const val SEEK_BACK_MS = 10_000L
        private const val SEEK_FORWARD_MS = 30_000L
        private const val AUTOSAVE_MS = 15_000L
        private const val HINT_MS = 2_500L
        private const val UP_NEXT_SECONDS = 10
        private const val HIGHLIGHT_COLOR = 0xFFB083FF.toInt() // primary der App
        fun intent(context: Context, video: Video, startMs: Long? = null): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_VIDEO, Json.encodeToString(Video.serializer(), video))
                .apply { if (startMs != null) putExtra(EXTRA_START, startMs) }
    }
}
