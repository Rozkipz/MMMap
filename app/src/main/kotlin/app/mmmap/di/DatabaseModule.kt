package app.mmmap.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import app.mmmap.data.db.AppDatabase
import app.mmmap.data.db.dao.FoursquareCacheDao
import app.mmmap.data.db.dao.RestaurantDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides fun provideRestaurantDao(db: AppDatabase): RestaurantDao = db.restaurantDao()

    @Provides fun provideFoursquareCacheDao(db: AppDatabase): FoursquareCacheDao = db.foursquareCacheDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
