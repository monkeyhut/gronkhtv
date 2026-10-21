package org.streamarchiv.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerLayoutTest {
    @Test fun `aufrecht und kopfüber ist hochkant`() {
        listOf(0, 20, 340, 180, 165).forEach { assertEquals("$it Grad", false, heldLandscape(it)) }
    }

    @Test fun `auf der Seite ist quer, in beide Richtungen`() {
        listOf(90, 270, 70, 250).forEach { assertEquals("$it Grad", true, heldLandscape(it)) }
    }

    @Test fun `schräg dazwischen und flach liegend entscheidet nichts`() {
        listOf(45, 135, 225, 315, -1).forEach { assertEquals("$it Grad", null, heldLandscape(it)) }
    }
}
