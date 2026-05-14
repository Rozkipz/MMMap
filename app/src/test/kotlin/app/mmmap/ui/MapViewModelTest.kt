package app.mmmap.ui

import android.content.Context
import android.location.LocationManager
import app.mmmap.data.prefs.ApiKeyPreferences
import app.mmmap.data.prefs.MapCachePreferences
import app.mmmap.data.repository.RestaurantRepository
import app.mmmap.data.sync.SyncPreferences
import app.mmmap.map.TileCacheManager
import app.mmmap.domain.model.Distinction
import app.mmmap.domain.model.Restaurant
import app.mmmap.ui.map.DebugState
import app.mmmap.ui.map.MapBounds
import app.mmmap.ui.map.MapFilters
import app.mmmap.ui.map.MapViewModel
import androidx.work.WorkManager
import app.mmmap.data.sync.DatasetSyncWorker
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
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
import org.junit.Assert.assertFalse
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
    private val tileCacheManager: TileCacheManager = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private lateinit var vm: MapViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns mockk<LocationManager>(relaxed = true)
        // WorkManager.getInstance calls context.getApplicationContext() during mockk recording;
        // the relaxed mock doesn't implement this abstract method, so we stub it explicitly.
        every { context.applicationContext } returns context
        coEvery { repo.distinctCuisines() } returns listOf("French", "Japanese")
        coEvery { repo.distinctPrices() } returns listOf("£", "££", "£££")
        every { repo.observeInBounds(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(emptyList())
        every { apiKeyPrefs.fsqApiKey } returns flowOf(null)
        every { tileCacheManager.maxSizeMb } returns flowOf(MapCachePreferences.DEFAULT_CACHE_MB)
        vm = MapViewModel(context, repo, apiKeyPrefs, syncPrefs, tileCacheManager, workManager)
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
        assertNull(f.distinctions)
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
        val filters = MapFilters(distinctions = setOf(Distinction.THREE_STAR), cuisines = setOf("French"))
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
        vm.updateFilters(MapFilters(distinctions = setOf(Distinction.ONE_STAR)))
        advanceUntilIdle()

        verify { repo.observeInBounds(50.0, 52.0, -1.0, 1.0, setOf(Distinction.ONE_STAR), null, null) }
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

    @Test fun filterPriceTiersPassedToRepo() = runTest {
        val job = vm.restaurants.launchIn(this)

        vm.updateBounds(MapBounds(50.0, 52.0, -1.0, 1.0))
        vm.updateFilters(MapFilters(priceTiers = setOf(2, 3)))
        advanceUntilIdle()

        verify { repo.observeInBounds(50.0, 52.0, -1.0, 1.0, null, null, setOf(2, 3)) }
        job.cancel()
    }

    @Test fun debugStateNullOnInit() {
        assertNull(vm.debugState.value)
    }

    @Test fun hasZoomedToUserOnce_defaultsFalse() {
        assertFalse(vm.hasZoomedToUserOnce)
    }

    @Test fun hasZoomedToUserOnce_canBeSetTrue() {
        vm.hasZoomedToUserOnce = true
        assertTrue(vm.hasZoomedToUserOnce)
    }

    @Test fun saveLastCamera_storesPosition() {
        vm.saveLastCamera(51.5, -0.1, 12.0)
        assertEquals(Triple(51.5, -0.1, 12.0), vm.lastCameraPosition)
    }

    @Test fun lastCameraPosition_nullOnInit() {
        assertNull(vm.lastCameraPosition)
    }

    @Test fun saveFoursquareKey_delegatesToPrefs() = runTest {
        vm.saveFoursquareKey("my-api-key")
        advanceUntilIdle()
        coVerify { apiKeyPrefs.setFsqApiKey("my-api-key") }
    }

    @Test fun debugStateDataClassEquality() {
        val bounds = MapBounds(1.0, 2.0, 3.0, 4.0)
        val filters = MapFilters()
        val state1 = DebugState(
            dbRestaurantCount = 100,
            viewportCount = 5,
            lastSyncAt = 1_000L,
            lastCsvSha = "abc12345",
            workerState = "RUNNING",
            nextSyncAt = 2_000L,
            bounds = bounds,
            filters = filters,
        )
        val state2 = state1.copy()
        assertEquals(state1, state2)
        assertEquals(state1.dbRestaurantCount, 100)
        assertEquals(state1.workerState, "RUNNING")
        assertEquals(state1.lastCsvSha, "abc12345")
    }

    @Test fun foursquareKey_defaultsToBuildConfig() = runTest {
        val keys = mutableListOf<String>()
        val job = vm.foursquareKey.onEach { keys.add(it) }.launchIn(this)
        advanceUntilIdle()
        assertTrue(keys.isNotEmpty())
        job.cancel()
    }

    // ── tile cache ────────────────────────────────────────────────────────────

    @Test fun cacheSizeMb_emitsFromTileCacheManager() = runTest {
        every { tileCacheManager.maxSizeMb } returns flowOf(500L)
        val freshVm = MapViewModel(context, repo, apiKeyPrefs, syncPrefs, tileCacheManager, workManager)
        val values = mutableListOf<Long>()
        val job = freshVm.cacheSizeMb.onEach { values.add(it) }.launchIn(this)
        advanceUntilIdle()
        assertTrue("expected 500 emission, got $values", values.any { it == 500L })
        job.cancel()
    }

    @Test fun setCacheSizeMb_delegatesToTileCacheManager() = runTest {
        coJustRun { tileCacheManager.setMaxSizeMb(any()) }
        vm.setCacheSizeMb(500L)
        advanceUntilIdle()
        coVerify { tileCacheManager.setMaxSizeMb(500L) }
    }

    @Test fun clearTileCache_delegatesToTileCacheManager() = runTest {
        coJustRun { tileCacheManager.clearAmbientCache() }
        vm.clearTileCache()
        advanceUntilIdle()
        coVerify { tileCacheManager.clearAmbientCache() }
    }

    // ── forceRefresh ──────────────────────────────────────────────────────────

    @Test fun forceRefresh_clearsShaAndEnqueuesWork() = runTest {
        coJustRun { syncPrefs.clearSha() }

        vm.forceRefresh()
        advanceUntilIdle()

        coVerify { syncPrefs.clearSha() }
        verify { workManager.enqueueUniquePeriodicWork(DatasetSyncWorker.TAG, any(), any()) }
    }

    // ── loadDebugInfo ─────────────────────────────────────────────────────────

    @Test fun loadDebugInfo_populatesDebugState() = runTest {
        val future = mockk<com.google.common.util.concurrent.ListenableFuture<List<androidx.work.WorkInfo>>>(relaxed = true)
        every { workManager.getWorkInfosForUniqueWork(any()) } returns future
        every { future.get() } returns emptyList()
        coEvery { repo.count() } returns 99
        coEvery { syncPrefs.lastSyncAt() } returns 1_000L
        coEvery { syncPrefs.lastCsvSha() } returns "abcdef1234567890"

        vm.loadDebugInfo()
        advanceUntilIdle()

        val state = vm.debugState.value
        assertEquals(99, state?.dbRestaurantCount)
        assertEquals(1_000L, state?.lastSyncAt)
        assertEquals("abcdef12", state?.lastCsvSha)
        assertEquals("none", state?.workerState)
    }

    @Test fun loadDebugInfo_workerStateFromWorkInfo() = runTest {
        val future = mockk<com.google.common.util.concurrent.ListenableFuture<List<androidx.work.WorkInfo>>>(relaxed = true)
        val workInfo = mockk<androidx.work.WorkInfo>(relaxed = true)
        every { workInfo.state } returns androidx.work.WorkInfo.State.RUNNING
        every { workInfo.nextScheduleTimeMillis } returns Long.MAX_VALUE
        every { workManager.getWorkInfosForUniqueWork(any()) } returns future
        every { future.get() } returns listOf(workInfo)
        coEvery { repo.count() } returns 0
        coEvery { syncPrefs.lastSyncAt() } returns null
        coEvery { syncPrefs.lastCsvSha() } returns null

        vm.loadDebugInfo()
        advanceUntilIdle()

        assertEquals("RUNNING", vm.debugState.value?.workerState)
        assertNull(vm.debugState.value?.nextSyncAt)
    }

    private fun restaurant(id: String, name: String) = Restaurant(
        id = id, name = name, address = "1 Test St", location = null,
        latitude = 51.5, longitude = -0.1, distinction = Distinction.ONE_STAR,
        greenStar = false, cuisine = null, price = null, phoneNumber = null,
        michelinUrl = "https://guide.michelin.com/$id",
        websiteUrl = null, description = null, facilitiesAndServices = null,
    )
}
