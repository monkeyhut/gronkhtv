package org.streamarchiv.tv.ui

import android.view.KeyEvent

enum class OptionsRow { CHAPTERS, SLEEP }

/** Markierung im Optionsfenster. Jede Zeile behält ihre Wahl, damit hoch und wieder runter nichts verstellt. */
data class OptionsCursor(val row: OptionsRow, val chapter: Int, val sleep: Int)

/**
 * Links/rechts wählt in der Zeile, hoch/runter wechselt die Zeile; die Kapitelzeile gibt es nur mit Kapiteln.
 * Andere Tasten lassen die Markierung stehen, OK und Zurück wertet die Activity aus.
 */
fun OptionsCursor.move(keyCode: Int, chapterCount: Int, sleepCount: Int = SLEEP_CHOICES.size): OptionsCursor {
    val step = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> -1
        KeyEvent.KEYCODE_DPAD_RIGHT -> 1
        KeyEvent.KEYCODE_DPAD_UP -> return if (chapterCount > 0) copy(row = OptionsRow.CHAPTERS) else this
        KeyEvent.KEYCODE_DPAD_DOWN -> return copy(row = OptionsRow.SLEEP)
        else -> return this
    }
    return when (row) {
        OptionsRow.CHAPTERS -> copy(chapter = (chapter + step).coerceIn(0, (chapterCount - 1).coerceAtLeast(0)))
        OptionsRow.SLEEP -> copy(sleep = (sleep + step).coerceIn(0, sleepCount - 1))
    }
}
