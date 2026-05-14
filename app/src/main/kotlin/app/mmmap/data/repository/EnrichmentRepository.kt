package app.mmmap.data.repository

import app.mmmap.BuildConfig
import app.mmmap.data.db.dao.FoursquareCacheDao
import app.mmmap.data.db.entities.FoursquareCacheEntity
import app.mmmap.data.prefs.ApiKeyPreferences
import app.mmmap.data.remote.FoursquareApi
import app.mmmap.data.remote.models.FsqHours
import app.mmmap.domain.model.FoursquareDetail
import app.mmmap.util.jaroWinklerDistance
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnrichmentRepository @Inject constructor(
    private val api: FoursquareApi,
    private val cacheDao: FoursquareCacheDao,
    private val apiKeyPrefs: ApiKeyPreferences,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(
        restaurantId: String,
        name: String,
        latitude: Double,
        longitude: Double,
    ): FoursquareDetail? {
        val cached = cacheDao.get(restaurantId)
        val now = System.currentTimeMillis()
        if (cached != null && cached.isPhotoFresh(now) && cached.isHoursFresh(now)) {
            return cached.toDetail()
        }

        val apiKey = apiKeyPrefs.fsqApiKey.first()
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.FSQ_API_KEY.takeIf { it.isNotBlank() }
            ?: return cached?.toDetail()

        return try {
            val searchResult = api.searchPlaces(
                apiKey = apiKey,
                latLon = "$latitude,$longitude",
                query = name,
            )
            val best = searchResult.results
                .filter { it.distance != null && it.distance < 200 }
                .minByOrNull { jaroWinklerDistance(name, it.name) }
                ?: return cached?.toDetail()

            val place = api.getPlace(apiKey, best.fsqId)
            val hoursJson = place.hours?.let { json.encodeToString(it) }
            val entity = FoursquareCacheEntity(
                restaurantId = restaurantId,
                fsqId = best.fsqId,
                photoUrl = place.photos?.firstOrNull()?.url(),
                openingHoursJson = hoursJson,
                phone = place.tel,
                rating = place.rating,
                fetchedAt = now,
            )
            cacheDao.upsert(entity)
            entity.toDetail()
        } catch (e: Exception) {
            cached?.toDetail()
        }
    }

    private fun FoursquareCacheEntity.toDetail(): FoursquareDetail {
        val hours: FsqHours? = openingHoursJson?.let {
            runCatching { json.decodeFromString<FsqHours>(it) }.getOrNull()
        }
        return FoursquareDetail(
            photoUrl = photoUrl,
            openingHours = hours?.display?.lines()?.toList() ?: emptyList(),
            isOpenNow = hours?.openNow,
            phone = phone,
            rating = rating,
        )
    }

}
