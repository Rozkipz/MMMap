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
        cuisinesAll = if (cuisines == null) 1 else 0,
        cuisines    = cuisines?.toList().orEmpty().ifEmpty { listOf("") },
        tiersAll    = if (priceTiers == null) 1 else 0,
        priceTiers  = priceTiers?.toList().orEmpty().ifEmpty { listOf(0) },
    ).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Restaurant? = dao.getById(id)?.toDomain()

    suspend fun count(): Int = dao.count()

    suspend fun distinctCuisines(): List<String> = dao.distinctCuisines()

    suspend fun distinctPrices(): List<String> = dao.distinctPrices()
}
