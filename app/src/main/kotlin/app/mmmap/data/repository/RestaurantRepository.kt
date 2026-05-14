package app.mmmap.data.repository

import app.mmmap.data.db.dao.RestaurantDao
import app.mmmap.domain.model.Restaurant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestaurantRepository @Inject constructor(
    private val dao: RestaurantDao,
) {
    fun observeInBounds(
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double,
        award: String? = null,
        cuisines: Set<String>? = null,
        priceTiers: Set<Int>? = null,
    ): Flow<List<Restaurant>> = dao.observeInBounds(
        minLat = minLat, maxLat = maxLat,
        minLon = minLon, maxLon = maxLon,
        award = award,
        tiersAll   = if (priceTiers == null) 1 else 0,
        priceTiers = priceTiers?.toList().orEmpty().ifEmpty { listOf(0) },
    ).map { list ->
        list.map { it.toDomain() }.filterByCuisines(cuisines)
    }

    suspend fun getById(id: String): Restaurant? = dao.getById(id)?.toDomain()

    suspend fun count(): Int = dao.count()

    suspend fun distinctCuisines(): List<String> = dao.distinctCuisines()
        .flatMap { raw -> raw.split(",").map { it.trim() }.filter { it.isNotEmpty() } }
        .toSortedSet()
        .toList()

    suspend fun distinctPrices(): List<String> = dao.distinctPrices()
}

private fun List<Restaurant>.filterByCuisines(cuisines: Set<String>?): List<Restaurant> {
    if (cuisines == null) return this
    if (cuisines.isEmpty()) return emptyList()
    return filter { r ->
        r.cuisine?.split(",")?.any { it.trim() in cuisines } ?: false
    }
}
