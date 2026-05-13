package app.mmmap.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mmmap.data.repository.EnrichmentRepository
import app.mmmap.domain.model.FoursquareDetail
import app.mmmap.domain.model.Restaurant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val enrichmentRepo: EnrichmentRepository,
) : ViewModel() {

    private val _enrichment = MutableStateFlow<FoursquareDetail?>(null)
    val enrichment: StateFlow<FoursquareDetail?> = _enrichment

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun loadEnrichment(restaurant: Restaurant) {
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
}
