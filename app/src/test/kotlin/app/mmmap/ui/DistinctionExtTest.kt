package app.mmmap.ui

import app.mmmap.domain.model.Distinction
import app.mmmap.ui.theme.BibBrown
import app.mmmap.ui.theme.BibGreen
import app.mmmap.ui.theme.MichelinRed
import app.mmmap.ui.theme.OneStarCyan
import app.mmmap.ui.theme.SelectedBlue
import app.mmmap.ui.theme.StarGold
import app.mmmap.ui.theme.TwoStarViolet
import org.junit.Assert.assertEquals
import org.junit.Test

class DistinctionExtTest {

    // ── shortLabel ────────────────────────────────────────────────────────────

    @Test fun shortLabel_threeStar()   = assertEquals("★★★",      Distinction.THREE_STAR.shortLabel())
    @Test fun shortLabel_twoStar()     = assertEquals("★★",        Distinction.TWO_STAR.shortLabel())
    @Test fun shortLabel_oneStar()     = assertEquals("★",          Distinction.ONE_STAR.shortLabel())
    @Test fun shortLabel_bibGourmand() = assertEquals("Bib",        Distinction.BIB_GOURMAND.shortLabel())
    @Test fun shortLabel_selected()    = assertEquals("Selected",   Distinction.SELECTED.shortLabel())

    // ── chipLabel ─────────────────────────────────────────────────────────────

    @Test fun chipLabel_threeStar()   = assertEquals("3★",       Distinction.THREE_STAR.chipLabel())
    @Test fun chipLabel_twoStar()     = assertEquals("2★",       Distinction.TWO_STAR.chipLabel())
    @Test fun chipLabel_oneStar()     = assertEquals("1★",       Distinction.ONE_STAR.chipLabel())
    @Test fun chipLabel_bibGourmand() = assertEquals("Bib",      Distinction.BIB_GOURMAND.chipLabel())
    @Test fun chipLabel_selected()    = assertEquals("Selected", Distinction.SELECTED.chipLabel())

    // ── dotColor ──────────────────────────────────────────────────────────────

    @Test fun dotColor_threeStar()   = assertEquals(MichelinRed,   Distinction.THREE_STAR.dotColor())
    @Test fun dotColor_twoStar()     = assertEquals(TwoStarViolet, Distinction.TWO_STAR.dotColor())
    @Test fun dotColor_oneStar()     = assertEquals(OneStarCyan,   Distinction.ONE_STAR.dotColor())
    @Test fun dotColor_bibGourmand() = assertEquals(BibGreen,      Distinction.BIB_GOURMAND.dotColor())
    @Test fun dotColor_selected()    = assertEquals(SelectedBlue,  Distinction.SELECTED.dotColor())

    // ── badgeLabel ────────────────────────────────────────────────────────────

    @Test fun badgeLabel_threeStar()   = assertEquals("★★★  3 Stars",    Distinction.THREE_STAR.badgeLabel())
    @Test fun badgeLabel_twoStar()     = assertEquals("★★  2 Stars",     Distinction.TWO_STAR.badgeLabel())
    @Test fun badgeLabel_oneStar()     = assertEquals("★  1 Star",       Distinction.ONE_STAR.badgeLabel())
    @Test fun badgeLabel_bibGourmand() = assertEquals("Bib Gourmand",    Distinction.BIB_GOURMAND.badgeLabel())
    @Test fun badgeLabel_selected()    = assertEquals("MICHELIN Selected", Distinction.SELECTED.badgeLabel())

    // ── badgeColor ────────────────────────────────────────────────────────────

    @Test fun badgeColor_threeStar()   = assertEquals(StarGold,   Distinction.THREE_STAR.badgeColor())
    @Test fun badgeColor_twoStar()     = assertEquals(StarGold,   Distinction.TWO_STAR.badgeColor())
    @Test fun badgeColor_oneStar()     = assertEquals(StarGold,   Distinction.ONE_STAR.badgeColor())
    @Test fun badgeColor_bibGourmand() = assertEquals(BibBrown,   Distinction.BIB_GOURMAND.badgeColor())
    @Test fun badgeColor_selected()    = assertEquals(SelectedBlue, Distinction.SELECTED.badgeColor())
}
