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
        if (visited) {
            dao.insert(
                VisitedRestaurantEntity(
                    restaurantId = restaurant.id,
                    name         = restaurant.name,
                    latitude     = restaurant.latitude,
                    longitude    = restaurant.longitude,
                    award        = restaurant.distinction.label,
                    cuisine      = restaurant.cuisine,
                    visitedAt    = System.currentTimeMillis(),
                )
            )
        } else {
            dao.delete(restaurant.id)
        }
    }

    suspend fun count(): Int = dao.count()
}
