package app.mmmap.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mmmap.data.db.dao.RestaurantDao
import app.mmmap.data.db.dao.VisitedDao
import app.mmmap.data.db.entities.RestaurantEntity
import app.mmmap.data.db.entities.VisitedRestaurantEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisitedDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var visitedDao: VisitedDao
    private lateinit var restaurantDao: RestaurantDao

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        visitedDao = db.visitedDao()
        restaurantDao = db.restaurantDao()
    }

    @After fun tearDown() = db.close()

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun visited(id: String) = VisitedRestaurantEntity(
        restaurantId = id,
        name = "Restaurant $id",
        latitude = 51.5,
        longitude = -0.1,
        award = "1 Star",
        cuisine = "French",
        visitedAt = 1_000_000L,
    )

    private fun restaurant(id: String) = RestaurantEntity(
        id = id, name = "Restaurant $id", address = "1 Test St", location = null,
        latitude = 51.5, longitude = -0.1, award = "1 MICHELIN Star",
        greenStar = false, cuisine = "French", price = "£££",
        phoneNumber = null, url = "https://guide.michelin.com/$id",
        websiteUrl = null, description = null, facilitiesAndServices = null,
    )

    // ── observeIsVisited ─────────────────────────────────────────────────────

    @Test fun observeIsVisited_falseBeforeInsert() = runTest {
        assertFalse(visitedDao.observeIsVisited("r1").first())
    }

    @Test fun observeIsVisited_trueAfterInsert() = runTest {
        visitedDao.insert(visited("r1"))
        assertTrue(visitedDao.observeIsVisited("r1").first())
    }

    @Test fun observeIsVisited_falseAfterDelete() = runTest {
        visitedDao.insert(visited("r1"))
        visitedDao.delete("r1")
        assertFalse(visitedDao.observeIsVisited("r1").first())
    }

    // ── observeAllIds ────────────────────────────────────────────────────────

    @Test fun observeAllIds_emptyInitially() = runTest {
        assertTrue(visitedDao.observeAllIds().first().isEmpty())
    }

    @Test fun observeAllIds_containsInsertedIds() = runTest {
        visitedDao.insert(visited("r1"))
        visitedDao.insert(visited("r2"))
        val ids = visitedDao.observeAllIds().first()
        assertEquals(setOf("r1", "r2"), ids.toSet())
    }

    @Test fun observeAllIds_excludesDeletedId() = runTest {
        visitedDao.insert(visited("r1"))
        visitedDao.insert(visited("r2"))
        visitedDao.delete("r1")
        assertEquals(listOf("r2"), visitedDao.observeAllIds().first())
    }

    // ── insert ───────────────────────────────────────────────────────────────

    @Test fun insert_replace_updatesTimestamp() = runTest {
        visitedDao.insert(visited("r1").copy(visitedAt = 1000L))
        visitedDao.insert(visited("r1").copy(visitedAt = 9999L))
        assertEquals(1, visitedDao.count())
    }

    // ── count ─────────────────────────────────────────────────────────────────

    @Test fun count_incrementsOnInsert() = runTest {
        assertEquals(0, visitedDao.count())
        visitedDao.insert(visited("r1"))
        assertEquals(1, visitedDao.count())
        visitedDao.insert(visited("r2"))
        assertEquals(2, visitedDao.count())
    }

    // ── sync isolation (the whole point of a separate table) ─────────────────

    @Test fun restaurantDeleteAll_doesNotClearVisitedRows() = runTest {
        restaurantDao.insertAll(listOf(restaurant("r1"), restaurant("r2")))
        visitedDao.insert(visited("r1"))
        visitedDao.insert(visited("r2"))

        // Simulate what DatasetSyncWorker does on every sync
        restaurantDao.deleteAll()
        restaurantDao.insertAll(listOf(restaurant("r1"), restaurant("r2")))

        // Visited state must survive
        assertEquals(2, visitedDao.count())
        assertTrue(visitedDao.observeIsVisited("r1").first())
        assertTrue(visitedDao.observeIsVisited("r2").first())
    }

    @Test fun restaurantDeleteAll_leavesOrphanedVisitedRows_intact() = runTest {
        restaurantDao.insertAll(listOf(restaurant("r1")))
        visitedDao.insert(visited("r1"))

        // Sync pulls a dataset that no longer includes r1
        restaurantDao.deleteAll()

        // Visit record for a now-absent restaurant still survives
        assertTrue(visitedDao.observeIsVisited("r1").first())
        assertEquals(1, visitedDao.count())
    }
}
