package app.mmmap.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.mmmap.data.db.entities.VisitedRestaurantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedDao {

    @Query("SELECT restaurant_id FROM visited_restaurant")
    fun observeAllIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM visited_restaurant WHERE restaurant_id = :id)")
    fun observeIsVisited(id: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VisitedRestaurantEntity)

    @Query("DELETE FROM visited_restaurant WHERE restaurant_id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM visited_restaurant ORDER BY visited_at DESC")
    suspend fun getAll(): List<VisitedRestaurantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<VisitedRestaurantEntity>)

    @Query("SELECT COUNT(*) FROM visited_restaurant")
    suspend fun count(): Int
}
