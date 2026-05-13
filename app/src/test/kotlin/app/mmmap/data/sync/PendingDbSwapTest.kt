package app.mmmap.data.sync

import app.mmmap.data.db.AppDatabase
import app.mmmap.di.applyPendingUpdate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PendingDbSwapTest {

    private val tmpDir: File = Files.createTempDirectory("swap_test").toFile()
    private val filesDir = File(tmpDir, "files").apply { mkdirs() }
    private val dbDir = File(tmpDir, "databases").apply { mkdirs() }

    private fun pending() = File(filesDir, DatasetSyncWorker.PENDING_DB_NAME)
    private fun dbFile() = File(dbDir, AppDatabase.DB_NAME)

    @After fun tearDown() { tmpDir.deleteRecursively() }

    @Test fun noPendingFile_dbFileUnchanged() {
        dbFile().writeText("original")

        applyPendingUpdate(pending(), dbFile())

        assertEquals("original", dbFile().readText())
        assertFalse(pending().exists())
    }

    @Test fun pendingFileExists_copiedToDbPath_pendingDeleted() {
        pending().writeText("new db content")

        applyPendingUpdate(pending(), dbFile())

        assertTrue(dbFile().exists())
        assertEquals("new db content", dbFile().readText())
        assertFalse(pending().exists())
    }

    @Test fun pendingFileExists_overwritesExistingDb() {
        dbFile().writeText("old db")
        pending().writeText("new db")

        applyPendingUpdate(pending(), dbFile())

        assertEquals("new db", dbFile().readText())
        assertFalse(pending().exists())
    }

    @Test fun pendingFileExists_createsParentDirsIfMissing() {
        val nestedDb = File(tmpDir, "a/b/c/${AppDatabase.DB_NAME}")
        pending().writeText("data")

        applyPendingUpdate(pending(), nestedDb)

        assertTrue(nestedDb.exists())
        assertEquals("data", nestedDb.readText())
    }

    @Test fun swapFails_pendingStillDeleted_noThrow() {
        pending().writeText("data")
        // Make dbFile() a directory so copyTo fails
        dbFile().mkdirs()

        applyPendingUpdate(pending(), dbFile())

        assertFalse(pending().exists())
    }
}
