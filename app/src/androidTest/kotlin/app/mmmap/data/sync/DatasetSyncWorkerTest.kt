package app.mmmap.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import app.mmmap.data.db.AppDatabase
import app.mmmap.data.remote.GitHubContentsApi
import app.mmmap.data.remote.models.GitHubContentsResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatasetSyncWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val contentsApi: GitHubContentsApi = mockk()
    private val prefs: SyncPreferences = mockk()
    private val httpClient = OkHttpClient()
    private lateinit var db: AppDatabase

    private fun worker() = DatasetSyncWorker(
        context, mockk<WorkerParameters>(relaxed = true),
        contentsApi, httpClient, prefs, db,
    )

    @Before fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun csvWithRows(n: Int): String = buildString {
        appendLine("Name,Address,Location,Price,Cuisine,Longitude,Latitude,PhoneNumber,Url,WebsiteUrl,Award,GreenStar,FacilitiesAndServices,Description")
        repeat(n) { i ->
            appendLine("Restaurant $i,${i} Main St,,\$\$,French,2.3$i,48.8$i,,https://guide.michelin.com/r$i,,1 MICHELIN Star,False,,")
        }
    }

    // ── SHA already current ───────────────────────────────────────────────────

    @Test fun shaMatchesCurrent_successNoDbWrite() = runTest {
        coEvery { prefs.lastCsvSha() } returns "abc123"
        coEvery { contentsApi.csvMetadata() } returns GitHubContentsResponse("abc123")

        assertEquals(Result.success(), worker().doWork())
        assertEquals(0, db.restaurantDao().count())
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test fun newSha_downloadParsedInserted_shaUpdated() = runTest {
        val csv = csvWithRows(150)
        server.enqueue(MockResponse().setBody(csv))
        coEvery { prefs.lastCsvSha() } returns "old"
        coEvery { contentsApi.csvMetadata() } returns GitHubContentsResponse("new")
        coEvery { prefs.setLastCsvSha("new") } just Runs

        // Override CSV_RAW_URL by pointing the worker's OkHttpClient at our mock server.
        // The worker constructs the request from the constant, so we intercept at the
        // network layer by using a custom client that redirects to the mock server.
        val redirectingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .url(server.url("/csv"))
                    .build()
                chain.proceed(req)
            }
            .build()
        val w = DatasetSyncWorker(
            context, mockk(relaxed = true), contentsApi, redirectingClient, prefs, db
        )

        val result = w.doWork()

        assertEquals(Result.success(), result)
        assertTrue(db.restaurantDao().count() >= 150)
        coVerify(exactly = 1) { prefs.setLastCsvSha("new") }
    }

    // ── too few rows ──────────────────────────────────────────────────────────

    @Test fun csvWithTooFewRows_failure() = runTest {
        val csv = csvWithRows(5)
        server.enqueue(MockResponse().setBody(csv))
        coEvery { prefs.lastCsvSha() } returns "old"
        coEvery { contentsApi.csvMetadata() } returns GitHubContentsResponse("new")

        val redirectingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/csv")).build())
            }
            .build()
        val w = DatasetSyncWorker(
            context, mockk(relaxed = true), contentsApi, redirectingClient, prefs, db
        )

        assertEquals(Result.failure(), w.doWork())
        assertEquals(0, db.restaurantDao().count())
    }

    // ── network errors ────────────────────────────────────────────────────────

    @Test fun contentsApiThrows_returnsRetry() = runTest {
        coEvery { prefs.lastCsvSha() } returns null
        coEvery { contentsApi.csvMetadata() } throws RuntimeException("network failure")

        assertEquals(Result.retry(), worker().doWork())
    }

    @Test fun csvHttp503_returnsRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        coEvery { prefs.lastCsvSha() } returns "old"
        coEvery { contentsApi.csvMetadata() } returns GitHubContentsResponse("new")

        val redirectingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/csv")).build())
            }
            .build()
        val w = DatasetSyncWorker(
            context, mockk(relaxed = true), contentsApi, redirectingClient, prefs, db
        )

        assertEquals(Result.retry(), w.doWork())
    }
}
