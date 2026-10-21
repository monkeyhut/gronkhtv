package org.streamarchiv.tv.ui

import android.view.KeyEvent

/** Was eine Taste der Fernbedienung im Player auslöst. */
enum class PlayerKey { SEEK_BACK, SEEK_FORWARD, HIDE_BAR, RESUME_AND_HIDE, TOGGLE_PAUSE, RETRY, NEXT_HIGHLIGHT, OPTIONS, PASS }

/**
 * Tastenbelegung des Players, als reine Funktion herausgezogen, damit ein Test sie ohne Gerät festhält.
 * Sie ist mehrfach am Fernseher abgestimmt worden; jede Abweichung fällt im Test auf.
 *
 * - Links/rechts spult, außer der Fokus liegt bewusst auf den Knöpfen in der Mitte ([onButtons], per Taste hoch).
 * - Hoch bei geschlossener Leiste springt zum nächsten Höhepunkt; bei offener Leiste führt hoch zu den Knöpfen.
 *   Hat ein Sprung die Leiste geöffnet ([afterJump]), springt hoch weiter, bis sie wieder zu ist.
 * - Runter und Zurück blenden eine offene Leiste samt Abdunklung aus; bei geschlossener Leiste fallen sie durch.
 * - OK bei offener Leiste (nach Spulen oder in der Pause) spielt weiter und blendet aus, sonst pausiert OK.
 * - Langes OK ([longPress]) öffnet die Optionen; kurzes OK wirkt deshalb erst beim Loslassen (siehe PlayerActivity).
 * - Nach einem Wiedergabefehler versucht OK es nochmal.
 */
fun playerKey(
    keyCode: Int,
    barShown: Boolean,
    onButtons: Boolean,
    failed: Boolean,
    longPress: Boolean = false,
    afterJump: Boolean = false,
): PlayerKey = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT -> if (onButtons) PlayerKey.PASS else PlayerKey.SEEK_BACK
    KeyEvent.KEYCODE_DPAD_RIGHT -> if (onButtons) PlayerKey.PASS else PlayerKey.SEEK_FORWARD
    KeyEvent.KEYCODE_DPAD_UP -> if (barShown && !afterJump) PlayerKey.PASS else PlayerKey.NEXT_HIGHLIGHT
    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BACK -> if (barShown) PlayerKey.HIDE_BAR else PlayerKey.PASS
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> when {
        failed -> PlayerKey.RETRY
        onButtons -> PlayerKey.PASS
        longPress -> PlayerKey.OPTIONS
        barShown -> PlayerKey.RESUME_AND_HIDE
        else -> PlayerKey.TOGGLE_PAUSE
    }
    else -> PlayerKey.PASS
}
