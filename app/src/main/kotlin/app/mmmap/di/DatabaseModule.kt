package app.mmmap.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import app.mmmap.data.db.AppDatabase
import app.mmmap.data.db.dao.RestaurantDao
import app.mmmap.data.db.dao.VisitedDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.zip.GZIPInputStream
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            // The asset is gzipped, so createFromAsset can't be used directly. Room
            // consumes this stream once, on first open, to materialise the database.
            .createFromInputStream {
                GZIPInputStream(context.assets.open(AppDatabase.SEED_ASSET))
            }
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides fun provideRestaurantDao(db: AppDatabase): RestaurantDao = db.restaurantDao()

    @Provides fun provideVisitedDao(db: AppDatabase): VisitedDao = db.visitedDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
