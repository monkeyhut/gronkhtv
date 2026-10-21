package org.streamarchiv.tv.ui

import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import org.junit.Assert.assertEquals
import org.junit.Test

/** Kurzes und langes OK, Halten und Loslassen, wie am Fernseher abgestimmt (21.10.2026). */
class KeyGateTest {
    private val gate = KeyGate()

    /** Drückt [code] wie der Player: nimmt er die Taste, meldet er es, OK hält er bis zum Loslassen zurück. */
    private fun press(code: Int, taken: Boolean = true): KeyStep =
        gate.onKey(code, ACTION_DOWN, 0).also { if (it == KeyStep.ROUTE && taken) gate.handled(code, deferOk = code == KEYCODE_DPAD_CENTER) }

    private fun hold(code: Int, repeat: Int) = gate.onKey(code, ACTION_DOWN, repeat)
    private fun release(code: Int) = gate.onKey(code, ACTION_UP, 0)

    @Test fun `kurzes OK wirkt beim Loslassen`() {
        assertEquals(KeyStep.ROUTE, press(KEYCODE_DPAD_CENTER))
        assertEquals(KeyStep.SHORT_OK, release(KEYCODE_DPAD_CENTER))
    }

    @Test fun `langes OK wirkt bei der ersten Wiederholung, danach nichts mehr`() {
        press(KEYCODE_DPAD_CENTER)
        assertEquals(KeyStep.LONG_OK, hold(KEYCODE_DPAD_CENTER, 1))
        assertEquals(KeyStep.SWALLOW, hold(KEYCODE_DPAD_CENTER, 2))
        assertEquals(KeyStep.SWALLOW, release(KEYCODE_DPAD_CENTER))
    }

    @Test fun `links gehalten spult weiter, das Loslassen wird geschluckt`() {
        press(KEYCODE_DPAD_LEFT)
        assertEquals(KeyStep.ROUTE, hold(KEYCODE_DPAD_LEFT, 1))
        assertEquals(KeyStep.SWALLOW, release(KEYCODE_DPAD_LEFT))
    }

    @Test fun `runter gehalten wiederholt nicht`() {
        press(KEYCODE_DPAD_DOWN)
        assertEquals(KeyStep.SWALLOW, hold(KEYCODE_DPAD_DOWN, 1))
        assertEquals(KeyStep.SWALLOW, release(KEYCODE_DPAD_DOWN))
    }

    @Test fun `eine neue Taste lässt das gedrückte OK verfallen`() {
        press(KEYCODE_DPAD_CENTER)
        press(KEYCODE_DPAD_LEFT)
        assertEquals(KeyStep.PASS, release(KEYCODE_DPAD_CENTER))
        assertEquals(KeyStep.SWALLOW, release(KEYCODE_DPAD_LEFT))
    }

    @Test fun `eine Taste, die der Player nicht nimmt, geht auch beim Loslassen ans System`() {
        assertEquals(KeyStep.ROUTE, press(KEYCODE_BACK, taken = false))
        assertEquals(KeyStep.PASS, release(KEYCODE_BACK))
    }
}
