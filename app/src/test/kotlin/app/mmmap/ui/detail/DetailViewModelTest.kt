package app.mmmap.ui.detail

import app.mmmap.data.repository.VisitedRepository
import app.mmmap.domain.model.Distinction
import app.mmmap.domain.model.Restaurant
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val visitedRepo: VisitedRepository = mockk(relaxed = true) {
        every { observeIsVisited(any()) } returns flowOf(false)
    }
    private lateinit var vm: DetailViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vm = DetailViewModel(visitedRepo)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
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
}
