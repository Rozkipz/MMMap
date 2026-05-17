package app.mmmap.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.mmmap.data.db.dao.RestaurantDao
import app.mmmap.data.db.dao.VisitedDao
import app.mmmap.data.db.entities.RestaurantEntity
import app.mmmap.data.db.entities.VisitedRestaurantEntity

@Database(
    entities = [RestaurantEntity::class, VisitedRestaurantEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun restaurantDao(): RestaurantDao
    abstract fun visitedDao(): VisitedDao

    companion object {
        const val DB_NAME = "michelin.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS visited_restaurant (
                        restaurant_id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        award TEXT,
                        cuisine TEXT,
                        visited_at INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS foursquare_cache")
            }
        }
    }
}
