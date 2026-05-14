package app.mmmap.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import app.mmmap.data.prefs.ApiKeyPreferences
import app.mmmap.data.prefs.MapCachePreferences
import app.mmmap.data.repository.RestaurantRepository
import app.mmmap.data.repository.VisitedRepository
import app.mmmap.data.sync.SyncPreferences
import app.mmmap.map.TileCacheManager
import app.mmmap.ui.map.MapViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class MapViewModelLocateTest {

    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(scheduler)
    private val repo = mockk<RestaurantRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val locationManager = mockk<LocationManager>(relaxed = true)
    private val apiKeyPrefs = mockk<ApiKeyPreferences>(relaxed = true)
    private val syncPrefs = mockk<SyncPreferences>(relaxed = true)
    private val tileCacheManager = mockk<TileCacheManager>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val visitedRepo = mockk<VisitedRepository>(relaxed = true) {
        every { visitedIds } returns flowOf(emptySet())
    }
    private lateinit var vm: MapViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
        coEvery { repo.distinctCuisines() } returns emptyList()
        coEvery { repo.distinctPrices() } returns emptyList()
        every { repo.observeInBounds(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(emptyList())
        every { apiKeyPrefs.fsqApiKey } returns flowOf(null)
        every { tileCacheManager.maxSizeMb } returns flowOf(MapCachePreferences.DEFAULT_CACHE_MB)
        mockkStatic(ContextCompat::class)
        mockkStatic(Looper::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        every { locationManager.isProviderEnabled(any()) } returns true
        every { locationManager.getLastKnownLocation(any()) } returns null
        vm = MapViewModel(context, repo, apiKeyPrefs, syncPrefs, tileCacheManager, workManager, visitedRepo)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // --- permission guard ---

    @Test fun noPermission_isLocatingUnchanged() {
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        vm.locateUser()
        assertFalse(vm.isLocating.value)
    }

    @Test fun noPermission_userLatLonUnchanged() {
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        vm.locateUser()
        assertNull(vm.userLatLon.value)
    }

    @Test fun finePermissionAloneIsSufficient() {
        every { ContextCompat.checkSelfPermission(any(), Manifest.permission.ACCESS_FINE_LOCATION) } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.checkSelfPermission(any(), Manifest.permission.ACCESS_COARSE_LOCATION) } returns PackageManager.PERMISSION_DENIED
        vm.locateUser()
        // proceeds — if no providers were enabled we'd still return; providers ARE enabled in setUp
        assertTrue(vm.isLocating.value) // no cache → spinner shown
    }

    // --- cached location: no spinner ---

    @Test fun cachedLocation_spinnerNeverShown() {
        every { locationManager.getLastKnownLocation(any()) } returns fakeLocation(36.8, -2.4)
        vm.locateUser()
        assertFalse(vm.isLocating.value)
    }

    @Test fun cachedLocation_positionSetImmediately() {
        every { locationManager.getLastKnownLocation(any()) } returns fakeLocation(36.8, -2.4)
        vm.locateUser()
        assertEquals(36.8 to -2.4, vm.userLatLon.value)
    }

    @Test fun mostRecentCachedLocationChosen() {
        val old = fakeLocation(10.0, 10.0, time = 1_000L)
        val recent = fakeLocation(20.0, 20.0, time = 2_000L)
        every { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } returns old
        every { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } returns recent
        vm.locateUser()
        assertEquals(20.0 to 20.0, vm.userLatLon.value)
    }

    // --- no cached location: spinner shown ---

    @Test fun noCachedLocation_spinnerShown() {
        vm.locateUser()
        assertTrue(vm.isLocating.value)
    }

    @Test fun noEnabledProviders_spinnerNotShown() {
        every { locationManager.isProviderEnabled(any()) } returns false
        vm.locateUser()
        assertFalse(vm.isLocating.value)
    }

    // --- location callback ---

    @Test fun locationCallback_hidesSpinner() {
        val slot = slot<LocationListener>()
        every {
            locationManager.requestLocationUpdates(any(), any<Long>(), any<Float>(), capture(slot), any())
        } just Runs

        vm.locateUser()
        assertTrue(vm.isLocating.value)

        slot.captured.onLocationChanged(fakeLocation(51.5, -0.1))
        assertFalse(vm.isLocating.value)
    }

    @Test fun locationCallback_updatesPosition() {
        val slot = slot<LocationListener>()
        every {
            locationManager.requestLocationUpdates(any(), any<Long>(), any<Float>(), capture(slot), any())
        } just Runs

        vm.locateUser()
        slot.captured.onLocationChanged(fakeLocation(51.5, -0.1))
        assertEquals(51.5 to -0.1, vm.userLatLon.value)
    }

    @Test fun secondLocationCallback_ignored() {
        val slot = slot<LocationListener>()
        every {
            locationManager.requestLocationUpdates(any(), any<Long>(), any<Float>(), capture(slot), any())
        } just Runs

        vm.locateUser()
        slot.captured.onLocationChanged(fakeLocation(51.5, -0.1))
        slot.captured.onLocationChanged(fakeLocation(99.0, 99.0))
        assertEquals(51.5 to -0.1, vm.userLatLon.value)
    }

    @Test fun cachedThenFreshCallback_updatesToFresh() {
        every { locationManager.getLastKnownLocation(any()) } returns fakeLocation(36.8, -2.4)
        val slot = slot<LocationListener>()
        every {
            locationManager.requestLocationUpdates(any(), any<Long>(), any<Float>(), capture(slot), any())
        } just Runs

        vm.locateUser()
        assertEquals(36.8 to -2.4, vm.userLatLon.value)

        slot.captured.onLocationChanged(fakeLocation(48.85, 2.35))
        assertEquals(48.85 to 2.35, vm.userLatLon.value)
    }

    // --- timeout ---

    @Test fun timeout_hidesSpinner() = runTest(testDispatcher) {
        vm.locateUser()
        assertTrue(vm.isLocating.value)

        advanceTimeBy(60_001L)
        assertFalse(vm.isLocating.value)
    }

    @Test fun timeoutAfterCallback_noStateCorruption() = runTest(testDispatcher) {
        val slot = slot<LocationListener>()
        every {
            locationManager.requestLocationUpdates(any(), any<Long>(), any<Float>(), capture(slot), any())
        } just Runs

        vm.locateUser()
        slot.captured.onLocationChanged(fakeLocation(51.5, -0.1))
        assertFalse(vm.isLocating.value)

        // Timeout fires after the callback — should not re-show spinner
        advanceTimeBy(60_001L)
        assertFalse(vm.isLocating.value)
        assertEquals(51.5 to -0.1, vm.userLatLon.value)
    }

    @Test fun onProviderDisabled_doesNotChangeSpinner() {
        val slot = slot<LocationListener>()
        every {
            locationManager.requestLocationUpdates(any(), any<Long>(), any<Float>(), capture(slot), any())
        } just Runs

        vm.locateUser()
        assertTrue(vm.isLocating.value)

        slot.captured.onProviderDisabled("gps")
        assertTrue(vm.isLocating.value)
    }

    @Test fun onProviderEnabled_doesNotChangeSpinner() {
        val slot = slot<LocationListener>()
        every {
            locationManager.requestLocationUpdates(any(), any<Long>(), any<Float>(), capture(slot), any())
        } just Runs

        vm.locateUser()
        assertTrue(vm.isLocating.value)

        slot.captured.onProviderEnabled("gps")
        assertTrue(vm.isLocating.value)
    }

    @Test fun coarseOnlyPermission_proceeds() {
        every { ContextCompat.checkSelfPermission(any(), android.Manifest.permission.ACCESS_FINE_LOCATION) } returns PackageManager.PERMISSION_DENIED
        every { ContextCompat.checkSelfPermission(any(), android.Manifest.permission.ACCESS_COARSE_LOCATION) } returns PackageManager.PERMISSION_GRANTED

        vm.locateUser()

        assertTrue(vm.isLocating.value)
    }

    // --- helpers ---

    private fun fakeLocation(lat: Double, lon: Double, time: Long = System.currentTimeMillis()) =
        mockk<Location> {
            every { latitude } returns lat
            every { longitude } returns lon
            every { this@mockk.time } returns time
        }
}
