package app.mmmap.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringUtilsTest {

    @Test fun identicalStringsAreZeroDistance() =
        assertEquals(0.0, jaroWinklerDistance("Noma", "Noma"), 0.0001)

    @Test fun caseInsensitive() =
        assertEquals(0.0, jaroWinklerDistance("Le Gavroche", "LE GAVROCHE"), 0.0001)

    @Test fun completelyDifferentStringsHighDistance() {
        val d = jaroWinklerDistance("Noma", "Chez Pierre")
        assertTrue("expected high distance, got $d", d > 0.5)
    }

    @Test fun closeNamesLowDistance() {
        // "Fat Duck" vs "The Fat Duck" — partial match
        val d = jaroWinklerDistance("Fat Duck", "The Fat Duck")
        assertTrue("expected low distance, got $d", d < 0.4)
    }

    @Test fun emptyVsNonEmptyIsHighDistance() {
        // matchWindow = max(0,4)/2-1 = -1 → returns 1.0
        val d = jaroWinklerDistance("", "Noma")
        assertEquals(1.0, d, 0.0001)
    }

    @Test fun prefixBonusAppliesToSharedPrefix() {
        // "Pierre" vs "Pierre's" share a 4-char prefix bonus
        val withPrefix = jaroWinklerDistance("Pierre", "Pierres")
        val noPrefix = jaroWinklerDistance("Pierre", "Random!")
        assertTrue("shared prefix should produce lower distance", withPrefix < noPrefix)
    }

    @Test fun orderMatters() {
        // Jaro-Winkler is symmetric
        val ab = jaroWinklerDistance("Alain Ducasse", "Ducasse")
        val ba = jaroWinklerDistance("Ducasse", "Alain Ducasse")
        assertEquals(ab, ba, 0.0001)
    }

    @Test fun singleCharStrings() {
        assertEquals(0.0, jaroWinklerDistance("A", "A"), 0.0001)
        // Two single chars that differ: matchWindow = 1/2-1 = -1 → 1.0
        assertEquals(1.0, jaroWinklerDistance("A", "B"), 0.0001)
    }
}
