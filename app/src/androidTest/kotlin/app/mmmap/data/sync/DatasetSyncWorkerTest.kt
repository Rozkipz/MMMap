package app.mmmap.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mmmap.data.remote.GitHubReleasesApi
import app.mmmap.data.remote.models.GithubAsset
import app.mmmap.data.remote.models.GithubRelease
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatasetSyncWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val releasesApi: GitHubReleasesApi = mockk()
    private val prefs: SyncPreferences = mockk()
    private val httpClient = OkHttpClient()

    private val pendingFile get() = File(context.filesDir, DatasetSyncWorker.PENDING_DB_NAME)
    private val tempDownload get() = File(context.cacheDir, "michelin_download.db")

    private fun worker() = DatasetSyncWorker(
        context, mockk<WorkerParameters>(relaxed = true), releasesApi, httpClient, prefs
    )

    @Before fun setUp() {
        server.start()
        pendingFile.delete()
        tempDownload.delete()
    }

    @After fun tearDown() {
        server.shutdown()
        pendingFile.delete()
        tempDownload.delete()
    }

    // Creates a minimal valid SQLite file with the restaurant table and enough rows.
    private fun validDbBytes(): ByteArray {
        val f = File.createTempFile("valid", ".db", context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
                db.execSQL("CREATE TABLE restaurant (id TEXT PRIMARY KEY, name TEXT)")
                repeat(150) { i -> db.execSQL("INSERT INTO restaurant VALUES ('r$i', 'R$i')") }
            }
            return f.readBytes()
        } finally {
            f.delete()
        }
    }

    private fun release(tag: String, url: String = "", size: Long = 0L) = GithubRelease(
        tagName = tag,
        assets = if (url.isEmpty()) emptyList()
                 else listOf(GithubAsset("michelin.db", url, size)),
    )

    // ── tag already current ───────────────────────────────────────────────────

    @Test fun tagMatchesCurrent_successNoPendingFile() = runTest {
        coEvery { prefs.lastReleaseTag() } returns "v1.0"
        coEvery { releasesApi.latestRelease() } returns release("v1.0")

        assertEquals(androidx.work.ListenableWorker.Result.success(), worker().doWork())
        assertFalse(pendingFile.exists())
    }

    // ── no .db asset in release ───────────────────────────────────────────────

    @Test fun noDbAsset_successNoPendingFile() = runTest {
        coEvery { prefs.lastReleaseTag() } returns "v1.0"
        coEvery { releasesApi.latestRelease() } returns GithubRelease(
            tagName = "v2.0",
            assets = listOf(GithubAsset("README.md", "http://example.com", 100)),
        )

        assertEquals(androidx.work.ListenableWorker.Result.success(), worker().doWork())
        assertFalse(pendingFile.exists())
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test fun newTagValidDb_pendingFileSaved_tagUpdated() = runTest {
        val bytes = validDbBytes()
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        coEvery { prefs.lastReleaseTag() } returns "v1.0"
        coEvery { releasesApi.latestRelease() } returns release(
            "v2.0", server.url("/db").toString(), bytes.size.toLong()
        )
        coEvery { prefs.setLastReleaseTag("v2.0") } just Runs

        val result = worker().doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertTrue(pendingFile.exists())
        coVerify(exactly = 1) { prefs.setLastReleaseTag("v2.0") }
    }

    // ── invalid schema ────────────────────────────────────────────────────────

    @Test fun invalidDb_failureNoPendingFile() = runTest {
        server.enqueue(MockResponse().setBody("this is not a sqlite database"))
        coEvery { prefs.lastReleaseTag() } returns "v1.0"
        coEvery { releasesApi.latestRelease() } returns release(
            "v2.0", server.url("/db").toString(), 29L
        )

        val result = worker().doWork()

        assertEquals(androidx.work.ListenableWorker.Result.failure(), result)
        assertFalse(pendingFile.exists())
    }

    @Test fun dbWithNoRestaurantTable_failureNoPendingFile() = runTest {
        val f = File.createTempFile("empty", ".db", context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
                db.execSQL("CREATE TABLE other (id TEXT)")
            }
            server.enqueue(MockResponse().setBody(Buffer().write(f.readBytes())))
            coEvery { prefs.lastReleaseTag() } returns "v1.0"
            coEvery { releasesApi.latestRelease() } returns release(
                "v2.0", server.url("/db").toString(), f.length()
            )

            assertEquals(androidx.work.ListenableWorker.Result.failure(), worker().doWork())
            assertFalse(pendingFile.exists())
        } finally {
            f.delete()
        }
    }

    @Test fun dbWithTooFewRows_failureNoPendingFile() = runTest {
        val f = File.createTempFile("sparse", ".db", context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
                db.execSQL("CREATE TABLE restaurant (id TEXT PRIMARY KEY)")
                repeat(10) { i -> db.execSQL("INSERT INTO restaurant VALUES ('r$i')") }
            }
            server.enqueue(MockResponse().setBody(Buffer().write(f.readBytes())))
            coEvery { prefs.lastReleaseTag() } returns "v1.0"
            coEvery { releasesApi.latestRelease() } returns release(
                "v2.0", server.url("/db").toString(), f.length()
            )

            assertEquals(androidx.work.ListenableWorker.Result.failure(), worker().doWork())
            assertFalse(pendingFile.exists())
        } finally {
            f.delete()
        }
    }

    // ── network errors ────────────────────────────────────────────────────────

    @Test fun http503_returnsRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        coEvery { prefs.lastReleaseTag() } returns "v1.0"
        coEvery { releasesApi.latestRelease() } returns release(
            "v2.0", server.url("/db").toString(), 100L
        )

        assertEquals(androidx.work.ListenableWorker.Result.retry(), worker().doWork())
        assertFalse(pendingFile.exists())
    }

    @Test fun apiThrows_returnsRetry() = runTest {
        coEvery { prefs.lastReleaseTag() } returns null
        coEvery { releasesApi.latestRelease() } throws RuntimeException("network failure")

        assertEquals(androidx.work.ListenableWorker.Result.retry(), worker().doWork())
    }

    // ── cleanup ───────────────────────────────────────────────────────────────

    @Test fun tempFileAlwaysDeletedOnSuccess() = runTest {
        val bytes = validDbBytes()
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        coEvery { prefs.lastReleaseTag() } returns "v1.0"
        coEvery { releasesApi.latestRelease() } returns release(
            "v2.0", server.url("/db").toString(), bytes.size.toLong()
        )
        coEvery { prefs.setLastReleaseTag(any()) } just Runs

        worker().doWork()

        assertFalse(tempDownload.exists())
    }

    @Test fun tempFileAlwaysDeletedOnFailure() = runTest {
        coEvery { prefs.lastReleaseTag() } returns null
        coEvery { releasesApi.latestRelease() } throws RuntimeException("error")

        worker().doWork()

        assertFalse(tempDownload.exists())
    }
}
