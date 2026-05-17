package app.mmmap.data.repository

import android.content.Context
import android.content.res.AssetManager
import app.mmmap.data.places.CustomPlaceCatalog
import app.mmmap.domain.model.CustomPlaceCollection
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class CustomPlaceRepositoryTest {

    private fun contextWithJson(json: String): Context {
        val assetManager = mockk<AssetManager> {
            every { open(any()) } returns ByteArrayInputStream(json.toByteArray())
        }
        return mockk<Context> {
            every { assets } returns assetManager
        }
    }

    @Test fun loadActive_parsesNameAndCoordinates() = runTest {
        val json = """
            [
              { "id": "test/a", "name": "Bar A", "latitude": 36.838, "longitude": -2.465, "address": "Calle X, 1", "notes": null },
              { "id": "test/b", "name": "Bar B", "latitude": 36.840, "longitude": -2.467, "address": null, "notes": "Nice spot" }
            ]
        """.trimIndent()

        val repo = CustomPlaceRepository(contextWithJson(json))
        val places = repo.loadActive()

        assertEquals(2, places.size)
        assertEquals("test/a", places[0].id)
        assertEquals("Bar A", places[0].name)
        assertEquals(36.838, places[0].latitude, 0.001)
        assertEquals(-2.465, places[0].longitude, 0.001)
        assertEquals("Calle X, 1", places[0].address)
        assertNull(places[0].notes)

        assertEquals("test/b", places[1].id)
        assertNull(places[1].address)
        assertEquals("Nice spot", places[1].notes)
    }

    @Test fun loadActive_cachesOnSecondCall() = runTest {
        val json = """[{ "id": "a", "name": "X", "latitude": 1.0, "longitude": 2.0 }]"""
        val assetManager = mockk<AssetManager>()
        var callCount = 0
        every { assetManager.open(any()) } answers {
            callCount++
            ByteArrayInputStream(json.toByteArray())
        }
        val context = mockk<Context> { every { assets } returns assetManager }

        val repo = CustomPlaceRepository(context)
        repo.loadActive()
        repo.loadActive()

        assertEquals(1, callCount)
    }
}
