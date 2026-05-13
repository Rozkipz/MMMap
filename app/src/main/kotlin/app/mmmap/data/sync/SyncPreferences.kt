package app.mmmap.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keyTag = stringPreferencesKey("last_release_tag")
    private val keySyncAt = longPreferencesKey("last_sync_at")

    suspend fun lastReleaseTag(): String? = dataStore.data.first()[keyTag]

    suspend fun setLastReleaseTag(tag: String) {
        dataStore.edit { it[keyTag] = tag; it[keySyncAt] = System.currentTimeMillis() }
    }
}
