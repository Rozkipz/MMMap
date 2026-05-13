package app.mmmap.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonObject
import app.mmmap.domain.model.Distinction
import app.mmmap.ui.detail.RestaurantSheet
import app.mmmap.ui.theme.BibGreen
import app.mmmap.ui.theme.MichelinRed
import app.mmmap.ui.theme.OneStarRed
import app.mmmap.ui.theme.SelectedBlue
import app.mmmap.ui.theme.StarGold
import app.mmmap.ui.theme.TwoStarGold
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val SOURCE_ID      = "restaurants-source"
private const val LAYER_ID       = "restaurants-layer"
private const val USER_SOURCE_ID = "user-location-source"
private const val USER_LAYER_ID  = "user-location-layer"
private const val PROP_RESTAURANT_ID = "id"
private const val PROP_DISTINCTION   = "distinction"

// OpenFreeMap — free, production-grade MapLibre tile service, no API key required
private const val TILE_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private fun Color.toCssHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    bottomPadding: Dp = 0.dp,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val restaurants        by viewModel.restaurants.collectAsState()
    val selectedRestaurant by viewModel.selectedRestaurant.collectAsState()
    val filters            by viewModel.filters.collectAsState()
    val userLatLon         by viewModel.userLatLon.collectAsState()
    val isLocating         by viewModel.isLocating.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val context = LocalContext.current
    val mapHolder = remember { MapHolder() }

    LaunchedEffect(userLatLon) {
        val map = mapHolder.map ?: return@LaunchedEffect
        val loc = userLatLon
        if (loc != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.first, loc.second), 14.0))
        }
        map.style?.getSourceAs<GeoJsonSource>(USER_SOURCE_ID)?.let { src ->
            if (loc != null)
                src.setGeoJson(Feature.fromGeometry(Point.fromLngLat(loc.second, loc.first)))
            else
                src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.locateUser() }
    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) viewModel.locateUser()
        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapHolder.mapView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START   -> mv.onStart()
                Lifecycle.Event.ON_RESUME  -> mv.onResume()
                Lifecycle.Event.ON_PAUSE   -> mv.onPause()
                Lifecycle.Event.ON_STOP    -> mv.onStop()
                Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                MapView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    mapHolder.mapView = this
                    onCreate(null)
                    // Activity is already RESUMED when this factory runs, so call
                    // onStart/onResume immediately. The DisposableEffect handles
                    // future background/foreground transitions.
                    onStart()
                    onResume()
                    getMapAsync { libMap ->
                        mapHolder.map = libMap
                        libMap.setStyle(Style.Builder().fromUri(TILE_STYLE_URL)) { style ->
                            // Restaurant dots — circle colour and size by distinction
                            val restaurantSource = GeoJsonSource(SOURCE_ID)
                            style.addSource(restaurantSource)
                            style.addLayer(
                                CircleLayer(LAYER_ID, SOURCE_ID).withProperties(
                                    PropertyFactory.circleColor(
                                        Expression.match(
                                            Expression.get(PROP_DISTINCTION),
                                            Expression.literal(SelectedBlue.toCssHex()),
                                            Expression.stop("3 Stars",      StarGold.toCssHex()),
                                            Expression.stop("2 Stars",      TwoStarGold.toCssHex()),
                                            Expression.stop("1 Star",       OneStarRed.toCssHex()),
                                            Expression.stop("Bib Gourmand", BibGreen.toCssHex()),
                                        )
                                    ),
                                    PropertyFactory.circleRadius(
                                        Expression.match(
                                            Expression.get(PROP_DISTINCTION),
                                            Expression.literal(9f),
                                            Expression.stop("3 Stars",      14f),
                                            Expression.stop("2 Stars",      13f),
                                            Expression.stop("1 Star",       12f),
                                            Expression.stop("Bib Gourmand", 10f),
                                        )
                                    ),
                                    PropertyFactory.circleStrokeWidth(1.5f),
                                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                                    PropertyFactory.circleOpacity(0.9f),
                                )
                            )

                            // User location dot — red circle with white stroke
                            style.addSource(GeoJsonSource(USER_SOURCE_ID))
                            style.addLayer(
                                CircleLayer(USER_LAYER_ID, USER_SOURCE_ID).withProperties(
                                    PropertyFactory.circleRadius(8f),
                                    PropertyFactory.circleColor(MichelinRed.toCssHex()),
                                    PropertyFactory.circleStrokeWidth(2.5f),
                                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                                    PropertyFactory.circleOpacity(1f),
                                )
                            )

                            // Flush any restaurant data that arrived before the style loaded
                            mapHolder.pendingRestaurantCollection?.let {
                                restaurantSource.setGeoJson(it)
                                mapHolder.pendingRestaurantCollection = null
                            }
                        }
                        libMap.addOnCameraIdleListener {
                            val region = libMap.projection.visibleRegion
                            val sw = region.nearLeft  ?: return@addOnCameraIdleListener
                            val ne = region.farRight  ?: return@addOnCameraIdleListener
                            viewModel.updateBounds(
                                MapBounds(
                                    minLat = sw.latitude,
                                    maxLat = ne.latitude,
                                    minLon = sw.longitude,
                                    maxLon = ne.longitude,
                                )
                            )
                        }
                        libMap.addOnMapClickListener { latLng ->
                            val screenPoint = libMap.projection.toScreenLocation(latLng)
                            val features = libMap.queryRenderedFeatures(screenPoint, LAYER_ID)
                            val id = features.firstOrNull()?.getStringProperty(PROP_RESTAURANT_ID)
                            val hit = restaurants.find { it.id == id }
                            viewModel.selectRestaurant(hit)
                            hit != null
                        }
                        libMap.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(51.5074, -0.1278))
                            .zoom(10.0)
                            .build()
                    }
                }
            },
            update = { _ ->
                val features = restaurants.map { r ->
                    val props = JsonObject().apply {
                        addProperty(PROP_RESTAURANT_ID, r.id)
                        addProperty(PROP_DISTINCTION, r.distinction.label)
                    }
                    Feature.fromGeometry(Point.fromLngLat(r.longitude, r.latitude), props)
                }
                val collection = FeatureCollection.fromFeatures(features)
                val source = mapHolder.map?.style?.getSourceAs<GeoJsonSource>(SOURCE_ID)
                if (source != null) {
                    source.setGeoJson(collection)
                } else {
                    // Style not ready yet — stash for the style-loaded callback to apply
                    mapHolder.pendingRestaurantCollection = collection
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Filter bar — frosted surface card floating over the map (Google Maps style)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.statusBarsPadding(),
            ) {
                items(Distinction.entries) { d ->
                    FilterChip(
                        selected = filters.distinction == d,
                        onClick = {
                            viewModel.updateFilters(
                                filters.copy(distinction = if (filters.distinction == d) null else d)
                            )
                        },
                        label = { Text(d.chipLabel()) },
                    )
                }
            }
        }

        // FAB — centres map on user location and shows a red dot
        FloatingActionButton(
            onClick = { if (!isLocating) viewModel.locateUser() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPadding + 16.dp, end = 16.dp),
        ) {
            if (isLocating) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(12.dp),
                    strokeWidth = 2.5.dp,
                )
            } else {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location")
            }
        }
    }

    if (selectedRestaurant != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectRestaurant(null) },
            sheetState = sheetState,
        ) {
            RestaurantSheet(
                restaurant = selectedRestaurant!!,
                onDismiss = {
                    scope.launch { sheetState.hide() }
                        .invokeOnCompletion { viewModel.selectRestaurant(null) }
                },
            )
        }
    }
}

/** Plain holder — not MutableState, so updating it doesn't trigger recomposition. */
private class MapHolder {
    var mapView: MapView? = null
    var map: MapLibreMap? = null
    var pendingRestaurantCollection: FeatureCollection? = null
}

private fun Distinction.chipLabel() = when (this) {
    Distinction.THREE_STAR   -> "3★"
    Distinction.TWO_STAR     -> "2★"
    Distinction.ONE_STAR     -> "1★"
    Distinction.BIB_GOURMAND -> "Bib"
    Distinction.SELECTED     -> "Selected"
}
