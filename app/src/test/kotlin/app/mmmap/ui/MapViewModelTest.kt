package app.mmmap.ui

import android.content.Context
import android.location.LocationManager
import app.mmmap.data.prefs.ApiKeyPreferences
import app.mmmap.data.repository.RestaurantRepository
import app.mmmap.data.sync.SyncPreferences
import app.mmmap.domain.model.Distinction
import app.mmmap.domain.model.Restaurant
import app.mmmap.ui.map.MapBounds
import app.mmmap.ui.map.MapFilters
import app.mmmap.ui.map.MapViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo: RestaurantRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val apiKeyPrefs: ApiKeyPreferences = mockk(relaxed = true)
    private val syncPrefs: SyncPreferences = mockk(relaxed = true)
    private lateinit var vm: MapViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns mockk<LocationManager>(relaxed = true)
        coEvery { repo.distinctCuisines() } returns listOf("French", "Japanese")
        coEvery { repo.distinctPrices() } returns listOf("£", "££", "£££")
        every { repo.observeInBounds(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(emptyList<app.mmmap.domain.model.Restaurant>())
        every { apiKeyPrefs.fsqApiKey } returns flowOf(null)
        vm = MapViewModel(context, repo, apiKeyPrefs, syncPrefs)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun initialRestaurantsEmpty() {
        assertTrue(vm.restaurants.value.isEmpty())
    }

    @Test fun initialBoundsNull() {
        assertNull(vm.bounds.value)
    }

    @Test fun initialFiltersAllNull() {
        val f = vm.filters.value
        assertNull(f.distinction)
        assertNull(f.cuisines)
        assertNull(f.priceTiers)
    }

    @Test fun availableCuisinesPopulatedOnInit() = runTest {
        advanceUntilIdle()
        assertEquals(listOf("French", "Japanese"), vm.availableCuisines.value)
    }

    @Test fun availablePricesPopulatedOnInit() = runTest {
        advanceUntilIdle()
        assertEquals(listOf("£", "££", "£££"), vm.availablePrices.value)
    }

    @Test fun updateBoundsSetsValue() {
        val bounds = MapBounds(1.0, 2.0, 3.0, 4.0)
        vm.updateBounds(bounds)
        assertEquals(bounds, vm.bounds.value)
    }

    @Test fun updateFiltersSetsValue() {
        val filters = MapFilters(distinction = Distinction.THREE_STAR, cuisines = setOf("French"))
        vm.updateFilters(filters)
        assertEquals(filters, vm.filters.value)
    }

    @Test fun selectRestaurantUpdatesSelection() {
        val r = restaurant("r1", "Noma")
        vm.selectRestaurant(r)
        assertEquals(r, vm.selectedRestaurant.value)
    }

    @Test fun selectRestaurantNullClearsSelection() {
        vm.selectRestaurant(restaurant("r1", "Noma"))
        vm.selectRestaurant(null)
        assertNull(vm.selectedRestaurant.value)
    }

    @Test fun restaurantsFlowEmitsFromRepo() = runTest {
        val list = listOf(restaurant("r1", "Le Gavroche"), restaurant("r2", "The Ritz"))
        every { repo.observeInBounds(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(list)

        val emissions = mutableListOf<List<Restaurant>>()
        val job = vm.restaurants.onEach { emissions.add(it) }.launchIn(this)

        vm.updateBounds(MapBounds(50.0, 52.0, -1.0, 1.0))
        advanceUntilIdle()

        assertTrue("expected list emission, got $emissions", emissions.any { it == list })
        job.cancel()
    }

    @Test fun nullBoundsKeepsEmptyList() = runTest {
        val emissions = mutableListOf<List<Restaurant>>()
        val job = vm.restaurants.onEach { emissions.add(it) }.launchIn(this)
        advanceUntilIdle()

        assertTrue(emissions.all { it.isEmpty() })
        job.cancel()
    }

    @Test fun filterDistinctionPassedToRepo() = runTest {
        val job = vm.restaurants.launchIn(this)

        vm.updateBounds(MapBounds(50.0, 52.0, -1.0, 1.0))
        vm.updateFilters(MapFilters(distinction = Distinction.ONE_STAR))
        advanceUntilIdle()

        verify { repo.observeInBounds(50.0, 52.0, -1.0, 1.0, "1 Star", null, null) }
        job.cancel()
    }

    @Test fun filterCuisinePassedToRepo() = runTest {
        val job = vm.restaurants.launchIn(this)

        vm.updateBounds(MapBounds(50.0, 52.0, -1.0, 1.0))
        vm.updateFilters(MapFilters(cuisines = setOf("French")))
        advanceUntilIdle()

        verify { repo.observeInBounds(50.0, 52.0, -1.0, 1.0, null, setOf("French"), null) }
        job.cancel()
    }

    private fun restaurant(id: String, name: String) = Restaurant(
        id = id, name = name, address = "1 Test St", location = null,
        latitude = 51.5, longitude = -0.1, distinction = Distinction.ONE_STAR,
        greenStar = false, cuisine = null, price = null, phoneNumber = null,
        michelinUrl = "https://guide.michelin.com/$id",
        websiteUrl = null, description = null, facilitiesAndServices = null,
    )
}
