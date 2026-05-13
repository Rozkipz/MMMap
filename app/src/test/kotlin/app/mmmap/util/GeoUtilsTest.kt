package app.mmmap.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    @Test fun samePointIsZero() {
        assertEquals(0f, haversineKm(51.5, -0.1, 51.5, -0.1), 0.01f)
    }

    @Test fun londonToParisApprox343km() {
        // London: 51.5074, -0.1278  Paris: 48.8566, 2.3522
        val km = haversineKm(51.5074, -0.1278, 48.8566, 2.3522)
        assertEquals(343f, km, 5f)
    }

    @Test fun londonToNewYorkApprox5570km() {
        // New York: 40.7128, -74.0060
        val km = haversineKm(51.5074, -0.1278, 40.7128, -74.0060)
        assertEquals(5570f, km, 20f)
    }

    @Test fun symmetrical() {
        val a = haversineKm(48.8566, 2.3522, 51.5074, -0.1278)
        val b = haversineKm(51.5074, -0.1278, 48.8566, 2.3522)
        assertEquals(a, b, 0.001f)
    }

    @Test fun acrossEquator() {
        // Nairobi (1.29°S) to Kampala (0.31°N) ≈ 500 km
        val km = haversineKm(-1.2921, 36.8219, 0.3136, 32.5811)
        assertTrue("expected ~500 km, got $km", km in 450f..550f)
    }

    @Test fun veryShortDistanceNonZero() {
        // 0.001 degree apart ≈ ~111 metres
        val km = haversineKm(51.5, -0.1, 51.501, -0.1)
        assertTrue("expected ~0.111 km, got $km", km in 0.10f..0.12f)
    }
}
