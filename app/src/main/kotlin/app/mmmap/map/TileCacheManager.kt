package app.mmmap.map

import android.content.Context
import app.mmmap.data.prefs.MapCachePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.maplibre.android.offline.OfflineManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class TileCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: MapCachePreferences,
) {
    val maxSizeMb = prefs.cacheSizeMb

    suspend fun applyStoredSize() {
        val mb = prefs.cacheSizeMb.first()
        setMaxSizeBytes(mb * 1024 * 1024)
    }

    suspend fun setMaxSizeMb(mb: Long) {
        prefs.setCacheSizeMb(mb)
        setMaxSizeBytes(mb * 1024 * 1024)
        invalidateCache()
    }

    suspend fun clearAmbientCache() {
        withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                OfflineManager.getInstance(context).clearAmbientCache(object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() = cont.resume(Unit)
                    override fun onError(message: String) = cont.resumeWithException(RuntimeException(message))
                })
            }
        }
    }

    private suspend fun setMaxSizeBytes(bytes: Long) {
        withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                OfflineManager.getInstance(context).setMaximumAmbientCacheSize(bytes, object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() = cont.resume(Unit)
                    override fun onError(message: String) = cont.resumeWithException(RuntimeException(message))
                })
            }
        }
    }

    private suspend fun invalidateCache() {
        withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                OfflineManager.getInstance(context).invalidateAmbientCache(object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() = cont.resume(Unit)
                    override fun onError(message: String) = cont.resumeWithException(RuntimeException(message))
                })
            }
        }
    }

    // Future: tile download support
    // suspend fun downloadRegion(definition: OfflineRegionDefinition, metadata: ByteArray): OfflineRegion
    // suspend fun listDownloadedRegions(): List<OfflineRegion>
    // suspend fun deleteRegion(region: OfflineRegion)
}
