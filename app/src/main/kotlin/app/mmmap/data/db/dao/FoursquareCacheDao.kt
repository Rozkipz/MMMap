package app.mmmap.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.mmmap.data.db.entities.FoursquareCacheEntity

@Dao
interface FoursquareCacheDao {

    @Query("SELECT * FROM foursquare_cache WHERE restaurantId = :restaurantId")
    suspend fun get(restaurantId: String): FoursquareCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FoursquareCacheEntity)
}
