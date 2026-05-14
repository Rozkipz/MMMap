package app.mmmap.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import app.mmmap.BuildConfig
import app.mmmap.data.prefs.ApiKeyPreferences
import app.mmmap.data.repository.RestaurantRepository
import app.mmmap.data.sync.DatasetSyncWorker
import app.mmmap.data.sync.SyncPreferences
import app.mmmap.domain.model.Distinction
import app.mmmap.domain.model.Restaurant
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val LOCATE_TIMEOUT_MS = 60_000L

data class MapBounds(
    val minLat: Double, val maxLat: Double,
    val minLon: Double, val maxLon: Double,
)

data class MapFilters(
    val distinction: Distinction? = null,
    val cuisine: String? = null,
    val price: String? = null,
)

data class DebugState(
    val dbRestaurantCount: Int,
    val viewportCount: Int,
    val lastSyncAt: Long?,
    val lastCsvSha: String?,
    val workerState: String,
    val bounds: MapBounds?,
    val filters: MapFilters,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: RestaurantRepository,
    private val apiKeyPrefs: ApiKeyPreferences,
    private val syncPrefs: SyncPreferences,
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

    // Effective Foursquare API key: DataStore override → build-time key from local.properties
    val foursquareKey: StateFlow<String> = apiKeyPrefs.fsqApiKey
        .map { stored -> stored?.takeIf { it.isNotBlank() } ?: BuildConfig.FSQ_API_KEY }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuildConfig.FSQ_API_KEY)

    fun saveFoursquareKey(key: String) {
        viewModelScope.launch { apiKeyPrefs.setFsqApiKey(key) }
    }

    val debugState = MutableStateFlow<DebugState?>(null)

    fun loadDebugInfo() {
        viewModelScope.launch {
            val workInfos = withContext(kotlinx.coroutines.Dispatchers.IO) {
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(DatasetSyncWorker.TAG)
                    .get()
            }
            debugState.value = DebugState(
                dbRestaurantCount = repo.count(),
                viewportCount     = restaurants.value.size,
                lastSyncAt        = syncPrefs.lastSyncAt(),
                lastCsvSha        = syncPrefs.lastCsvSha()?.take(8),
                workerState       = workInfos.firstOrNull()?.state?.name ?: "none",
                bounds            = bounds.value,
                filters           = filters.value,
            )
        }
    }

    private val _userLatLon = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLatLon: StateFlow<Pair<Double, Double>?> = _userLatLon

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating

    private var pendingLocationListener: LocationListener? = null
    private var pendingCancellationSignal: CancellationSignal? = null
    private var locateTimeoutJob: Job? = null
    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    init {
        viewModelScope.launch {
            availableCuisines.value = repo.distinctCuisines()
            availablePrices.value = repo.distinctPrices()
        }
    }

    // lat, lon, zoom — persists across tab switches so the map can restore its position
    var lastCameraPosition: Triple<Double, Double, Double>? = null
        private set

    // True after the first automatic zoom to GPS; prevents re-zooming on tab switch
    var hasZoomedToUserOnce = false

    fun saveLastCamera(lat: Double, lon: Double, zoom: Double) {
        lastCameraPosition = Triple(lat, lon, zoom)
    }

    fun selectRestaurant(restaurant: Restaurant?) { selectedRestaurant.value = restaurant }
    fun updateBounds(b: MapBounds) { bounds.value = b }
    fun updateFilters(f: MapFilters) { filters.value = f }

    @SuppressLint("MissingPermission")
    fun locateUser() {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null) _userLatLon.value = last.latitude to last.longitude

        pendingLocationListener?.let { locationManager.removeUpdates(it) }
        pendingCancellationSignal?.cancel()
        pendingCancellationSignal = null

        val enabledProviders = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { locationManager.isProviderEnabled(it) }.getOrElse { false } }
        if (enabledProviders.isEmpty()) return

        // Only show spinner when we have no position yet; a cached fix is enough to hide it
        if (last == null) _isLocating.value = true
        locateTimeoutJob?.cancel()
        locateTimeoutJob = viewModelScope.launch {
            delay(LOCATE_TIMEOUT_MS)
            _isLocating.value = false
            pendingCancellationSignal?.cancel()
            pendingCancellationSignal = null
            pendingLocationListener?.let { locationManager.removeUpdates(it) }
            pendingLocationListener = null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = if (hasFine && LocationManager.GPS_PROVIDER in enabledProviders)
                LocationManager.GPS_PROVIDER
            else
                enabledProviders.first()
            val signal = CancellationSignal()
            pendingCancellationSignal = signal
            locationManager.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                locateTimeoutJob?.cancel()
                _isLocating.value = false
                pendingCancellationSignal = null
                if (loc != null) _userLatLon.value = loc.latitude to loc.longitude
            }
        } else {
            var fired = false
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    if (fired) return
                    fired = true
                    locateTimeoutJob?.cancel()
                    _isLocating.value = false
                    _userLatLon.value = loc.latitude to loc.longitude
                    locationManager.removeUpdates(this)
                    pendingLocationListener = null
                }
                override fun onProviderDisabled(p: String) {}
                override fun onProviderEnabled(p: String) {}
            }
            pendingLocationListener = listener
            enabledProviders.forEach { provider ->
                runCatching {
                    locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locateTimeoutJob?.cancel()
        pendingCancellationSignal?.cancel()
        pendingLocationListener?.let { locationManager.removeUpdates(it) }
    }
}
