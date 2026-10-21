package org.streamarchiv.tv.ui

import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import org.junit.Assert.assertEquals
import org.junit.Test

/** Hält die am Fernseher abgestimmte Belegung fest (21.10.2026). */
class PlayerKeysTest {
    private fun key(
        code: Int, bar: Boolean = false, buttons: Boolean = false, failed: Boolean = false, long: Boolean = false, jumped: Boolean = false,
    ) = playerKey(code, bar, buttons, failed, long, jumped)

    @Test fun `links und rechts spulen mit und ohne Leiste`() {
        assertEquals(PlayerKey.SEEK_BACK, key(KEYCODE_DPAD_LEFT))
        assertEquals(PlayerKey.SEEK_FORWARD, key(KEYCODE_DPAD_RIGHT, bar = true))
    }

    @Test fun `auf den Knöpfen wechselt links und rechts den Knopf`() {
        assertEquals(PlayerKey.PASS, key(KEYCODE_DPAD_RIGHT, bar = true, buttons = true))
    }

    @Test fun `runter und zurück blenden die offene Leiste aus`() {
        assertEquals(PlayerKey.HIDE_BAR, key(KEYCODE_DPAD_DOWN, bar = true))
        assertEquals(PlayerKey.HIDE_BAR, key(KEYCODE_BACK, bar = true))
    }

    @Test fun `zurück ohne Leiste verlässt das Video`() {
        assertEquals(PlayerKey.PASS, key(KEYCODE_BACK))
    }

    @Test fun `OK nach dem Spulen spielt weiter statt zu pausieren`() {
        assertEquals(PlayerKey.RESUME_AND_HIDE, key(KEYCODE_DPAD_CENTER, bar = true))
    }

    @Test fun `OK ohne Leiste pausiert`() {
        assertEquals(PlayerKey.TOGGLE_PAUSE, key(KEYCODE_DPAD_CENTER))
    }

    @Test fun `OK nach Abbruch versucht es nochmal`() {
        assertEquals(PlayerKey.RETRY, key(KEYCODE_DPAD_CENTER, bar = true, buttons = true, failed = true))
    }

    @Test fun `hoch bleibt beim Player`() {
        assertEquals(PlayerKey.PASS, key(KEYCODE_DPAD_UP, bar = true))
    }

    @Test fun `langes OK öffnet die Optionen, mit und ohne Leiste`() {
        assertEquals(PlayerKey.OPTIONS, key(KEYCODE_DPAD_CENTER, long = true))
        assertEquals(PlayerKey.OPTIONS, key(KEYCODE_DPAD_CENTER, bar = true, long = true))
    }

    @Test fun `langes OK auf den Knöpfen bleibt beim Knopf`() {
        assertEquals(PlayerKey.PASS, key(KEYCODE_DPAD_CENTER, bar = true, buttons = true, long = true))
    }

    @Test fun `hoch ohne Leiste springt zum nächsten Höhepunkt`() {
        assertEquals(PlayerKey.NEXT_HIGHLIGHT, key(KEYCODE_DPAD_UP))
    }

    @Test fun `hoch springt weiter, solange die Leiste vom Sprung offen ist`() {
        assertEquals(PlayerKey.NEXT_HIGHLIGHT, key(KEYCODE_DPAD_UP, bar = true, jumped = true))
    }
}
