package app.mmmap.domain.model

data class FoursquareDetail(
    val photoUrl: String?,
    val openingHours: List<String>,
    val isOpenNow: Boolean?,
    val phone: String?,
    val rating: Double?,
)
