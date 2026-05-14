package app.mmmap.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class CsvParserTest {

    private fun reader(s: String) = BufferedReader(StringReader(s))

    // ── splitCsvRecord ────────────────────────────────────────────────────────

    @Test fun simpleFields() {
        assertEquals(listOf("a", "b", "c"), DatasetSyncWorker.splitCsvRecord("a,b,c"))
    }

    @Test fun quotedFieldWithComma() {
        assertEquals(listOf("a", "b,c", "d"), DatasetSyncWorker.splitCsvRecord("""a,"b,c",d"""))
    }

    @Test fun doubledQuoteInsideField() {
        assertEquals(listOf("say \"hi\""), DatasetSyncWorker.splitCsvRecord(""""say ""hi"""""))
    }

    @Test fun emptyFields() {
        assertEquals(listOf("a", "", "c"), DatasetSyncWorker.splitCsvRecord("a,,c"))
    }

    @Test fun trailingComma() {
        val fields = DatasetSyncWorker.splitCsvRecord("a,b,")
        assertEquals(3, fields.size)
        assertEquals("", fields[2])
    }

    // ── csvRecords ────────────────────────────────────────────────────────────

    @Test fun multipleRecords() {
        val csv = "a,b\nc,d\ne,f"
        val records = DatasetSyncWorker.csvRecords(reader(csv))
        assertEquals(3, records.size)
        assertEquals(listOf("a", "b"), records[0])
        assertEquals(listOf("c", "d"), records[1])
    }

    @Test fun multilineQuotedField() {
        val csv = "\"line1\nline2\",b\nc,d"
        val records = DatasetSyncWorker.csvRecords(reader(csv))
        assertEquals(2, records.size)
        assertEquals("line1\nline2", records[0][0])
    }

    // ── parseCsv ──────────────────────────────────────────────────────────────

    @Test fun parsesHeaderAndRows() {
        val csv = """
            Name,Address,Location,Price,Cuisine,Longitude,Latitude,PhoneNumber,Url,WebsiteUrl,Award,GreenStar,FacilitiesAndServices,Description
            Le Test,1 Rue de la Paix,Paris,${'$'}${'$'},French,2.3308,48.8698,+33123456,https://guide.michelin.com/test,,1 MICHELIN Star,False,,Great place
        """.trimIndent()
        val entities = DatasetSyncWorker.parseCsv(reader(csv))
        assertEquals(1, entities.size)
        val e = entities[0]
        assertEquals("Le Test", e.name)
        assertEquals("Paris", e.location)
        assertEquals(2.3308, e.longitude, 0.0001)
        assertEquals(48.8698, e.latitude, 0.0001)
        assertEquals("1 MICHELIN Star", e.award)
        assertEquals(false, e.greenStar)
        assertEquals("Great place", e.description)
    }

    @Test fun skipsRowsWithMissingUrl() {
        val csv = """
            Name,Address,Location,Price,Cuisine,Longitude,Latitude,PhoneNumber,Url,WebsiteUrl,Award,GreenStar,FacilitiesAndServices,Description
            Bad Row,addr,,,French,2.0,48.0,,,1 Star,False,,
        """.trimIndent()
        assertTrue(DatasetSyncWorker.parseCsv(reader(csv)).isEmpty())
    }

    @Test fun skipsRowsWithInvalidCoordinates() {
        val csv = """
            Name,Address,Location,Price,Cuisine,Longitude,Latitude,PhoneNumber,Url,WebsiteUrl,Award,GreenStar,FacilitiesAndServices,Description
            Bad Row,addr,,,French,notanumber,48.0,,https://guide.michelin.com/r,,1 Star,False,,
        """.trimIndent()
        assertTrue(DatasetSyncWorker.parseCsv(reader(csv)).isEmpty())
    }

    @Test fun greenStarParsedCorrectly() {
        val csv = """
            Name,Address,Location,Price,Cuisine,Longitude,Latitude,PhoneNumber,Url,WebsiteUrl,Award,GreenStar,FacilitiesAndServices,Description
            Green,addr,,,French,2.0,48.0,,https://guide.michelin.com/g,,Bib Gourmand,True,,
            Plain,addr,,,French,2.0,48.0,,https://guide.michelin.com/p,,Bib Gourmand,False,,
        """.trimIndent()
        val entities = DatasetSyncWorker.parseCsv(reader(csv))
        assertEquals(2, entities.size)
        assertTrue(entities[0].greenStar)
        assertTrue(!entities[1].greenStar)
    }

    @Test fun sha256PrefixIsStable() {
        val id = DatasetSyncWorker.sha256Prefix("https://guide.michelin.com/en/ile-de-france/paris/restaurant/le-meurice")
        assertEquals(16, id.length)
        assertEquals(id, DatasetSyncWorker.sha256Prefix("https://guide.michelin.com/en/ile-de-france/paris/restaurant/le-meurice"))
    }
}
