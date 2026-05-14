package app.mmmap.map

import app.mmmap.data.prefs.MapCachePreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TileCacheManager @Inject constructor(
    private val prefs: MapCachePreferences,
    private val cacheSource: AmbientCacheSource,
) {
    val maxSizeMb = prefs.cacheSizeMb

    suspend fun applyStoredSize() {
        val mb = prefs.cacheSizeMb.first()
        cacheSource.setMaxBytes(mb * 1024 * 1024)
    }

    suspend fun setMaxSizeMb(mb: Long) {
        prefs.setCacheSizeMb(mb)
        cacheSource.setMaxBytes(mb * 1024 * 1024)
        cacheSource.invalidate()
    }

    suspend fun clearAmbientCache() {
        cacheSource.clear()
    }

    // Future: tile download support
    // suspend fun downloadRegion(definition: OfflineRegionDefinition, metadata: ByteArray): OfflineRegion
    // suspend fun listDownloadedRegions(): List<OfflineRegion>
    // suspend fun deleteRegion(region: OfflineRegion)
}
