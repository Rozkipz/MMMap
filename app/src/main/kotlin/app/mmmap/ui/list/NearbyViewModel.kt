package app.mmmap.ui.list

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mmmap.data.repository.RestaurantRepository
import app.mmmap.domain.model.Restaurant
import app.mmmap.util.haversineKm
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NearbyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: RestaurantRepository,
) : ViewModel() {

    private val _nearby = MutableStateFlow<List<Pair<Restaurant, Float>>>(emptyList())
    val nearby: StateFlow<List<Pair<Restaurant, Float>>> = _nearby

    private val _locationMissing = MutableStateFlow(false)
    val locationMissing: StateFlow<Boolean> = _locationMissing

    fun load() {
        viewModelScope.launch {
            val location = bestLastLocation() ?: run { _locationMissing.value = true; return@launch }
            val lat = location.latitude
            val lon = location.longitude
            val delta = 0.5
            repo.observeInBounds(
                minLat = lat - delta, maxLat = lat + delta,
                minLon = lon - delta, maxLon = lon + delta,
            ).collect { restaurants ->
                _nearby.value = restaurants
                    .map { it to haversineKm(lat, lon, it.latitude, it.longitude) }
                    .sortedBy { it.second }
                    .take(50)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastLocation(): Location? {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

}
