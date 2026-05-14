package app.mmmap.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SyncPreferencesTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun prefs(): SyncPreferences {
        val dataStore = PreferenceDataStoreFactory.create(scope = testScope) {
            tmpFolder.newFile("sync_prefs_test.preferences_pb")
        }
        return SyncPreferences(dataStore)
    }

    @Test fun lastCsvSha_nullByDefault() = testScope.runTest {
        assertNull(prefs().lastCsvSha())
    }

    @Test fun lastSyncAt_nullByDefault() = testScope.runTest {
        assertNull(prefs().lastSyncAt())
    }

    @Test fun setLastCsvSha_persistsSha() = testScope.runTest {
        val p = prefs()
        p.setLastCsvSha("abc123")
        assertEquals("abc123", p.lastCsvSha())
    }

    @Test fun setLastCsvSha_overwritesPreviousValue() = testScope.runTest {
        val p = prefs()
        p.setLastCsvSha("sha1")
        p.setLastCsvSha("sha2")
        assertEquals("sha2", p.lastCsvSha())
    }

    @Test fun setLastCsvSha_alsoSetsTimestamp() = testScope.runTest {
        val before = System.currentTimeMillis()
        val p = prefs()
        p.setLastCsvSha("abc123")
        val after = System.currentTimeMillis()
        val ts = p.lastSyncAt()
        assertNotNull(ts)
        assert(ts!! in before..after) { "timestamp $ts not in [$before, $after]" }
    }

    @Test fun clearSha_removesSha() = testScope.runTest {
        val p = prefs()
        p.setLastCsvSha("abc123")
        p.clearSha()
        assertNull(p.lastCsvSha())
    }

    @Test fun clearSha_doesNotClearTimestamp() = testScope.runTest {
        val p = prefs()
        p.setLastCsvSha("abc123")
        p.clearSha()
        assertNotNull(p.lastSyncAt())
    }
}
