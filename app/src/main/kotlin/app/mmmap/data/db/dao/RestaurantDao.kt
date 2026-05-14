package app.mmmap.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.mmmap.data.db.entities.RestaurantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RestaurantDao {

    @Query("DELETE FROM restaurant")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(restaurants: List<RestaurantEntity>)

    @Query("""
        SELECT * FROM restaurant
        WHERE latitude BETWEEN :minLat AND :maxLat
          AND longitude BETWEEN :minLon AND :maxLon
          AND (:award IS NULL OR award = :award)
          AND (:cuisine IS NULL OR cuisine = :cuisine)
          AND (:price IS NULL OR price = :price)
        ORDER BY name ASC
    """)
    fun observeInBounds(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        award: String?,
        cuisine: String?,
        price: String?,
    ): Flow<List<RestaurantEntity>>

    @Query("SELECT * FROM restaurant WHERE id = :id")
    suspend fun getById(id: String): RestaurantEntity?

    @Query("SELECT COUNT(*) FROM restaurant")
    suspend fun count(): Int

    @Query("SELECT DISTINCT cuisine FROM restaurant WHERE cuisine IS NOT NULL ORDER BY cuisine ASC")
    suspend fun distinctCuisines(): List<String>

    @Query("SELECT DISTINCT price FROM restaurant WHERE price IS NOT NULL ORDER BY price ASC")
    suspend fun distinctPrices(): List<String>
}
