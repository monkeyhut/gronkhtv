package org.streamarchiv.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Titel und Schlagwörter echter Suchtreffer vom 21.10.2026. */
class ShowFormatsTest {
    private fun video(title: String, vararg tags: String) =
        Video(id = title, title = title, createdAt = "2026-10-21T18:00:00+02:00", urls = Urls("p"), tags = tags.map(::Tag))

    private fun format(title: String) = SHOW_FORMATS.single { it.title == title }

    private fun kept(format: ShowFormat, vararg hits: Video) = hits.filter(format::belongsTo).map { it.title }

    @Test fun `HORDE behält die Folgen und wirft Erwähnungen raus`() {
        val horde = format("HORDE")
        assertEquals(
            listOf("Heute 18 Uhr Jubiläums-HORDE!", "Die Wilde H.O.R.D.E. Teil 2"),
            kept(
                horde,
                video("Heute 18 Uhr Jubiläums-HORDE!", "Special Events", "H.O.R.D.E."),
                video("Die Wilde H.O.R.D.E. Teil 2", "Specials", "H.O.R.D.E.", "Pen & Paper"),
                video("Heute #FREiAB18 (HORDE wurde verschoben)", "FREiAB18"),
                video("HORDE COMIC Reveal / 90er Werbung", "Kaffeekranz"),
                video("HEUTE ab 18 Uhr Thunderful World Showcase  !ff7 !horde !archiv"),
            ),
        )
    }

    @Test fun `ohne Schlagwörter zählt der Titel`() {
        val freiab = format("#FREiAB18")
        assertEquals(
            listOf("Heute im #FreiAb18 Barotrauma", "Heute #FREiAB1830 D"),
            kept(freiab, video("Heute im #FreiAb18 Barotrauma"), video("Heute #FREiAB1830 D"), video("Streamsonntag")),
        )
    }

    @Test fun `Schlagwort zählt auch ohne Titel`() {
        assertEquals(1, kept(format("#FREiAB18"), video("DU WOLLTEST GERADE HIER KLICKEN", "FREiAB18", "Live LP")).size)
    }

    @Test fun `Ankündigung im fremden Stream gehört nicht dazu`() {
        assertEquals(0, kept(format("#FREiAB18"), video("Nächster Stream am Freitag #FreiAb18", "React")).size)
    }

    @Test fun `Friendly Fire in jeder Schreibweise`() {
        val ff = format("Friendly Fire")
        assertEquals(2, kept(ff, video("FRiENDLY FiRE 11"), video("#FriendlyFire8 friendlyfi.re"), video("Fire Emblem")).size)
    }
}
