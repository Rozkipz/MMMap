package app.mmmap

import app.mmmap.data.db.entities.FoursquareCacheEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoursquareCacheEntityTest {

    private val DAY_MS = 24 * 60 * 60 * 1000L

    private fun entity(fetchedAt: Long) = FoursquareCacheEntity(
        restaurantId = "r1",
        fsqId = "fsq1",
        photoUrl = null,
        openingHoursJson = null,
        phone = null,
        rating = null,
        fetchedAt = fetchedAt,
    )

    @Test fun photoFreshWithinThirtyDays() {
        val now = System.currentTimeMillis()
        assertTrue(entity(now - 29 * DAY_MS).isPhotoFresh(now))
    }

    @Test fun photoStaleAfterThirtyDays() {
        val now = System.currentTimeMillis()
        assertFalse(entity(now - 31 * DAY_MS).isPhotoFresh(now))
    }

    @Test fun hoursFreshWithinSevenDays() {
        val now = System.currentTimeMillis()
        assertTrue(entity(now - 6 * DAY_MS).isHoursFresh(now))
    }

    @Test fun hoursStaleAfterSevenDays() {
        val now = System.currentTimeMillis()
        assertFalse(entity(now - 8 * DAY_MS).isHoursFresh(now))
    }

    @Test fun freshNow() {
        val now = System.currentTimeMillis()
        val e = entity(now)
        assertTrue(e.isPhotoFresh(now))
        assertTrue(e.isHoursFresh(now))
    }

    @Test fun staleForBoth() {
        val now = System.currentTimeMillis()
        val e = entity(now - 31 * DAY_MS)
        assertFalse(e.isPhotoFresh(now))
        assertFalse(e.isHoursFresh(now))
    }
}
