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
    private val keySha    = stringPreferencesKey("last_csv_sha")
    private val keySyncAt = longPreferencesKey("last_sync_at")

    suspend fun lastCsvSha(): String? = dataStore.data.first()[keySha]

    suspend fun setLastCsvSha(sha: String) {
        dataStore.edit { it[keySha] = sha; it[keySyncAt] = System.currentTimeMillis() }
    }
}
