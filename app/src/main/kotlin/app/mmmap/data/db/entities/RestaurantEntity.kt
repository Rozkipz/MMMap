package app.mmmap.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.mmmap.domain.model.Distinction
import app.mmmap.domain.model.Restaurant

@Entity(tableName = "restaurant")
data class RestaurantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val location: String?,
    val latitude: Double,
    val longitude: Double,
    val award: String?,
    val greenStar: Boolean,
    val cuisine: String?,
    val price: String?,
    val phoneNumber: String?,
    val url: String,
    val websiteUrl: String?,
    val description: String?,
    val facilitiesAndServices: String?,
) {
    fun toDomain() = Restaurant(
        id = id,
        name = name,
        address = address,
        location = location,
        latitude = latitude,
        longitude = longitude,
        distinction = Distinction.fromAward(award),
        greenStar = greenStar,
        cuisine = cuisine,
        price = price,
        phoneNumber = phoneNumber,
        michelinUrl = url,
        websiteUrl = websiteUrl,
        description = description,
        facilitiesAndServices = facilitiesAndServices,
    )
}
