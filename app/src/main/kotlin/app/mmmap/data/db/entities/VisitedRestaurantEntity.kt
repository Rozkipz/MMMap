package app.mmmap.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "visited_restaurant")
data class VisitedRestaurantEntity(
    @PrimaryKey @ColumnInfo(name = "restaurant_id") val restaurantId: String,
    @ColumnInfo(name = "name")       val name: String,
    @ColumnInfo(name = "latitude")   val latitude: Double,
    @ColumnInfo(name = "longitude")  val longitude: Double,
    @ColumnInfo(name = "award")      val award: String?,
    @ColumnInfo(name = "cuisine")    val cuisine: String?,
    @ColumnInfo(name = "visited_at") val visitedAt: Long,
)
