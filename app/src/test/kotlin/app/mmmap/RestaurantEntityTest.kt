package app.mmmap

import app.mmmap.data.db.entities.RestaurantEntity
import app.mmmap.domain.model.Distinction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantEntityTest {

    private fun entity(
        award: String? = null,
        cuisine: String? = "French",
        greenStar: Boolean = false,
    ) = RestaurantEntity(
        id = "abc123",
        name = "Test Restaurant",
        address = "1 Test St",
        location = "Mayfair",
        latitude = 51.5,
        longitude = -0.1,
        award = award,
        greenStar = greenStar,
        cuisine = cuisine,
        price = "£££",
        phoneNumber = "+44 20 0000 0000",
        url = "https://guide.michelin.com/test",
        websiteUrl = "https://testrestaurant.com",
        description = "A fine establishment",
        facilitiesAndServices = null,
    )

    @Test fun idPreserved() = assertEquals("abc123", entity().toDomain().id)

    @Test fun namePreserved() = assertEquals("Test Restaurant", entity().toDomain().name)

    @Test fun coordinatesPreserved() {
        val r = entity().toDomain()
        assertEquals(51.5, r.latitude, 0.0001)
        assertEquals(-0.1, r.longitude, 0.0001)
    }

    @Test fun awardMapsToDistinction() =
        assertEquals(Distinction.THREE_STAR, entity(award = "3 MICHELIN Stars").toDomain().distinction)

    @Test fun nullAwardMapsToSelected() =
        assertEquals(Distinction.SELECTED, entity(award = null).toDomain().distinction)

    @Test fun greenStarPreserved() = assertTrue(entity(greenStar = true).toDomain().greenStar)

    @Test fun greenStarFalse() = assertFalse(entity(greenStar = false).toDomain().greenStar)

    @Test fun nullCuisinePreserved() = assertNull(entity(cuisine = null).toDomain().cuisine)

    @Test fun nullFacilitiesPreserved() = assertNull(entity().toDomain().facilitiesAndServices)

    @Test fun michelinUrlMappedFromUrl() =
        assertEquals("https://guide.michelin.com/test", entity().toDomain().michelinUrl)
}
