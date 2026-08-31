package app.mmmap

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.mmmap.data.sync.DatasetSyncWorker
import app.mmmap.data.sync.SyncPreferences
import app.mmmap.map.TileCacheManager
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

@HiltAndroidApp
class MmmapApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var tileCacheManager: TileCacheManager
    @Inject lateinit var syncPrefs: SyncPreferences

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        // Must not throw: an ambient-cache failure here would crash the app on every
        // single launch, with no recovery short of clearing app data.
        appScope.launch { runCatching { tileCacheManager.applyStoredSize() } }
        appScope.launch {
            runCatching {
                syncPrefs.seedShaIfAbsent(getString(R.string.bundled_csv_sha))
            }
        }
        enqueueSyncIfNeeded()
    }

    private fun enqueueSyncIfNeeded() {
        val request = PeriodicWorkRequestBuilder<DatasetSyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DatasetSyncWorker.TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
