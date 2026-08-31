package app.mmmap.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Guards the bundled seed asset. It is a binary blob that only a script regenerates, so
 * nothing else would notice if it were committed uncompressed, truncated, or built from a
 * schema Room does not expect — the failure would surface as a crash on first launch of a
 * fresh install, which no other test exercises.
 *
 * Unit tests run with the module directory as their working directory.
 */
class SeedAssetTest {

    private val asset = File("src/main/assets/${AppDatabase.SEED_ASSET}")
    private val provenance = File("src/main/res/values/dataset_provenance.xml")

    @Test fun seedAssetExistsAndIsGzipped() {
        assertTrue("${asset.path} is missing — run `just seed-db`", asset.exists())
        // 0x1f 0x8b is the gzip magic. Catches the asset being committed raw, which would
        // still be a valid SQLite file and so would slip past a looser check.
        val magic = asset.inputStream().use { byteArrayOf(it.read().toByte(), it.read().toByte()) }
        assertEquals("not gzipped", 0x1f.toByte(), magic[0])
        assertEquals("not gzipped", 0x8b.toByte(), magic[1])
    }

    @Test fun seedAssetInflatesToRoomCompatibleSqlite() {
        val bytes = GZIPInputStream(asset.inputStream().buffered()).use { it.readBytes() }

        assertTrue("inflated asset is implausibly small: ${bytes.size}", bytes.size > 1_000_000)
        assertEquals(
            "not a SQLite database",
            "SQLite format 3",
            String(bytes, 0, 15, Charsets.US_ASCII),
        )
        // Byte 60 of the header is user_version (big-endian int). Room skips migrations on
        // first open only when this matches the @Database version the asset was built for.
        val userVersion = ((bytes[60].toInt() and 0xff) shl 24) or
            ((bytes[61].toInt() and 0xff) shl 16) or
            ((bytes[62].toInt() and 0xff) shl 8) or
            (bytes[63].toInt() and 0xff)
        assertEquals("unexpected PRAGMA user_version", 2, userVersion)
    }

    @Test fun provenanceRecordsAFullBlobSha() {
        assertTrue("${provenance.path} is missing — run `just seed-db`", provenance.exists())
        val sha = Regex("""name="bundled_csv_sha"[^>]*>([0-9a-f]+)<""")
            .find(provenance.readText())
            ?.groupValues?.get(1)
        assertEquals("expected a 40-char git blob SHA", 40, sha?.length)
    }
}
