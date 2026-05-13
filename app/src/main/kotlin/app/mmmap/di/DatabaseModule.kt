package app.mmmap.di

import android.content.Context
import androidx.room.Room
import app.mmmap.data.db.AppDatabase
import app.mmmap.data.db.dao.FoursquareCacheDao
import app.mmmap.data.db.dao.RestaurantDao
import app.mmmap.data.sync.DatasetSyncWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        // If a sync worker left a validated pending update, atomically apply it now,
        // before Room opens the database for the first time this process.
        applyPendingUpdate(
            pending = File(context.filesDir, DatasetSyncWorker.PENDING_DB_NAME),
            dbFile = context.getDatabasePath(AppDatabase.DB_NAME),
        )
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .createFromAsset(AppDatabase.DB_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideRestaurantDao(db: AppDatabase): RestaurantDao = db.restaurantDao()

    @Provides fun provideFoursquareCacheDao(db: AppDatabase): FoursquareCacheDao = db.foursquareCacheDao()
}

internal fun applyPendingUpdate(pending: File, dbFile: File) {
    if (!pending.exists()) return
    runCatching {
        dbFile.parentFile?.mkdirs()
        pending.copyTo(dbFile, overwrite = true)
    }
    pending.delete()
}
