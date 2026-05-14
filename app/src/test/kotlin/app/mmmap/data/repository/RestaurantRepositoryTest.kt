package app.mmmap.data.repository

import app.mmmap.data.db.dao.RestaurantDao
import app.mmmap.data.db.entities.RestaurantEntity
import app.mmmap.domain.model.Distinction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RestaurantRepositoryTest {

    private val dao: RestaurantDao = mockk(relaxed = true)
    private lateinit var repo: RestaurantRepository

    @Before fun setUp() {
        repo = RestaurantRepository(dao)
    }

    // ── observeInBounds ───────────────────────────────────────────────────────

    @Test fun observeInBounds_mapsEntitiesToDomain() = runTest {
        coEvery { dao.observeInBounds(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
            listOf(entity("r1"), entity("r2"))
        )

        val result = repo.observeInBounds(50.0, 52.0, -1.0, 1.0).first()

        assertEquals(2, result.size)
        assertEquals("r1", result[0].id)
        assertEquals("r2", result[1].id)
    }

    @Test fun observeInBounds_entityFieldsMappedCorrectly() = runTest {
        coEvery { dao.observeInBounds(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
            listOf(entity("r1", name = "Le Gavroche", award = "3 MICHELIN Stars", cuisine = "French", price = "££££"))
        )

        val domain = repo.observeInBounds(50.0, 52.0, -1.0, 1.0).first().single()

        assertEquals("Le Gavroche", domain.name)
        assertEquals(Distinction.THREE_STAR, domain.distinction)
        assertEquals("French", domain.cuisine)
        assertEquals("££££", domain.price)
    }

    @Test fun observeInBounds_nullFilters_passesAllFlagsToDao() = runTest {
        coEvery { dao.observeInBounds(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(emptyList())

        repo.observeInBounds(minLat = 50.0, maxLat = 52.0, minLon = -1.0, maxLon = 1.0).first()

        coVerify { dao.observeInBounds(50.0, 52.0, -1.0, 1.0, null, 1, any(), 1, any()) }
    }

    @Test fun observeInBounds_cuisineSet_passedToDao() = runTest {
        coEvery { dao.observeInBounds(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(emptyList())

        repo.observeInBounds(50.0, 52.0, -1.0, 1.0, cuisines = setOf("French", "Italian")).first()

        coVerify { dao.observeInBounds(50.0, 52.0, -1.0, 1.0, null, 0, match { it.containsAll(listOf("French", "Italian")) }, 1, any()) }
    }

    @Test fun observeInBounds_priceTierSet_passedToDao() = runTest {
        coEvery { dao.observeInBounds(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(emptyList())

        repo.observeInBounds(50.0, 52.0, -1.0, 1.0, priceTiers = setOf(2, 3)).first()

        coVerify { dao.observeInBounds(50.0, 52.0, -1.0, 1.0, null, 1, any(), 0, match { it.containsAll(listOf(2, 3)) }) }
    }

    @Test fun observeInBounds_emptyCuisineSet_passedWithSentinel() = runTest {
        coEvery { dao.observeInBounds(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(emptyList())

        repo.observeInBounds(50.0, 52.0, -1.0, 1.0, cuisines = emptySet()).first()

        coVerify { dao.observeInBounds(50.0, 52.0, -1.0, 1.0, null, 0, listOf(""), 1, any()) }
    }

    @Test fun observeInBounds_emptyResultReturnsEmptyList() = runTest {
        coEvery { dao.observeInBounds(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(emptyList())

        val result = repo.observeInBounds(50.0, 52.0, -1.0, 1.0).first()

        assertEquals(emptyList<Any>(), result)
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test fun getById_returnsMappedDomain() = runTest {
        coEvery { dao.getById("r1") } returns entity("r1", name = "Noma", cuisine = "Nordic")

        val result = repo.getById("r1")

        assertEquals("r1", result?.id)
        assertEquals("Noma", result?.name)
        assertEquals("Nordic", result?.cuisine)
    }

    @Test fun getById_returnsNullForMissing() = runTest {
        coEvery { dao.getById("missing") } returns null

        assertNull(repo.getById("missing"))
    }

    // ── count ─────────────────────────────────────────────────────────────────

    @Test fun count_delegatesToDao() = runTest {
        coEvery { dao.count() } returns 42

        assertEquals(42, repo.count())
    }

    // ── distinctCuisines / distinctPrices ─────────────────────────────────────

    @Test fun distinctCuisines_delegatesToDao() = runTest {
        coEvery { dao.distinctCuisines() } returns listOf("French", "Japanese")

        assertEquals(listOf("French", "Japanese"), repo.distinctCuisines())
    }

    @Test fun distinctPrices_delegatesToDao() = runTest {
        coEvery { dao.distinctPrices() } returns listOf("£", "££", "£££")

        assertEquals(listOf("£", "££", "£££"), repo.distinctPrices())
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun entity(
        id: String,
        name: String = "Restaurant $id",
        award: String? = "1 MICHELIN Star",
        cuisine: String? = "French",
        price: String? = "£££",
    ) = RestaurantEntity(
        id = id, name = name, address = "1 Test St", location = null,
        latitude = 51.5, longitude = -0.1,
        award = award, greenStar = false,
        cuisine = cuisine, price = price, phoneNumber = null,
        url = "https://guide.michelin.com/$id",
        websiteUrl = null, description = null, facilitiesAndServices = null,
    )
}
