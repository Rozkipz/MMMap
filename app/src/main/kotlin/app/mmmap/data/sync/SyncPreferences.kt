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
    suspend fun lastSyncAt(): Long?   = dataStore.data.first()[keySyncAt]

    suspend fun setLastCsvSha(sha: String) {
        dataStore.edit {
            it[keySha] = sha
            it[keySyncAt] = System.currentTimeMillis()
        }
    }

    /**
     * Records [sha] as the already-synced revision, but only if nothing is stored yet.
     *
     * Called once on first launch with the SHA of the CSV the bundled database was built
     * from. Without it the first [app.mmmap.data.sync.DatasetSyncWorker] run sees a null
     * SHA, decides it is out of date, and re-downloads the whole ~17.5 MB CSV to rebuild
     * rows the APK already shipped.
     *
     * Deliberately seeds rather than falling back at read time: Room's seed asset is only
     * materialised on a fresh install, so an upgrade must keep whatever SHA the previous
     * version actually synced to.
     */
    suspend fun seedShaIfAbsent(sha: String) {
        dataStore.edit {
            if (it[keySha] == null) {
                it[keySha] = sha
                it[keySyncAt] = System.currentTimeMillis()
            }
        }
    }

    suspend fun clearSha() {
        dataStore.edit { it.remove(keySha) }
    }
}
