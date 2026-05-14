package app.mmmap.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class MapCachePreferencesTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun prefs(): MapCachePreferences {
        val dataStore = PreferenceDataStoreFactory.create(scope = testScope) {
            tmpFolder.newFile("test_map_cache.preferences_pb")
        }
        return MapCachePreferences(dataStore)
    }

    @Test fun cacheSizeMb_defaultsTo100() = testScope.runTest {
        assertEquals(100L, prefs().cacheSizeMb.first())
    }

    @Test fun setCacheSizeMb_persistsValue() = testScope.runTest {
        val p = prefs()
        p.setCacheSizeMb(500L)
        assertEquals(500L, p.cacheSizeMb.first())
    }

    @Test fun setCacheSizeMb_overwritesPreviousValue() = testScope.runTest {
        val p = prefs()
        p.setCacheSizeMb(50L)
        p.setCacheSizeMb(1024L)
        assertEquals(1024L, p.cacheSizeMb.first())
    }

    @Test fun optionsMb_containsExpectedTiers() {
        assertEquals(listOf(50L, 100L, 500L, 1024L), MapCachePreferences.OPTIONS_MB)
    }

    @Test fun defaultCacheMb_isInOptionsList() {
        assertTrue(MapCachePreferences.DEFAULT_CACHE_MB in MapCachePreferences.OPTIONS_MB)
    }

    @Test fun allOptionsCanBeRoundTripped() = testScope.runTest {
        val p = prefs()
        for (mb in MapCachePreferences.OPTIONS_MB) {
            p.setCacheSizeMb(mb)
            assertEquals(mb, p.cacheSizeMb.first())
        }
    }
}
