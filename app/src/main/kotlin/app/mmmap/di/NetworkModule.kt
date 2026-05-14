package app.mmmap.di

import app.mmmap.BuildConfig
import app.mmmap.data.remote.FoursquareApi
import app.mmmap.data.remote.GitHubContentsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    @Provides
    @Singleton
    @Named("foursquare")
    fun provideFoursquareRetrofit(client: OkHttpClient, json: Json): Retrofit =
        retrofit(FoursquareApi.BASE_URL, client, json)

    @Provides
    @Singleton
    @Named("github")
    fun provideGitHubRetrofit(client: OkHttpClient, json: Json): Retrofit =
        retrofit(GitHubContentsApi.BASE_URL, client, json)

    private fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideFoursquareApi(@Named("foursquare") retrofit: Retrofit): FoursquareApi =
        retrofit.create(FoursquareApi::class.java)

    @Provides
    @Singleton
    fun provideGitHubContentsApi(@Named("github") retrofit: Retrofit): GitHubContentsApi =
        retrofit.create(GitHubContentsApi::class.java)
}
