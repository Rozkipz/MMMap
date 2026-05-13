package app.mmmap.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.mmmap.data.db.AppDatabase
import app.mmmap.data.remote.GitHubReleasesApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

@HiltWorker
class DatasetSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val releasesApi: GitHubReleasesApi,
    private val okHttpClient: OkHttpClient,
    private val prefs: SyncPreferences,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val latest = releasesApi.latestRelease()
            val currentTag = prefs.lastReleaseTag()
            if (latest.tagName == currentTag) return@withContext Result.success()

            val asset = latest.assets.firstOrNull { it.name.endsWith(".db") }
                ?: return@withContext Result.success()

            val tempFile = File(appContext.cacheDir, "michelin_update.db")
            downloadFile(asset.downloadUrl, tempFile)

            if (!isSchemaValid(tempFile)) {
                tempFile.delete()
                return@withContext Result.failure()
            }

            val dbFile = appContext.getDatabasePath(AppDatabase.DB_NAME)
            dbFile.parentFile?.mkdirs()
            tempFile.copyTo(dbFile, overwrite = true)
            tempFile.delete()

            prefs.setLastReleaseTag(latest.tagName)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun downloadFile(url: String, dest: File) {
        val request = okhttp3.Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            response.body?.byteStream()?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private suspend fun isSchemaValid(dbFile: File): Boolean = try {
        val db = Room.databaseBuilder(appContext, AppDatabase::class.java, "temp_check")
            .createFromFile(dbFile)
            .fallbackToDestructiveMigration()
            .build()
        val count = runCatching { db.restaurantDao().count() }.getOrDefault(0)
        db.close()
        count > 100
    } catch (e: Exception) {
        false
    }

    companion object {
        const val TAG = "DatasetSyncWorker"
    }
}
