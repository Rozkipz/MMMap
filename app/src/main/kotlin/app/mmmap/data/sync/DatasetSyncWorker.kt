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
            // Absolute floor for the very first sync, plus a relative floor once we already
            // hold data: a parse that yields ≥100 rows but far fewer than we have would
            // otherwise wipe the dataset AND store the SHA, so it would never be retried.
            val existing = db.restaurantDao().count()
            if (entities.size < MIN_ROWS || entities.size < existing / 2) {
                return@withContext Result.failure()
            }

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
            val body = response.body
            body.charStream().buffered().use { parseCsv(it) }
        }
    }

    companion object {
        const val TAG = "DatasetSyncWorker"

        /** Absolute floor on a parsed dataset; the real one is ~19,000 rows. */
        internal const val MIN_ROWS = 100

        // CSV columns (0-indexed):
        // 0:Name 1:Address 2:Location 3:Price 4:Cuisine 5:Longitude 6:Latitude
        // 7:PhoneNumber 8:Url 9:WebsiteUrl 10:Award 11:GreenStar 12:FacilitiesAndServices 13:Description
        internal val EXPECTED_HEADER = listOf(
            "Name", "Address", "Location", "Price", "Cuisine", "Longitude", "Latitude",
            "PhoneNumber", "Url", "WebsiteUrl", "Award", "GreenStar",
            "FacilitiesAndServices", "Description",
        )

        /**
         * Thrown when the upstream CSV's columns no longer match [EXPECTED_HEADER].
         *
         * Every field is read positionally, so an inserted or reordered column would parse
         * cleanly into the wrong properties — awards becoming URLs, for instance, which
         * silently breaks award filtering while still looking like a successful sync.
         */
        class CsvSchemaException(message: String) : IOException(message)

        internal fun parseCsv(reader: BufferedReader): List<RestaurantEntity> {
            val entities = mutableListOf<RestaurantEntity>()
            val records = csvRecords(reader)
            val header = records.firstOrNull()?.map { it.trim().trim('"') }
                ?: throw CsvSchemaException("CSV was empty")
            // Trailing columns may be added upstream without breaking positional reads;
            // the prefix we actually index into must match exactly.
            val expected = EXPECTED_HEADER.take(minOf(EXPECTED_HEADER.size, header.size))
            val actual = header.take(expected.size)
            if (actual.map { it.lowercase() } != expected.map { it.lowercase() }) {
                throw CsvSchemaException(
                    "Unexpected CSV columns: expected $expected but got $actual"
                )
            }
            for (record in records.drop(1)) {
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
                        price                 = normalisePrice(record[3]),
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

        // The upstream dataset writes "none" where a restaurant has no price band.
        // LENGTH("none") is 4, which would otherwise place it in the top price tier
        // alongside "$$$$" / "€€€€" — so normalise it away at the point of parse.
        internal fun normalisePrice(raw: String): String? =
            raw.trim().takeIf { it.isNotEmpty() && !it.equals("none", ignoreCase = true) }

        // A record may legitimately span several lines when a field is quoted. Accumulate
        // until the running quote count is even, i.e. we're no longer inside a quoted field.
        //
        // MAX_PENDING_LINES bounds that accumulation: one stray unescaped quote would
        // otherwise make the count odd forever, glue the entire remainder of the file into
        // a single record, and silently drop every row after it (while still clearing the
        // row-count guard). Give up on the run instead and carry on with the next line.
        private const val MAX_PENDING_LINES = 64

        internal fun csvRecords(reader: BufferedReader): List<List<String>> {
            val records = mutableListOf<List<String>>()
            val pending = StringBuilder()
            var pendingLines = 0
            reader.forEachLine { line ->
                if (pending.isNotEmpty()) pending.append('\n')
                pending.append(line)
                pendingLines++
                if (pending.count { it == '"' } % 2 == 0) {
                    records.add(splitCsvRecord(pending.toString()))
                    pending.clear()
                    pendingLines = 0
                } else if (pendingLines >= MAX_PENDING_LINES) {
                    // Unbalanced quote — emit what we have and resynchronise.
                    records.add(splitCsvRecord(pending.toString()))
                    pending.clear()
                    pendingLines = 0
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
                    if (i < line.length && line[i] == ',') i++ else break
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
