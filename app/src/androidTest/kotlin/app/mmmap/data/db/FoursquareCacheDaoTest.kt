package app.mmmap.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mmmap.data.db.dao.FoursquareCacheDao
import app.mmmap.data.db.entities.FoursquareCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoursquareCacheDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: FoursquareCacheDao

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.foursquareCacheDao()
    }

    @After fun tearDown() = db.close()

    // ── get ───────────────────────────────────────────────────────────────────

    @Test fun getMissing_returnsNull() = runTest {
        assertNull(dao.get("missing"))
    }

    @Test fun upsertThenGet_returnsEntity() = runTest {
        dao.upsert(entity("r1"))
        assertNotNull(dao.get("r1"))
    }

    @Test fun upsertThenGet_fieldsPreserved() = runTest {
        val e = entity("r1", photoUrl = "https://img.example.com/1.jpg", phone = "+33 1 00 00 00 00")
        dao.upsert(e)
        val result = dao.get("r1")
        assertEquals("https://img.example.com/1.jpg", result?.photoUrl)
        assertEquals("+33 1 00 00 00 00", result?.phone)
        assertEquals("fsq_r1", result?.fsqId)
        assertEquals(9.0, result?.rating)
    }

    // ── upsert ────────────────────────────────────────────────────────────────

    @Test fun upsert_replacesExistingRow() = runTest {
        dao.upsert(entity("r1", photoUrl = "https://old.jpg"))
        dao.upsert(entity("r1", photoUrl = "https://new.jpg"))
        assertEquals("https://new.jpg", dao.get("r1")?.photoUrl)
    }

    @Test fun upsert_doesNotAffectOtherRows() = runTest {
        dao.upsert(entity("r1"))
        dao.upsert(entity("r2"))
        assertNotNull(dao.get("r1"))
        assertNotNull(dao.get("r2"))
    }

    @Test fun upsert_nullableFieldsRoundTrip() = runTest {
        dao.upsert(entity("r1", photoUrl = null, phone = null, hoursJson = null))
        val result = dao.get("r1")!!
        assertNull(result.photoUrl)
        assertNull(result.phone)
        assertNull(result.openingHoursJson)
    }

    // ── TTL logic (via retrieved entity) ─────────────────────────────────────

    @Test fun isPhotoFresh_withinTtl() = runTest {
        val now = System.currentTimeMillis()
        dao.upsert(entity("r1", fetchedAt = now))
        assertTrue(dao.get("r1")!!.isPhotoFresh(now))
    }

    @Test fun isPhotoStale_beyondTtl() = runTest {
        val stale = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
        dao.upsert(entity("r1", fetchedAt = stale))
        assertFalse(dao.get("r1")!!.isPhotoFresh())
    }

    @Test fun isHoursFresh_withinTtl() = runTest {
        val now = System.currentTimeMillis()
        dao.upsert(entity("r1", fetchedAt = now))
        assertTrue(dao.get("r1")!!.isHoursFresh(now))
    }

    @Test fun isHoursStale_beyondTtl() = runTest {
        val stale = System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000
        dao.upsert(entity("r1", fetchedAt = stale))
        assertFalse(dao.get("r1")!!.isHoursFresh())
    }

    @Test fun hoursStalerThanPhoto_photoFreshHoursNot() = runTest {
        // Photo TTL is 30 days, hours TTL is 7 days
        val tenDaysAgo = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000
        dao.upsert(entity("r1", fetchedAt = tenDaysAgo))
        val e = dao.get("r1")!!
        assertTrue(e.isPhotoFresh())
        assertFalse(e.isHoursFresh())
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun entity(
        restaurantId: String,
        photoUrl: String? = "https://example.com/photo.jpg",
        hoursJson: String? = null,
        phone: String? = "+44 20 0000 0000",
        fetchedAt: Long = System.currentTimeMillis(),
    ) = FoursquareCacheEntity(
        restaurantId = restaurantId,
        fsqId = "fsq_$restaurantId",
        photoUrl = photoUrl,
        openingHoursJson = hoursJson,
        phone = phone,
        rating = 9.0,
        fetchedAt = fetchedAt,
    )
}
