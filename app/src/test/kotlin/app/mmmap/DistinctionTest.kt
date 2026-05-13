package app.mmmap

import app.mmmap.domain.model.Distinction
import org.junit.Assert.assertEquals
import org.junit.Test

class DistinctionTest {

    @Test fun threeStarFromAward() =
        assertEquals(Distinction.THREE_STAR, Distinction.fromAward("3 MICHELIN Stars"))

    @Test fun twoStarFromAward() =
        assertEquals(Distinction.TWO_STAR, Distinction.fromAward("2 MICHELIN Stars"))

    @Test fun oneStarFromAward() =
        assertEquals(Distinction.ONE_STAR, Distinction.fromAward("1 MICHELIN Star"))

    @Test fun bibGourmandFromAward() =
        assertEquals(Distinction.BIB_GOURMAND, Distinction.fromAward("Bib Gourmand"))

    @Test fun bibGourmandCaseInsensitive() =
        assertEquals(Distinction.BIB_GOURMAND, Distinction.fromAward("bib gourmand"))

    @Test fun selectedFromExplicitString() =
        assertEquals(Distinction.SELECTED, Distinction.fromAward("Selected Restaurants"))

    @Test fun selectedFromNull() =
        assertEquals(Distinction.SELECTED, Distinction.fromAward(null))

    @Test fun selectedFromEmpty() =
        assertEquals(Distinction.SELECTED, Distinction.fromAward(""))

    @Test fun selectedFromUnknown() =
        assertEquals(Distinction.SELECTED, Distinction.fromAward("Green Star"))

    @Test fun numericStringThreeStar() =
        assertEquals(Distinction.THREE_STAR, Distinction.fromAward("3"))

    @Test fun numericStringOneStar() =
        assertEquals(Distinction.ONE_STAR, Distinction.fromAward("1 Star"))
}
