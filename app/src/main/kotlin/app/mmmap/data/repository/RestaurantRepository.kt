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
        cuisine: String? = null,
        price: String? = null,
    ): Flow<List<Restaurant>> = dao
        .observeInBounds(minLat, maxLat, minLon, maxLon, award, cuisine, price)
        .map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Restaurant? = dao.getById(id)?.toDomain()

    suspend fun count(): Int = dao.count()

    suspend fun distinctCuisines(): List<String> = dao.distinctCuisines()

    suspend fun distinctPrices(): List<String> = dao.distinctPrices()
}
