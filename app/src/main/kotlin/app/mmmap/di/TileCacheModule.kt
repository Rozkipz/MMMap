package app.mmmap.di

import app.mmmap.map.AmbientCacheSource
import app.mmmap.map.MapLibreAmbientCacheSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TileCacheModule {

    @Binds
    @Singleton
    abstract fun bindAmbientCacheSource(impl: MapLibreAmbientCacheSource): AmbientCacheSource
}
