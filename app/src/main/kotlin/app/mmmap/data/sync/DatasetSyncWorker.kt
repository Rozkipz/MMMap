package app.mmmap.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.mmmap.data.db.AppDatabase
import app.mmmap.data.db.entities.RestaurantEntity
import app.mmmap.data.remote.GitHubContentsApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.security.MessageDigest

@HiltWorker
class DatasetSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val contentsApi: GitHubContentsApi,
    private val okHttpClient: OkHttpClient,
    private val prefs: SyncPreferences,
    private val db: AppDatabase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val sha = contentsApi.csvMetadata().sha
            if (sha == prefs.lastCsvSha()) return@withContext Result.success()

            val entities = downloadAndParseCsv()
            if (entities.size < 100) return@withContext Result.failure()

            db.withTransaction {
                db.restaurantDao().deleteAll()
                db.restaurantDao().insertAll(entities)
            }

            prefs.setLastCsvSha(sha)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun downloadAndParseCsv(): List<RestaurantEntity> {
        val request = Request.Builder().url(GitHubContentsApi.CSV_RAW_URL).build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty body")
            body.charStream().buffered().use { parseCsv(it) }
        }
    }

    companion object {
        const val TAG = "DatasetSyncWorker"

        // CSV columns (0-indexed):
        // 0:Name 1:Address 2:Location 3:Price 4:Cuisine 5:Longitude 6:Latitude
        // 7:PhoneNumber 8:Url 9:WebsiteUrl 10:Award 11:GreenStar 12:FacilitiesAndServices 13:Description
        internal fun parseCsv(reader: BufferedReader): List<RestaurantEntity> {
            val entities = mutableListOf<RestaurantEntity>()
            var firstLine = true
            for (record in csvRecords(reader)) {
                if (firstLine) { firstLine = false; continue }
                if (record.size < 13) continue
                val url = record[8].trim().takeIf { it.isNotEmpty() } ?: continue
                val lat = record[6].toDoubleOrNull() ?: continue
                val lon = record[5].toDoubleOrNull() ?: continue
                entities.add(
                    RestaurantEntity(
                        id                    = sha256Prefix(url),
                        name                  = record[0].trim(),
                        address               = record[1].trim(),
                        location              = record[2].trim().takeIf { it.isNotEmpty() },
                        price                 = record[3].trim().takeIf { it.isNotEmpty() },
                        cuisine               = record[4].trim().takeIf { it.isNotEmpty() },
                        longitude             = lon,
                        latitude              = lat,
                        phoneNumber           = record[7].trim().takeIf { it.isNotEmpty() },
                        url                   = url,
                        websiteUrl            = record[9].trim().takeIf { it.isNotEmpty() },
                        award                 = record[10].trim().takeIf { it.isNotEmpty() },
                        greenStar             = record[11].trim().equals("True", ignoreCase = true),
                        facilitiesAndServices = record[12].trim().takeIf { it.isNotEmpty() },
                        description           = record.getOrNull(13)?.trim()?.takeIf { it.isNotEmpty() },
                    )
                )
            }
            return entities
        }

        // Minimal RFC 4180 parser. Accumulates lines until the running quote count is even
        // (i.e. we're not inside a quoted field), then emits one complete record.
        internal fun csvRecords(reader: BufferedReader): List<List<String>> {
            val records = mutableListOf<List<String>>()
            val pending = StringBuilder()
            reader.forEachLine { line ->
                if (pending.isNotEmpty()) pending.append('\n')
                pending.append(line)
                // Odd number of quote chars means we're still inside a quoted field
                if (pending.count { it == '"' } % 2 == 0) {
                    records.add(splitCsvRecord(pending.toString()))
                    pending.clear()
                }
            }
            if (pending.isNotEmpty()) records.add(splitCsvRecord(pending.toString()))
            return records
        }

        internal fun splitCsvRecord(line: String): List<String> {
            val fields = mutableListOf<String>()
            var i = 0
            while (i <= line.length) {
                if (i == line.length) { fields.add(""); break }
                if (line[i] == '"') {
                    val sb = StringBuilder()
                    i++
                    while (i < line.length) {
                        when {
                            line[i] == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                                sb.append('"'); i += 2
                            }
                            line[i] == '"' -> { i++; break }
                            else           -> sb.append(line[i++])
                        }
                    }
                    fields.add(sb.toString())
                    if (i < line.length && line[i] == ',') i++
                } else {
                    val end = line.indexOf(',', i).takeIf { it >= 0 } ?: line.length
                    fields.add(line.substring(i, end))
                    i = end + 1
                }
            }
            return fields
        }

        internal fun sha256Prefix(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }.substring(0, 16)
        }
    }
}
