package app.mmmap.data.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.mmmap.data.db.AppDatabase
import app.mmmap.data.db.dao.RestaurantDao
import app.mmmap.data.remote.GitHubContentsApi
import app.mmmap.data.remote.models.GitHubContentsResponse
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DatasetSyncWorkerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val context = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)
    private val contentsApi = mockk<GitHubContentsApi>()
    private val okHttpClient = mockk<OkHttpClient>()
    private val prefs = mockk<SyncPreferences>()
    private val db = mockk<AppDatabase>(relaxed = true)
    private val dao = mockk<RestaurantDao>(relaxed = true)

    private lateinit var worker: DatasetSyncWorker

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { db.restaurantDao() } returns dao
        // Room 2.8 moved withTransaction to RoomDatabaseKt__RoomDatabase_androidKt
        mockkStatic("androidx.room.RoomDatabaseKt__RoomDatabase_androidKt")
        coEvery { db.withTransaction<Unit>(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            (invocation.args[1] as suspend () -> Unit).invoke()
        }
        worker = DatasetSyncWorker(context, workerParams, contentsApi, okHttpClient, prefs, db)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ── SHA cache hit ─────────────────────────────────────────────────────────

    @Test fun shaCacheHit_returnsSuccessWithoutDownload() = runTest {
        coEvery { contentsApi.csvMetadata() } returns GitHubContentsResponse(sha = "known-sha")
        coEvery { prefs.lastCsvSha() } returns "known-sha"

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { db.withTransaction(any()) }
    }

    // ── min-row guard ─────────────────────────────────────────────────────────

    @Test fun tooFewRows_returnsFailure() = runTest {
        coEvery { contentsApi.csvMetadata() } returns GitHubContentsResponse(sha = "new-sha")
        coEvery { prefs.lastCsvSha() } returns null
        stubHttpResponse(csvWithRows(50))

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { db.withTransaction(any()) }
    }

    @Test fun exactlyOneHundredRows_succeedsAndPersists() = runTest {
        coEvery { contentsApi.csvMetadata() } returns GitHubContentsResponse(sha = "new-sha")
        coEvery { prefs.lastCsvSha() } returns null
        coJustRun { prefs.setLastCsvSha(any()) }
        stubHttpResponse(csvWithRows(100))

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { prefs.setLastCsvSha("new-sha") }
    }

    // ── error handling ────────────────────────────────────────────────────────

    @Test fun networkException_returnsRetry() = runTest {
        coEvery { contentsApi.csvMetadata() } throws RuntimeException("network error")
        coEvery { prefs.lastCsvSha() } returns null

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test fun httpErrorResponse_returnsRetry() = runTest {
        coEvery { contentsApi.csvMetadata() } returns GitHubContentsResponse(sha = "new-sha")
        coEvery { prefs.lastCsvSha() } returns null
        stubHttpResponse("", statusCode = 404)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    // ── success path ──────────────────────────────────────────────────────────

    @Test fun successfulDownload_clearsAndInsertsAndPersistsSha() = runTest {
        coEvery { contentsApi.csvMetadata() } returns GitHubContentsResponse(sha = "fresh-sha")
        coEvery { prefs.lastCsvSha() } returns null
        coJustRun { prefs.setLastCsvSha(any()) }
        coJustRun { dao.deleteAll() }
        coJustRun { dao.insertAll(any()) }
        stubHttpResponse(csvWithRows(150))

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { dao.deleteAll() }
        coVerify { dao.insertAll(match { it.size == 150 }) }
        coVerify { prefs.setLastCsvSha("fresh-sha") }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun stubHttpResponse(body: String, statusCode: Int = 200) {
        val call = mockk<okhttp3.Call>()
        val response = Response.Builder()
            .request(Request.Builder().url(GitHubContentsApi.CSV_RAW_URL).build())
            .protocol(Protocol.HTTP_1_1)
            .code(statusCode)
            .message(if (statusCode == 200) "OK" else "Error")
            .body(body.toResponseBody("text/plain".toMediaType()))
            .build()
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } returns response
    }

    private fun csvWithRows(count: Int): String {
        val header = "Name,Address,Location,Price,Cuisine,Longitude,Latitude,Phone,Url,Website,Award,GreenStar,Facilities,Description"
        val rows = (1..count).joinToString("\n") { i ->
            "Restaurant $i,1 Test St,London,£££,French,-0.1,51.5,+44 20 0000 $i,https://guide.michelin.com/$i,,1 MICHELIN Star,False,,"
        }
        return "$header\n$rows"
    }
}
