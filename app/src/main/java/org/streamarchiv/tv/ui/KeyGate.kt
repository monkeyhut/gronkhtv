package org.streamarchiv.tv.ui

import android.view.KeyEvent

/** Was der Player mit einem Tastenereignis tut, entschieden von [KeyGate]. */
enum class KeyStep {
    /** Neu gedrückt (oder links/rechts gehalten): Belegung auswerten, dann [KeyGate.handled] melden. */
    ROUTE,
    /** Ans System weiterreichen. */
    PASS,
    /** Schlucken, ohne etwas auszulösen. */
    SWALLOW,
    /** Das zurückgehaltene OK auslösen, kurz bzw. lang. */
    SHORT_OK,
    LONG_OK,
}

/**
 * Kurzes und langes OK und das Schlucken behandelter Tasten, als reine Klasse herausgezogen, damit ein Test den Ablauf
 * ohne Gerät festhält.
 *
 * - PlayerView blendet die Leiste bei jedem Steuerkreuz-Ereignis ein, auch beim Loslassen. Das Loslassen einer
 *   behandelten Taste wird deshalb geschluckt; ohne das ging die Leiste nach „runter“ oder OK sofort wieder auf.
 * - OK wartet aufs Loslassen (kurz) oder die erste Wiederholung (lang); die kommt nach der Lang-Druck-Zeit des Systems.
 * - Gedrückt halten wiederholt nur links/rechts; runter oder OK würden sonst flackern bzw. die Leiste zurückholen.
 * - Eine neue Taste, während OK noch gedrückt ist, lässt das OK verfallen, sonst löste sein Loslassen es aus.
 */
class KeyGate {
    private var handledKey: Int? = null
    private var okPending = false

    /** [action] wie KeyEvent.action. */
    fun onKey(code: Int, action: Int, repeatCount: Int): KeyStep {
        if (action == KeyEvent.ACTION_UP && code == handledKey) {
            handledKey = null
            return if (okPending.also { okPending = false }) KeyStep.SHORT_OK else KeyStep.SWALLOW
        }
        if (action != KeyEvent.ACTION_DOWN) return KeyStep.PASS
        if (repeatCount > 0 && code == handledKey) {
            if (okPending) { okPending = false; return KeyStep.LONG_OK }
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return KeyStep.SWALLOW
        }
        okPending = false
        return KeyStep.ROUTE
    }

    /** Nach [KeyStep.ROUTE], wenn der Player die Taste genommen hat; [deferOk] hält ein OK bis zum Loslassen zurück. */
    fun handled(code: Int, deferOk: Boolean) {
        handledKey = code
        okPending = deferOk
    }
}
