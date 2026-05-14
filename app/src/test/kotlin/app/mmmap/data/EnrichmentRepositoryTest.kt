package app.mmmap.data

import app.mmmap.data.db.dao.FoursquareCacheDao
import app.mmmap.data.db.entities.FoursquareCacheEntity
import app.mmmap.data.prefs.ApiKeyPreferences
import app.mmmap.data.remote.FoursquareApi
import app.mmmap.data.remote.models.FsqHours
import app.mmmap.data.remote.models.FsqPhoto
import app.mmmap.data.remote.models.FsqPlaceResponse
import app.mmmap.data.remote.models.FsqSearchResponse
import app.mmmap.data.remote.models.FsqSearchResult
import app.mmmap.data.repository.EnrichmentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnrichmentRepositoryTest {

    private val api: FoursquareApi = mockk(relaxed = true)
    private val cacheDao: FoursquareCacheDao = mockk(relaxed = true)
    private val apiKeyPrefs: ApiKeyPreferences = mockk(relaxed = true)
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
        every { apiKeyPrefs.fsqApiKey } returns flowOf(null)
        repo = EnrichmentRepository(api, cacheDao, apiKeyPrefs)
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

    // ── API success path ──────────────────────────────────────────────────────

    @Test fun staleCacheApiSuccess_returnsNewPhotoAndPhone() = runTest {
        every { apiKeyPrefs.fsqApiKey } returns flowOf("test-key")
        coEvery { cacheDao.get("r1") } returns staleEntity()
        coEvery { api.searchPlaces(any(), any(), any()) } returns FsqSearchResponse(
            results = listOf(FsqSearchResult(fsqId = "fsq_new", name = "Test", distance = 50))
        )
        coEvery { api.getPlace(any(), "fsq_new") } returns FsqPlaceResponse(
            fsqId = "fsq_new",
            photos = listOf(FsqPhoto(prefix = "https://img.example.com/", suffix = "/img.jpg")),
            tel = "+44 20 1234 5678",
            rating = 9.5,
        )

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertEquals("https://img.example.com/800x800/img.jpg", detail?.photoUrl)
        assertEquals("+44 20 1234 5678", detail?.phone)
        assertEquals(9.5, detail?.rating)
    }

    @Test fun staleCacheApiSuccess_cacheIsUpserted() = runTest {
        every { apiKeyPrefs.fsqApiKey } returns flowOf("test-key")
        coEvery { cacheDao.get("r1") } returns staleEntity()
        coEvery { api.searchPlaces(any(), any(), any()) } returns FsqSearchResponse(
            results = listOf(FsqSearchResult(fsqId = "fsq_new", name = "Test", distance = 50))
        )
        coEvery { api.getPlace(any(), "fsq_new") } returns FsqPlaceResponse(fsqId = "fsq_new")

        repo.get("r1", "Test", 51.5, -0.1)

        coVerify { cacheDao.upsert(match { it.restaurantId == "r1" && it.fsqId == "fsq_new" }) }
    }

    @Test fun nullCacheApiSuccess_returnsDetail() = runTest {
        every { apiKeyPrefs.fsqApiKey } returns flowOf("test-key")
        coEvery { cacheDao.get("r1") } returns null
        coEvery { api.searchPlaces(any(), any(), any()) } returns FsqSearchResponse(
            results = listOf(FsqSearchResult(fsqId = "fsq_new", name = "Test", distance = 100))
        )
        coEvery { api.getPlace(any(), "fsq_new") } returns FsqPlaceResponse(
            fsqId = "fsq_new", rating = 8.0
        )

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertEquals(8.0, detail?.rating)
    }

    @Test fun resultsOutsideRadius_returnsStaleCacheWithoutGetPlace() = runTest {
        every { apiKeyPrefs.fsqApiKey } returns flowOf("test-key")
        coEvery { cacheDao.get("r1") } returns staleEntity()
        coEvery { api.searchPlaces(any(), any(), any()) } returns FsqSearchResponse(
            results = listOf(FsqSearchResult(fsqId = "fsq_far", name = "Test", distance = 300))
        )

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertEquals("https://example.com/photo.jpg", detail?.photoUrl)
        coVerify(exactly = 0) { api.getPlace(any(), any()) }
    }

    @Test fun emptySearchResults_returnsStaleCacheWithoutGetPlace() = runTest {
        every { apiKeyPrefs.fsqApiKey } returns flowOf("test-key")
        coEvery { cacheDao.get("r1") } returns staleEntity()
        coEvery { api.searchPlaces(any(), any(), any()) } returns FsqSearchResponse(results = emptyList())

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertEquals("https://example.com/photo.jpg", detail?.photoUrl)
        coVerify(exactly = 0) { api.getPlace(any(), any()) }
    }

    @Test fun apiSuccess_hoursAndOpenNowParsed() = runTest {
        every { apiKeyPrefs.fsqApiKey } returns flowOf("test-key")
        coEvery { cacheDao.get("r1") } returns null
        coEvery { api.searchPlaces(any(), any(), any()) } returns FsqSearchResponse(
            results = listOf(FsqSearchResult(fsqId = "fsq_new", name = "Test", distance = 50))
        )
        coEvery { api.getPlace(any(), "fsq_new") } returns FsqPlaceResponse(
            fsqId = "fsq_new",
            hours = FsqHours(openNow = true, display = "Mon-Fri 9:00-17:00\nSat 10:00-16:00"),
        )

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertTrue(detail?.isOpenNow == true)
        assertEquals(listOf("Mon-Fri 9:00-17:00", "Sat 10:00-16:00"), detail?.openingHours)
    }

    @Test fun nullSearchResultDistance_resultExcluded() = runTest {
        every { apiKeyPrefs.fsqApiKey } returns flowOf("test-key")
        coEvery { cacheDao.get("r1") } returns null
        coEvery { api.searchPlaces(any(), any(), any()) } returns FsqSearchResponse(
            results = listOf(FsqSearchResult(fsqId = "fsq_nodist", name = "Test", distance = null))
        )

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertNull(detail)
        coVerify(exactly = 0) { api.getPlace(any(), any()) }
    }

    // ── Jaro-Winkler best-name ranking ────────────────────────────────────────

    @Test fun multipleResults_closestNameMatchChosen() = runTest {
        every { apiKeyPrefs.fsqApiKey } returns flowOf("test-key")
        coEvery { cacheDao.get("r1") } returns null
        coEvery { api.searchPlaces(any(), any(), any()) } returns FsqSearchResponse(
            results = listOf(
                FsqSearchResult(fsqId = "fsq_wrong", name = "Completely Different Place", distance = 50),
                FsqSearchResult(fsqId = "fsq_right", name = "Le Gavroche",               distance = 80),
            )
        )
        coEvery { api.getPlace(any(), any()) } returns FsqPlaceResponse(fsqId = "fsq_right", rating = 9.0)

        repo.get("r1", "Le Gavroche", 51.5, -0.1)

        coVerify { api.getPlace(any(), "fsq_right") }
        coVerify(exactly = 0) { api.getPlace(any(), "fsq_wrong") }
    }

    @Test fun multipleResults_beyondRadiusExcludedBeforeRanking() = runTest {
        every { apiKeyPrefs.fsqApiKey } returns flowOf("test-key")
        coEvery { cacheDao.get("r1") } returns null
        // Perfect name match but too far; imperfect name match within range
        coEvery { api.searchPlaces(any(), any(), any()) } returns FsqSearchResponse(
            results = listOf(
                FsqSearchResult(fsqId = "fsq_far",  name = "Le Gavroche", distance = 201),
                FsqSearchResult(fsqId = "fsq_near", name = "Le Gavroch",  distance = 30),
            )
        )
        coEvery { api.getPlace(any(), any()) } returns FsqPlaceResponse(fsqId = "fsq_near")

        repo.get("r1", "Le Gavroche", 51.5, -0.1)

        coVerify { api.getPlace(any(), "fsq_near") }
        coVerify(exactly = 0) { api.getPlace(any(), "fsq_far") }
    }

    // ── BuildConfig / no API key ──────────────────────────────────────────────

    @Test fun nullStoredApiKey_buildConfigKeyUsedAsFallback() = runTest {
        // Only runs when local.properties supplies FSQ_API_KEY (i.e. not in CI).
        // BuildConfig.FSQ_API_KEY is blank when local.properties is absent, so the repo
        // short-circuits before the API call — there is nothing to verify in that case.
        org.junit.Assume.assumeTrue(
            "Skipped: BuildConfig.FSQ_API_KEY not set (no local.properties)",
            app.mmmap.BuildConfig.FSQ_API_KEY.isNotBlank(),
        )
        every { apiKeyPrefs.fsqApiKey } returns flowOf(null)
        coEvery { cacheDao.get("r1") } returns null
        coEvery { api.searchPlaces(any(), any(), any()) } returns FsqSearchResponse(results = emptyList())

        repo.get("r1", "Test", 51.5, -0.1)

        coVerify { api.searchPlaces(any(), any(), any()) }
    }

    @Test fun storedApiKeyTakesPriorityOverBuildConfig() = runTest {
        every { apiKeyPrefs.fsqApiKey } returns flowOf("user-key")
        coEvery { cacheDao.get("r1") } returns null
        coEvery { api.searchPlaces("user-key", any(), any()) } returns FsqSearchResponse(results = emptyList())

        repo.get("r1", "Test", 51.5, -0.1)

        coVerify { api.searchPlaces("user-key", any(), any()) }
    }

    // ── photo URL construction ────────────────────────────────────────────────

    @Test fun photoUrl_formattedAs800x800() = runTest {
        every { apiKeyPrefs.fsqApiKey } returns flowOf("test-key")
        coEvery { cacheDao.get("r1") } returns null
        coEvery { api.searchPlaces(any(), any(), any()) } returns FsqSearchResponse(
            results = listOf(FsqSearchResult(fsqId = "fsq1", name = "Test", distance = 10))
        )
        coEvery { api.getPlace(any(), any()) } returns FsqPlaceResponse(
            fsqId = "fsq1",
            photos = listOf(FsqPhoto(prefix = "https://cdn.example.com/", suffix = "/original.jpg")),
        )

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertEquals("https://cdn.example.com/800x800/original.jpg", detail?.photoUrl)
    }

    @Test fun noPhotos_photoUrlIsNull() = runTest {
        every { apiKeyPrefs.fsqApiKey } returns flowOf("test-key")
        coEvery { cacheDao.get("r1") } returns null
        coEvery { api.searchPlaces(any(), any(), any()) } returns FsqSearchResponse(
            results = listOf(FsqSearchResult(fsqId = "fsq1", name = "Test", distance = 10))
        )
        coEvery { api.getPlace(any(), any()) } returns FsqPlaceResponse(
            fsqId = "fsq1",
            photos = emptyList(),
        )

        val detail = repo.get("r1", "Test", 51.5, -0.1)

        assertNull(detail?.photoUrl)
    }
}
