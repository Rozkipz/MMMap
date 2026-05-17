package app.mmmap.domain.model

data class CustomPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val notes: String? = null,
    val description: String? = null,
    val link: String? = null,
)
