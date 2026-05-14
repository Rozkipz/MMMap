package app.mmmap.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mmmap.data.repository.EnrichmentRepository
import app.mmmap.data.repository.VisitedRepository
import app.mmmap.domain.model.FoursquareDetail
import app.mmmap.domain.model.Restaurant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val enrichmentRepo: EnrichmentRepository,
    private val visitedRepo: VisitedRepository,
) : ViewModel() {

    private val _enrichment = MutableStateFlow<FoursquareDetail?>(null)
    val enrichment: StateFlow<FoursquareDetail?> = _enrichment

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _currentId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val isVisited: StateFlow<Boolean> = _currentId
        .filterNotNull()
        .flatMapLatest { visitedRepo.observeIsVisited(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun loadEnrichment(restaurant: Restaurant) {
        _currentId.value = restaurant.id
        viewModelScope.launch {
            _loading.value = true
            _enrichment.value = enrichmentRepo.get(
                restaurantId = restaurant.id,
                name = restaurant.name,
                latitude = restaurant.latitude,
                longitude = restaurant.longitude,
            )
            _loading.value = false
        }
    }

    fun setVisited(restaurant: Restaurant, visited: Boolean) {
        viewModelScope.launch { visitedRepo.setVisited(restaurant, visited) }
    }
}
