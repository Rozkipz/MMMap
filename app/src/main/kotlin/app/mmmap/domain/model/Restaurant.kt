package app.mmmap.domain.model

data class Restaurant(
    val id: String,
    val name: String,
    val address: String,
    val location: String?,
    val latitude: Double,
    val longitude: Double,
    val distinction: Distinction,
    val greenStar: Boolean,
    val cuisine: String?,
    val price: String?,
    val phoneNumber: String?,
    val michelinUrl: String,
    val websiteUrl: String?,
    val description: String?,
    val facilitiesAndServices: String?,
)
