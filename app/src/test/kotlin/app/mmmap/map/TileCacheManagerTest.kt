package app.mmmap.map

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.mmmap.data.prefs.MapCachePreferences
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TileCacheManagerTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val cacheSource: AmbientCacheSource = mockk(relaxed = true)
    private lateinit var prefs: MapCachePreferences
    private lateinit var manager: TileCacheManager

    @Before fun setUp() {
        coJustRun { cacheSource.setMaxBytes(any()) }
        coJustRun { cacheSource.invalidate() }
        coJustRun { cacheSource.clear() }

        val dataStore = PreferenceDataStoreFactory.create(scope = testScope) {
            tmpFolder.newFile("tile_cache_test.preferences_pb")
        }
        prefs = MapCachePreferences(dataStore)
        manager = TileCacheManager(prefs, cacheSource)
    }

    @Test fun maxSizeMb_reflectsPrefs() = testScope.runTest {
        prefs.setCacheSizeMb(500L)
        assertEquals(500L, manager.maxSizeMb.first())
    }

    @Test fun applyStoredSize_callsCacheSourceWithCorrectBytes() = testScope.runTest {
        prefs.setCacheSizeMb(50L)
        manager.applyStoredSize()
        advanceUntilIdle()
        coVerify { cacheSource.setMaxBytes(50L * 1024 * 1024) }
    }

    @Test fun applyStoredSize_usesDefaultWhenNoPrefSet() = testScope.runTest {
        manager.applyStoredSize()
        advanceUntilIdle()
        coVerify { cacheSource.setMaxBytes(MapCachePreferences.DEFAULT_CACHE_MB * 1024 * 1024) }
    }

    @Test fun setMaxSizeMb_storesPreference() = testScope.runTest {
        manager.setMaxSizeMb(500L)
        advanceUntilIdle()
        assertEquals(500L, prefs.cacheSizeMb.first())
    }

    @Test fun setMaxSizeMb_callsCacheSourceWithCorrectBytes() = testScope.runTest {
        manager.setMaxSizeMb(500L)
        advanceUntilIdle()
        coVerify { cacheSource.setMaxBytes(500L * 1024 * 1024) }
    }

    @Test fun setMaxSizeMb_invalidatesCacheAfterResize() = testScope.runTest {
        manager.setMaxSizeMb(100L)
        advanceUntilIdle()
        coVerify { cacheSource.invalidate() }
    }

    @Test fun setMaxSizeMb_1gb_usesCorrectBytes() = testScope.runTest {
        manager.setMaxSizeMb(1024L)
        advanceUntilIdle()
        coVerify { cacheSource.setMaxBytes(1024L * 1024 * 1024) }
    }

    @Test fun clearAmbientCache_callsCacheSourceClear() = testScope.runTest {
        manager.clearAmbientCache()
        advanceUntilIdle()
        coVerify { cacheSource.clear() }
    }

    @Test fun clearAmbientCache_doesNotTouchPreference() = testScope.runTest {
        prefs.setCacheSizeMb(500L)
        manager.clearAmbientCache()
        advanceUntilIdle()
        assertEquals(500L, prefs.cacheSizeMb.first())
    }

    @Test fun clearAmbientCache_doesNotInvalidate() = testScope.runTest {
        manager.clearAmbientCache()
        advanceUntilIdle()
        coVerify(exactly = 0) { cacheSource.invalidate() }
    }
}
