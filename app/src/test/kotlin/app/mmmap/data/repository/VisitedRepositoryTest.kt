package app.mmmap.data.repository

import app.mmmap.data.db.dao.VisitedDao
import app.mmmap.data.db.entities.VisitedRestaurantEntity
import app.mmmap.domain.model.Distinction
import app.mmmap.domain.model.Restaurant
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisitedRepositoryTest {

    private val dao: VisitedDao = mockk(relaxed = true)
    private lateinit var repo: VisitedRepository

    private fun restaurant(
        id: String = "abc123",
        name: String = "Test Restaurant",
        distinction: Distinction = Distinction.ONE_STAR,
        cuisine: String? = "French",
    ) = Restaurant(
        id = id,
        name = name,
        address = "1 Test Street",
        location = null,
        latitude = 51.5,
        longitude = -0.1,
        distinction = distinction,
        greenStar = false,
        cuisine = cuisine,
        price = "££",
        phoneNumber = null,
        michelinUrl = "https://guide.michelin.com/test",
        websiteUrl = null,
        description = null,
        facilitiesAndServices = null,
    )

    @Before fun setUp() {
        repo = VisitedRepository(dao)
    }

    @Test fun setVisited_true_insertsEntity() = runTest {
        val slot = slot<VisitedRestaurantEntity>()
        coJustRun { dao.insert(capture(slot)) }
        val r = restaurant(id = "abc123", name = "Le Gavroche",
            distinction = Distinction.THREE_STAR, cuisine = "French")

        repo.setVisited(r, true)

        assertEquals("abc123", slot.captured.restaurantId)
        assertEquals("Le Gavroche", slot.captured.name)
        assertEquals(51.5, slot.captured.latitude, 0.001)
        assertEquals(-0.1, slot.captured.longitude, 0.001)
        assertEquals("3 Stars", slot.captured.award)
        assertEquals("French", slot.captured.cuisine)
    }

    @Test fun setVisited_false_deletesById() = runTest {
        val r = restaurant(id = "abc123")

        repo.setVisited(r, false)

        coVerify { dao.delete("abc123") }
    }

    @Test fun setVisited_false_doesNotInsert() = runTest {
        val r = restaurant()

        repo.setVisited(r, false)

        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test fun visitedIds_mapsListToSet() = runTest {
        every { dao.observeAllIds() } returns flowOf(listOf("a", "b", "c"))

        val ids = repo.visitedIds.first()

        assertEquals(setOf("a", "b", "c"), ids)
    }

    @Test fun visitedIds_emptyWhenNoneVisited() = runTest {
        every { dao.observeAllIds() } returns flowOf(emptyList())

        assertTrue(repo.visitedIds.first().isEmpty())
    }

    @Test fun observeIsVisited_proxiesToDao() = runTest {
        every { dao.observeIsVisited("abc123") } returns flowOf(true)

        assertTrue(repo.observeIsVisited("abc123").first())
    }

    @Test fun observeIsVisited_falseWhenNotPresent() = runTest {
        every { dao.observeIsVisited("xyz") } returns flowOf(false)

        assertFalse(repo.observeIsVisited("xyz").first())
    }

    @Test fun setVisited_true_doesNotCallDelete() = runTest {
        val r = restaurant()

        repo.setVisited(r, true)

        coVerify(exactly = 0) { dao.delete(any()) }
    }
}
