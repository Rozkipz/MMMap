package app.mmmap.data.sync

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.mmmap.data.remote.GitHubReleasesApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

@HiltWorker
class DatasetSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val releasesApi: GitHubReleasesApi,
    private val okHttpClient: OkHttpClient,
    private val prefs: SyncPreferences,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tempFile = File(appContext.cacheDir, "michelin_download.db")
        try {
            val latest = releasesApi.latestRelease()
            if (latest.tagName == prefs.lastReleaseTag()) return@withContext Result.success()

            val asset = latest.assets.firstOrNull { it.name.endsWith(".db") }
                ?: return@withContext Result.success()

            downloadFile(asset.downloadUrl, tempFile)

            if (!isSchemaValid(tempFile)) return@withContext Result.failure()

            // Save outside the live DB path — DatabaseModule swaps it in on the next cold start,
            // before Room opens the database, so we never touch an open file.
            tempFile.copyTo(File(appContext.filesDir, PENDING_DB_NAME), overwrite = true)

            prefs.setLastReleaseTag(latest.tagName)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        } finally {
            tempFile.delete()
        }
    }

    private fun downloadFile(url: String, dest: File) {
        val request = okhttp3.Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.byteStream()?.use { it.copyTo(dest.outputStream()) }
                ?: throw IOException("Empty body")
        }
    }

    private fun isSchemaValid(dbFile: File): Boolean = try {
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        ).use { db -> DatabaseUtils.queryNumEntries(db, "restaurant") > 100 }
    } catch (e: Exception) {
        false
    }

    companion object {
        const val TAG = "DatasetSyncWorker"
        const val PENDING_DB_NAME = "michelin_pending.db"
    }
}
