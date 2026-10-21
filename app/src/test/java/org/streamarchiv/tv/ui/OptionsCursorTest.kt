package org.streamarchiv.tv.ui

import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import org.junit.Assert.assertEquals
import org.junit.Test

class OptionsCursorTest {
    private val start = OptionsCursor(OptionsRow.SLEEP, chapter = 2, sleep = 0)

    @Test fun `links und rechts wählen in der Zeile, am Rand ist Schluss`() {
        assertEquals(1, start.move(KEYCODE_DPAD_RIGHT, chapterCount = 4).sleep)
        assertEquals(0, start.move(KEYCODE_DPAD_LEFT, chapterCount = 4).sleep)
        val chapters = start.copy(row = OptionsRow.CHAPTERS, chapter = 3)
        assertEquals(3, chapters.move(KEYCODE_DPAD_RIGHT, chapterCount = 4).chapter)
    }

    @Test fun `hoch und runter wechseln die Zeile, jede behält ihre Wahl`() {
        val up = start.move(KEYCODE_DPAD_UP, chapterCount = 4)
        assertEquals(OptionsCursor(OptionsRow.CHAPTERS, chapter = 2, sleep = 0), up)
        assertEquals(start, up.move(KEYCODE_DPAD_DOWN, chapterCount = 4))
    }

    @Test fun `ohne Kapitel bleibt hoch beim Timer`() {
        assertEquals(start, start.move(KEYCODE_DPAD_UP, chapterCount = 0))
    }
}
