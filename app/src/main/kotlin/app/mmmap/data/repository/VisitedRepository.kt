package app.mmmap.data.repository

import app.mmmap.data.db.dao.VisitedDao
import app.mmmap.data.db.entities.VisitedRestaurantEntity
import app.mmmap.domain.model.Restaurant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisitedRepository @Inject constructor(private val dao: VisitedDao) {

    val visitedIds: Flow<Set<String>> get() = dao.observeAllIds().map { it.toSet() }

    fun observeIsVisited(id: String): Flow<Boolean> = dao.observeIsVisited(id)

    suspend fun setVisited(restaurant: Restaurant, visited: Boolean) {
        setVisited(
            id        = restaurant.id,
            name      = restaurant.name,
            latitude  = restaurant.latitude,
            longitude = restaurant.longitude,
            award     = restaurant.distinction.label,
            cuisine   = restaurant.cuisine,
            visited   = visited,
        )
    }

    suspend fun setVisited(
        id: String,
        name: String,
        latitude: Double,
        longitude: Double,
        award: String? = null,
        cuisine: String? = null,
        visited: Boolean,
    ) {
        if (visited) {
            dao.insert(
                VisitedRestaurantEntity(
                    restaurantId = id,
                    name         = name,
                    latitude     = latitude,
                    longitude    = longitude,
                    award        = award,
                    cuisine      = cuisine,
                    visitedAt    = System.currentTimeMillis(),
                )
            )
        } else {
            dao.delete(id)
        }
    }

    suspend fun getAll(): List<VisitedRestaurantEntity> = dao.getAll()

    suspend fun importAll(entities: List<VisitedRestaurantEntity>) = dao.insertAll(entities)

    suspend fun count(): Int = dao.count()
}
