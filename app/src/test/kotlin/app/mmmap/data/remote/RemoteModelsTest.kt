package app.mmmap.data.remote

import app.mmmap.data.remote.models.FsqGeocodes
import app.mmmap.data.remote.models.FsqHours
import app.mmmap.data.remote.models.FsqHoursEntry
import app.mmmap.data.remote.models.FsqLatLon
import app.mmmap.data.remote.models.FsqPhoto
import app.mmmap.data.remote.models.FsqPlaceResponse
import app.mmmap.data.remote.models.FsqSearchResponse
import app.mmmap.data.remote.models.FsqSearchResult
import app.mmmap.data.remote.models.GitHubContentsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteModelsTest {

    @Test fun fsqLatLon_fieldsRoundtrip() {
        val ll = FsqLatLon(latitude = 48.854, longitude = 2.332)
        assertEquals(48.854, ll.latitude, 0.0001)
        assertEquals(2.332, ll.longitude, 0.0001)
    }

    @Test fun fsqGeocodes_withMain() {
        val gc = FsqGeocodes(main = FsqLatLon(51.5, -0.1))
        assertEquals(51.5, gc.main!!.latitude, 0.0001)
    }

    @Test fun fsqGeocodes_nullMain() {
        assertNull(FsqGeocodes().main)
    }

    @Test fun fsqHoursEntry_fields() {
        val entry = FsqHoursEntry(day = 1, open = "09:00", close = "22:00")
        assertEquals(1, entry.day)
        assertEquals("09:00", entry.open)
        assertEquals("22:00", entry.close)
    }

    @Test fun fsqPhoto_urlDefaultSize() {
        val photo = FsqPhoto(prefix = "https://cdn.example.com/", suffix = "/img.jpg")
        assertEquals("https://cdn.example.com/800x800/img.jpg", photo.url())
    }

    @Test fun fsqPhoto_urlCustomSize() {
        val photo = FsqPhoto(prefix = "https://cdn.example.com/", suffix = "/img.jpg")
        assertEquals("https://cdn.example.com/400x400/img.jpg", photo.url(400))
    }

    @Test fun fsqHours_openNow() {
        val hours = FsqHours(openNow = true)
        assertTrue(hours.openNow == true)
    }

    @Test fun fsqHours_displayLines() {
        val hours = FsqHours(display = "Mon-Fri 9-17\nSat 10-16")
        assertEquals("Mon-Fri 9-17\nSat 10-16", hours.display)
    }

    @Test fun fsqHours_regularEntries() {
        val entry = FsqHoursEntry(day = 2, open = "12:00", close = "23:00")
        val hours = FsqHours(regular = listOf(entry))
        assertEquals(1, hours.regular!!.size)
        assertEquals(2, hours.regular!![0].day)
    }

    @Test fun fsqSearchResult_distanceNull() {
        val result = FsqSearchResult(fsqId = "fsq1", name = "Test", distance = null)
        assertNull(result.distance)
    }

    @Test fun fsqSearchResponse_defaultsToEmpty() {
        assertTrue(FsqSearchResponse().results.isEmpty())
    }

    @Test fun fsqPlaceResponse_fieldsDefaultNull() {
        val place = FsqPlaceResponse(fsqId = "fsq1")
        assertNull(place.photos)
        assertNull(place.hours)
        assertNull(place.tel)
        assertNull(place.rating)
    }

    @Test fun gitHubContentsResponse_sha() {
        val resp = GitHubContentsResponse(sha = "abc123def456")
        assertEquals("abc123def456", resp.sha)
    }
}
