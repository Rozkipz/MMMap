package app.mmmap.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mmmap.data.repository.VisitedRepository
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
    private val visitedRepo: VisitedRepository,
) : ViewModel() {

    private val _currentId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val isVisited: StateFlow<Boolean> = _currentId
        .filterNotNull()
        .flatMapLatest { visitedRepo.observeIsVisited(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun loadEnrichment(restaurant: Restaurant) {
        _currentId.value = restaurant.id
    }

    fun setVisited(restaurant: Restaurant, visited: Boolean) {
        viewModelScope.launch { visitedRepo.setVisited(restaurant, visited) }
    }
}
