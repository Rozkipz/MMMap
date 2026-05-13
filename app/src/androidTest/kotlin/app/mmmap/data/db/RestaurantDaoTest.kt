package app.mmmap.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mmmap.data.db.dao.RestaurantDao
import app.mmmap.data.db.entities.RestaurantEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestaurantDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RestaurantDao

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.restaurantDao()
    }

    @After fun tearDown() = db.close()

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun entity(
        id: String,
        lat: Double,
        lon: Double,
        award: String? = "1 MICHELIN Star",
        cuisine: String? = "French",
        price: String? = "£££",
    ) = RestaurantEntity(
        id = id,
        name = "Restaurant $id",
        address = "1 Test St",
        location = null,
        latitude = lat,
        longitude = lon,
        award = award,
        greenStar = false,
        cuisine = cuisine,
        price = price,
        phoneNumber = null,
        url = "https://guide.michelin.com/$id",
        websiteUrl = null,
        description = null,
        facilitiesAndServices = null,
    )

    private suspend fun insert(vararg entities: RestaurantEntity) =
        entities.forEach { db.openHelper.writableDatabase.also { _ ->
            // use Room insert via raw SQL for test data setup
        } }

    // Use Room's insert directly via a writable DAO workaround: just upsert via raw DB
    private fun insertAll(vararg entities: RestaurantEntity) {
        val db2 = db.openHelper.writableDatabase
        entities.forEach { e ->
            db2.execSQL(
                """INSERT OR REPLACE INTO restaurant
                   (id, name, address, location, latitude, longitude, award, greenStar,
                    cuisine, price, phoneNumber, url, websiteUrl, description, facilitiesAndServices)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                arrayOf(e.id, e.name, e.address, e.location, e.latitude, e.longitude,
                    e.award, if (e.greenStar) 1 else 0,
                    e.cuisine, e.price, e.phoneNumber, e.url,
                    e.websiteUrl, e.description, e.facilitiesAndServices)
            )
        }
    }

    // ── count ─────────────────────────────────────────────────────────────────

    @Test fun countEmptyDatabase() = runTest {
        assertEquals(0, dao.count())
    }

    @Test fun countMatchesInserted() = runTest {
        insertAll(entity("r1", 51.5, -0.1), entity("r2", 51.6, -0.2))
        assertEquals(2, dao.count())
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test fun getByIdReturnsRow() = runTest {
        insertAll(entity("r1", 51.5, -0.1))
        assertNotNull(dao.getById("r1"))
        assertEquals("r1", dao.getById("r1")?.id)
    }

    @Test fun getByIdMissingReturnsNull() = runTest {
        assertNull(dao.getById("missing"))
    }

    // ── observeInBounds ───────────────────────────────────────────────────────

    @Test fun boundsIncludesMatchingRow() = runTest {
        insertAll(entity("r1", 51.5, -0.1))
        val rows = dao.observeInBounds(51.0, 52.0, -1.0, 0.0, null, null, null).first()
        assertEquals(1, rows.size)
        assertEquals("r1", rows[0].id)
    }

    @Test fun boundsExcludesOutsideRow() = runTest {
        insertAll(entity("r1", 48.8, 2.3)) // Paris, outside London bounds
        val rows = dao.observeInBounds(51.0, 52.0, -1.0, 0.0, null, null, null).first()
        assertTrue(rows.isEmpty())
    }

    @Test fun boundsOnlyReturnsInRange() = runTest {
        insertAll(
            entity("in",  51.5, -0.1),
            entity("out", 48.8,  2.3),
        )
        val rows = dao.observeInBounds(51.0, 52.0, -1.0, 0.0, null, null, null).first()
        assertEquals(1, rows.size)
        assertEquals("in", rows[0].id)
    }

    @Test fun awardFilterMatchesExact() = runTest {
        insertAll(
            entity("star1", 51.5, -0.1, award = "1 MICHELIN Star"),
            entity("star3", 51.6, -0.2, award = "3 MICHELIN Stars"),
        )
        val rows = dao.observeInBounds(51.0, 52.0, -1.0, 0.0, "1 MICHELIN Star", null, null).first()
        assertEquals(1, rows.size)
        assertEquals("star1", rows[0].id)
    }

    @Test fun awardFilterNullReturnsAll() = runTest {
        insertAll(
            entity("r1", 51.5, -0.1, award = "1 MICHELIN Star"),
            entity("r2", 51.6, -0.2, award = "Bib Gourmand"),
        )
        val rows = dao.observeInBounds(51.0, 52.0, -1.0, 0.0, null, null, null).first()
        assertEquals(2, rows.size)
    }

    @Test fun cuisineFilterMatchesExact() = runTest {
        insertAll(
            entity("r1", 51.5, -0.1, cuisine = "French"),
            entity("r2", 51.6, -0.2, cuisine = "Japanese"),
        )
        val rows = dao.observeInBounds(51.0, 52.0, -1.0, 0.0, null, "French", null).first()
        assertEquals(1, rows.size)
        assertEquals("r1", rows[0].id)
    }

    @Test fun priceFilterMatchesExact() = runTest {
        insertAll(
            entity("cheap", 51.5, -0.1, price = "£"),
            entity("pricey", 51.6, -0.2, price = "££££"),
        )
        val rows = dao.observeInBounds(51.0, 52.0, -1.0, 0.0, null, null, "£").first()
        assertEquals(1, rows.size)
        assertEquals("cheap", rows[0].id)
    }

    @Test fun combinedFiltersApplyAndSemantics() = runTest {
        insertAll(
            entity("match",    51.5, -0.1, award = "1 MICHELIN Star", cuisine = "French", price = "£££"),
            entity("no_award", 51.6, -0.2, award = "Bib Gourmand",    cuisine = "French", price = "£££"),
            entity("no_cuisine",51.4,-0.05, award = "1 MICHELIN Star", cuisine = "Italian",price = "£££"),
        )
        val rows = dao.observeInBounds(51.0, 52.0, -1.0, 0.0, "1 MICHELIN Star", "French", "£££").first()
        assertEquals(1, rows.size)
        assertEquals("match", rows[0].id)
    }

    @Test fun resultsOrderedByName() = runTest {
        insertAll(
            entity("r2", 51.5, -0.1).copy(name = "Zuma"),
            entity("r1", 51.6, -0.2).copy(name = "Alain Ducasse"),
        )
        val rows = dao.observeInBounds(51.0, 52.0, -1.0, 0.0, null, null, null).first()
        assertEquals("r1", rows[0].id)
        assertEquals("r2", rows[1].id)
    }

    // ── distinctCuisines / distinctPrices ─────────────────────────────────────

    @Test fun distinctCuisinesIgnoresNull() = runTest {
        insertAll(
            entity("r1", 51.5, -0.1, cuisine = "French"),
            entity("r2", 51.6, -0.2, cuisine = null),
            entity("r3", 51.4, -0.0, cuisine = "French"),
        )
        val cuisines = dao.distinctCuisines()
        assertEquals(listOf("French"), cuisines)
    }

    @Test fun distinctCuisinesAlphabetical() = runTest {
        insertAll(
            entity("r1", 51.5, -0.1, cuisine = "Japanese"),
            entity("r2", 51.6, -0.2, cuisine = "French"),
        )
        assertEquals(listOf("French", "Japanese"), dao.distinctCuisines())
    }

    @Test fun distinctPricesSorted() = runTest {
        insertAll(
            entity("r1", 51.5, -0.1, price = "£££"),
            entity("r2", 51.6, -0.2, price = "£"),
            entity("r3", 51.4, -0.0, price = "££"),
        )
        assertEquals(listOf("£", "££", "£££"), dao.distinctPrices())
    }

    @Test fun distinctCuisinesEmptyWhenNoCuisineSet() = runTest {
        insertAll(entity("r1", 51.5, -0.1, cuisine = null))
        assertTrue(dao.distinctCuisines().isEmpty())
    }
}
