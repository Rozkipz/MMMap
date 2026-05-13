package app.mmmap.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mmmap.data.repository.RestaurantRepository
import app.mmmap.domain.model.Distinction
import app.mmmap.domain.model.Restaurant
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapBounds(
    val minLat: Double, val maxLat: Double,
    val minLon: Double, val maxLon: Double,
)

data class MapFilters(
    val distinction: Distinction? = null,
    val cuisine: String? = null,
    val price: String? = null,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: RestaurantRepository,
) : ViewModel() {

    val bounds = MutableStateFlow<MapBounds?>(null)
    val filters = MutableStateFlow(MapFilters())

    val availableCuisines = MutableStateFlow<List<String>>(emptyList())
    val availablePrices = MutableStateFlow<List<String>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val restaurants: StateFlow<List<Restaurant>> = combine(bounds, filters) { b, f -> b to f }
        .flatMapLatest { (b, f) ->
            if (b == null) return@flatMapLatest kotlinx.coroutines.flow.flowOf(emptyList())
            repo.observeInBounds(
                minLat = b.minLat, maxLat = b.maxLat,
                minLon = b.minLon, maxLon = b.maxLon,
                award = f.distinction?.label,
                cuisine = f.cuisine,
                price = f.price,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedRestaurant = MutableStateFlow<Restaurant?>(null)

    private val _userLatLon = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLatLon: StateFlow<Pair<Double, Double>?> = _userLatLon

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating

    private var pendingLocationListener: LocationListener? = null
    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    init {
        viewModelScope.launch {
            availableCuisines.value = repo.distinctCuisines()
            availablePrices.value = repo.distinctPrices()
        }
    }

    fun selectRestaurant(restaurant: Restaurant?) { selectedRestaurant.value = restaurant }
    fun updateBounds(b: MapBounds) { bounds.value = b }
    fun updateFilters(f: MapFilters) { filters.value = f }

    @SuppressLint("MissingPermission")
    fun locateUser() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return

        // Show last known location immediately for instant feedback
        val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null) _userLatLon.value = last.latitude to last.longitude

        // Cancel any pending request then ask for a fresh fix
        pendingLocationListener?.let { locationManager.removeUpdates(it) }
        val enabledProviders = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { locationManager.isProviderEnabled(it) }
        if (enabledProviders.isEmpty()) return

        _isLocating.value = true
        var fired = false
        val wrappedListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (fired) return
                fired = true
                _isLocating.value = false
                _userLatLon.value = loc.latitude to loc.longitude
                locationManager.removeUpdates(this)
                pendingLocationListener = null
            }
            override fun onProviderDisabled(p: String) {}
            override fun onProviderEnabled(p: String) {}
        }
        pendingLocationListener = wrappedListener
        enabledProviders.forEach { provider ->
            @Suppress("DEPRECATION")
            locationManager.requestSingleUpdate(provider, wrappedListener, Looper.getMainLooper())
        }
    }

    override fun onCleared() {
        super.onCleared()
        pendingLocationListener?.let { locationManager.removeUpdates(it) }
    }
}
