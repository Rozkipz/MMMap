package app.mmmap.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapCachePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keyCacheMb = longPreferencesKey("map_tile_cache_mb")

    val cacheSizeMb: Flow<Long> = dataStore.data.map { it[keyCacheMb] ?: DEFAULT_CACHE_MB }

    suspend fun setCacheSizeMb(mb: Long) {
        dataStore.edit { it[keyCacheMb] = mb }
    }

    companion object {
        const val DEFAULT_CACHE_MB = 100L
        val OPTIONS_MB = listOf(50L, 100L, 500L, 1024L)
    }
}
