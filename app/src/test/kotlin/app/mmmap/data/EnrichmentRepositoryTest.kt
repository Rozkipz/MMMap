package app.mmmap.data

import app.mmmap.data.db.dao.FoursquareCacheDao
import app.mmmap.data.db.entities.FoursquareCacheEntity
import app.mmmap.data.remote.FoursquareApi
import app.mmmap.data.repository.EnrichmentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class EnrichmentRepositoryTest {

    private val api: FoursquareApi = mockk(relaxed = true)
    private val cacheDao: FoursquareCacheDao = mockk(relaxed = true)
    private lateinit var repo: EnrichmentRepository

    private val now = System.currentTimeMillis()

    private fun freshEntity() = FoursquareCacheEntity(
        restaurantId = "r1",
        fsqId = "fsq1",
        photoUrl = "https://example.com/photo.jpg",
        openingHoursJson = null,
        phone = "+44 20 0000 0000",
        rating = 9.2,
        fetchedAt = now,
    )

    private fun staleEntity() = freshEntity().copy(
        fetchedAt = now - 40L * 24 * 60 * 60 * 1000,
    )

    @Before fun setUp() {
        repo = EnrichmentRepository(api, cacheDao)
    }

    @Test fun freshCacheReturnedWithoutApiCall() = runTest {
        coEvery { cacheDao.get("r1") } returns freshEntity()

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertEquals("https://example.com/photo.jpg", detail?.photoUrl)
        assertEquals("+44 20 0000 0000", detail?.phone)
        assertEquals(9.2, detail?.rating)
        coVerify(exactly = 0) { api.searchPlaces(any(), any(), any()) }
    }

    @Test fun nullCacheApiExceptionReturnsNull() = runTest {
        coEvery { cacheDao.get("r1") } returns null
        coEvery { api.searchPlaces(any(), any(), any()) } throws RuntimeException("network error")

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertNull(detail)
    }

    @Test fun staleCacheApiExceptionReturnsStaleDetail() = runTest {
        coEvery { cacheDao.get("r1") } returns staleEntity()
        coEvery { api.searchPlaces(any(), any(), any()) } throws RuntimeException("network error")

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertEquals("https://example.com/photo.jpg", detail?.photoUrl)
    }

    @Test fun photoUrlAndPhoneFromFreshCache() = runTest {
        coEvery { cacheDao.get("r1") } returns freshEntity().copy(
            photoUrl = "https://img.example.com/1.jpg",
            phone = "+33 1 00 00 00 00",
        )

        val detail = repo.get("r1", "Café de Flore", 48.854, 2.332)

        assertEquals("https://img.example.com/1.jpg", detail?.photoUrl)
        assertEquals("+33 1 00 00 00 00", detail?.phone)
    }

    @Test fun ratingNullableInDetail() = runTest {
        coEvery { cacheDao.get("r1") } returns freshEntity().copy(rating = null)

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertNull(detail?.rating)
    }

    @Test fun hoursJsonNullProducesEmptyList() = runTest {
        coEvery { cacheDao.get("r1") } returns freshEntity().copy(openingHoursJson = null)

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertEquals(emptyList<String>(), detail?.openingHours)
    }

    @Test fun malformedHoursJsonProducesEmptyList() = runTest {
        coEvery { cacheDao.get("r1") } returns freshEntity().copy(openingHoursJson = "not valid json{{{")

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertEquals(emptyList<String>(), detail?.openingHours)
    }
}
