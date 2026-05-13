package app.mmmap.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "foursquare_cache")
data class FoursquareCacheEntity(
    @PrimaryKey val restaurantId: String,
    val fsqId: String?,
    val photoUrl: String?,
    val openingHoursJson: String?,
    val phone: String?,
    val rating: Double?,
    val fetchedAt: Long,
) {
    companion object {
        private const val PHOTO_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val HOURS_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }

    fun isPhotoFresh(now: Long = System.currentTimeMillis()) = now - fetchedAt < PHOTO_TTL_MS
    fun isHoursFresh(now: Long = System.currentTimeMillis()) = now - fetchedAt < HOURS_TTL_MS
}
