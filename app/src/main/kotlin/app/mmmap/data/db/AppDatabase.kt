package app.mmmap.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import app.mmmap.data.db.dao.FoursquareCacheDao
import app.mmmap.data.db.dao.RestaurantDao
import app.mmmap.data.db.entities.FoursquareCacheEntity
import app.mmmap.data.db.entities.RestaurantEntity

@Database(
    entities = [RestaurantEntity::class, FoursquareCacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun restaurantDao(): RestaurantDao
    abstract fun foursquareCacheDao(): FoursquareCacheDao

    companion object {
        const val DB_NAME = "michelin.db"
    }
}
