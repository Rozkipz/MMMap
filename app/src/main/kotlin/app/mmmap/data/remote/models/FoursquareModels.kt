package app.mmmap.data.remote.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FsqSearchResponse(
    val results: List<FsqSearchResult> = emptyList(),
)

@Serializable
data class FsqSearchResult(
    @SerialName("fsq_id") val fsqId: String,
    val name: String,
    val geocodes: FsqGeocodes? = null,
    val distance: Int? = null,
)

@Serializable
data class FsqGeocodes(
    val main: FsqLatLon? = null,
)

@Serializable
data class FsqLatLon(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class FsqPlaceResponse(
    @SerialName("fsq_id") val fsqId: String,
    val name: String? = null,
    val photos: List<FsqPhoto>? = null,
    val hours: FsqHours? = null,
    val tel: String? = null,
    val rating: Double? = null,
)

@Serializable
data class FsqPhoto(
    val prefix: String,
    val suffix: String,
) {
    fun url(width: Int = 800) = "${prefix}${width}x${width}${suffix}"
}

@Serializable
data class FsqHours(
    @SerialName("is_local_holiday") val isLocalHoliday: Boolean? = null,
    @SerialName("open_now") val openNow: Boolean? = null,
    @SerialName("regular") val regular: List<FsqHoursEntry>? = null,
    @SerialName("display") val display: String? = null,
)

@Serializable
data class FsqHoursEntry(
    val day: Int,
    val open: String,
    val close: String,
)
