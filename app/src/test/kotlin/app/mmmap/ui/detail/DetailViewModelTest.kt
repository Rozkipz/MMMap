package app.mmmap.ui.detail

import app.mmmap.data.repository.EnrichmentRepository
import app.mmmap.data.repository.VisitedRepository
import app.mmmap.domain.model.Distinction
import app.mmmap.domain.model.FoursquareDetail
import app.mmmap.domain.model.Restaurant
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val enrichmentRepo: EnrichmentRepository = mockk(relaxed = true)
    private val visitedRepo: VisitedRepository = mockk(relaxed = true) {
        every { observeIsVisited(any()) } returns flowOf(false)
    }
    private lateinit var vm: DetailViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vm = DetailViewModel(enrichmentRepo, visitedRepo)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun initialEnrichmentNull() {
        assertNull(vm.enrichment.value)
    }

    @Test fun initialLoadingFalse() {
        assertFalse(vm.loading.value)
    }

    @Test fun loadEnrichment_setsEnrichmentFromRepo() = runTest {
        val detail = detail(photoUrl = "https://img.example.com/1.jpg")
        coEvery { enrichmentRepo.get(any(), any(), any(), any()) } returns detail

        vm.loadEnrichment(restaurant("r1"))
        advanceUntilIdle()

        assertEquals(detail, vm.enrichment.value)
    }

    @Test fun loadEnrichment_loadingFalseAfterCompletion() = runTest {
        coEvery { enrichmentRepo.get(any(), any(), any(), any()) } returns null

        vm.loadEnrichment(restaurant("r1"))
        advanceUntilIdle()

        assertFalse(vm.loading.value)
    }

    @Test fun loadEnrichment_nullResultSetsNullEnrichment() = runTest {
        coEvery { enrichmentRepo.get(any(), any(), any(), any()) } returns null

        vm.loadEnrichment(restaurant("r1"))
        advanceUntilIdle()

        assertNull(vm.enrichment.value)
    }

    @Test fun loadEnrichment_passesRestaurantFieldsToRepo() = runTest {
        coEvery { enrichmentRepo.get(any(), any(), any(), any()) } returns null

        vm.loadEnrichment(restaurant("r1", name = "Le Test", lat = 48.87, lon = 2.33))
        advanceUntilIdle()

        coVerify { enrichmentRepo.get("r1", "Le Test", 48.87, 2.33) }
    }

    @Test fun loadEnrichment_secondCallOverwritesFirst() = runTest {
        val first  = detail(photoUrl = "https://first.jpg")
        val second = detail(photoUrl = "https://second.jpg")
        coEvery { enrichmentRepo.get("r1", any(), any(), any()) } returns first
        coEvery { enrichmentRepo.get("r2", any(), any(), any()) } returns second

        vm.loadEnrichment(restaurant("r1"))
        advanceUntilIdle()
        vm.loadEnrichment(restaurant("r2"))
        advanceUntilIdle()

        assertEquals(second, vm.enrichment.value)
    }

    // ── visited state ─────────────────────────────────────────────────────────

    @Test fun isVisited_initiallyFalse() {
        assertFalse(vm.isVisited.value)
    }

    @Test fun isVisited_reflectsRepoFlow() = runTest {
        every { visitedRepo.observeIsVisited("r1") } returns flowOf(true)

        vm.loadEnrichment(restaurant("r1"))
        advanceUntilIdle()

        assertTrue(vm.isVisited.value)
    }

    @Test fun setVisited_delegatesToRepo() = runTest {
        val r = restaurant("r1")

        vm.setVisited(r, true)
        advanceUntilIdle()

        coVerify { visitedRepo.setVisited(r, true) }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun restaurant(
        id: String,
        name: String = "Restaurant $id",
        lat: Double = 51.5,
        lon: Double = -0.1,
    ) = Restaurant(
        id = id, name = name, address = "1 Test St", location = null,
        latitude = lat, longitude = lon,
        distinction = Distinction.ONE_STAR, greenStar = false,
        cuisine = null, price = null, phoneNumber = null,
        michelinUrl = "https://guide.michelin.com/$id",
        websiteUrl = null, description = null, facilitiesAndServices = null,
    )

    private fun detail(photoUrl: String? = null) = FoursquareDetail(
        photoUrl = photoUrl,
        openingHours = emptyList(),
        isOpenNow = null,
        phone = null,
        rating = null,
    )
}
