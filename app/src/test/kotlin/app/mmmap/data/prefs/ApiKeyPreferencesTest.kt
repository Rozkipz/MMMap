package app.mmmap.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ApiKeyPreferencesTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun prefs(): ApiKeyPreferences {
        val dataStore = PreferenceDataStoreFactory.create(scope = testScope) {
            tmpFolder.newFile("api_key_test.preferences_pb")
        }
        return ApiKeyPreferences(dataStore)
    }

    @Test fun fsqApiKey_nullByDefault() = testScope.runTest {
        assertNull(prefs().fsqApiKey.first())
    }

    @Test fun setFsqApiKey_persistsKey() = testScope.runTest {
        val p = prefs()
        p.setFsqApiKey("my-key-123")
        assertEquals("my-key-123", p.fsqApiKey.first())
    }

    @Test fun setFsqApiKey_trimsWhitespace() = testScope.runTest {
        val p = prefs()
        p.setFsqApiKey("  trimmed-key  ")
        assertEquals("trimmed-key", p.fsqApiKey.first())
    }

    @Test fun setFsqApiKey_blankStringRemovesKey() = testScope.runTest {
        val p = prefs()
        p.setFsqApiKey("my-key")
        p.setFsqApiKey("   ")
        assertNull(p.fsqApiKey.first())
    }

    @Test fun setFsqApiKey_nullRemovesKey() = testScope.runTest {
        val p = prefs()
        p.setFsqApiKey("my-key")
        p.setFsqApiKey(null)
        assertNull(p.fsqApiKey.first())
    }

    @Test fun setFsqApiKey_overwritesPreviousKey() = testScope.runTest {
        val p = prefs()
        p.setFsqApiKey("old-key")
        p.setFsqApiKey("new-key")
        assertEquals("new-key", p.fsqApiKey.first())
    }
}
