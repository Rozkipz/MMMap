package app.mmmap.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keyFsq = stringPreferencesKey("fsq_api_key")

    val fsqApiKey: Flow<String?> = dataStore.data.map { it[keyFsq] }

    suspend fun setFsqApiKey(key: String?) {
        dataStore.edit {
            if (key.isNullOrBlank()) it.remove(keyFsq) else it[keyFsq] = key.trim()
        }
    }
}
