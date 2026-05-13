package app.mmmap.data.repository

import app.mmmap.BuildConfig
import app.mmmap.data.db.dao.FoursquareCacheDao
import app.mmmap.data.db.entities.FoursquareCacheEntity
import app.mmmap.data.remote.FoursquareApi
import app.mmmap.domain.model.FoursquareDetail
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnrichmentRepository @Inject constructor(
    private val api: FoursquareApi,
    private val cacheDao: FoursquareCacheDao,
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

        if (BuildConfig.FSQ_API_KEY.isBlank()) return cached?.toDetail()

        return try {
            val searchResult = api.searchPlaces(
                apiKey = BuildConfig.FSQ_API_KEY,
                latLon = "$latitude,$longitude",
                query = name,
            )
            val best = searchResult.results
                .filter { it.distance != null && it.distance < 200 }
                .minByOrNull { jaroWinklerDistance(name, it.name) }
                ?: return cached?.toDetail()

            val place = api.getPlace(BuildConfig.FSQ_API_KEY, best.fsqId)
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
        val hours = openingHoursJson?.let {
            runCatching {
                val parsed = json.decodeFromString<app.mmmap.data.remote.models.FsqHours>(it)
                parsed.display?.lines()?.toList() ?: emptyList()
            }.getOrDefault(emptyList())
        } ?: emptyList()

        return FoursquareDetail(
            photoUrl = photoUrl,
            openingHours = hours,
            isOpenNow = openingHoursJson?.let {
                runCatching {
                    json.decodeFromString<app.mmmap.data.remote.models.FsqHours>(it).openNow
                }.getOrNull()
            },
            phone = phone,
            rating = rating,
        )
    }

    private fun jaroWinklerDistance(s1: String, s2: String): Double {
        val a = s1.lowercase()
        val b = s2.lowercase()
        if (a == b) return 0.0
        val matchWindow = maxOf(a.length, b.length) / 2 - 1
        if (matchWindow < 0) return 1.0
        val aMatches = BooleanArray(a.length)
        val bMatches = BooleanArray(b.length)
        var matches = 0
        var transpositions = 0
        for (i in a.indices) {
            val start = maxOf(0, i - matchWindow)
            val end = minOf(i + matchWindow + 1, b.length)
            for (j in start until end) {
                if (bMatches[j] || a[i] != b[j]) continue
                aMatches[i] = true; bMatches[j] = true; matches++; break
            }
        }
        if (matches == 0) return 1.0
        var k = 0
        for (i in a.indices) {
            if (!aMatches[i]) continue
            while (!bMatches[k]) k++
            if (a[i] != b[k]) transpositions++
            k++
        }
        val jaro = (matches.toDouble() / a.length + matches.toDouble() / b.length +
                (matches - transpositions / 2.0) / matches) / 3.0
        val prefix = (0 until minOf(4, minOf(a.length, b.length))).takeWhile { a[it] == b[it] }.size
        return 1.0 - (jaro + prefix * 0.1 * (1 - jaro))
    }
}
