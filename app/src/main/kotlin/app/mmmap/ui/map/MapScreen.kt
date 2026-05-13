package app.mmmap.ui.map

import android.Manifest
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonObject
import app.mmmap.domain.model.Distinction
import app.mmmap.ui.detail.RestaurantSheet
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onNavigateToNearby: () -> Unit = {},
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

    val mapHolder = remember { MapHolder() }

    // Animate camera to user's location whenever it changes
    LaunchedEffect(userLatLon) {
        val loc = userLatLon ?: return@LaunchedEffect
        mapHolder.map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(loc.first, loc.second), 14.0)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* map works with or without; NearbyScreen handles "no permission" gracefully */ }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
                            // Restaurant pins (SymbolLayer — icons added in a future step)
                            style.addSource(GeoJsonSource(SOURCE_ID))
                            style.addLayer(
                                SymbolLayer(LAYER_ID, SOURCE_ID).withProperties(
                                    PropertyFactory.iconImage(
                                        org.maplibre.android.style.expressions.Expression.match(
                                            org.maplibre.android.style.expressions.Expression.get(PROP_DISTINCTION),
                                            org.maplibre.android.style.expressions.Expression.literal("marker_selected"),
                                            org.maplibre.android.style.expressions.Expression.stop("3 Stars",      "marker_3star"),
                                            org.maplibre.android.style.expressions.Expression.stop("2 Stars",      "marker_2star"),
                                            org.maplibre.android.style.expressions.Expression.stop("1 Star",       "marker_1star"),
                                            org.maplibre.android.style.expressions.Expression.stop("Bib Gourmand", "marker_bib"),
                                        )
                                    ),
                                    PropertyFactory.iconAllowOverlap(false),
                                    PropertyFactory.iconSize(0.8f),
                                )
                            )

                            // User location dot — red circle with white stroke
                            style.addSource(GeoJsonSource(USER_SOURCE_ID))
                            style.addLayer(
                                CircleLayer(USER_LAYER_ID, USER_SOURCE_ID).withProperties(
                                    PropertyFactory.circleRadius(8f),
                                    PropertyFactory.circleColor("#D32F2F"),
                                    PropertyFactory.circleStrokeWidth(2.5f),
                                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                                    PropertyFactory.circleOpacity(1f),
                                )
                            )
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
                val style  = mapHolder.map?.style ?: return@AndroidView
                val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return@AndroidView

                // Update restaurant pins
                val features = restaurants.map { r ->
                    val props = JsonObject().apply {
                        addProperty(PROP_RESTAURANT_ID, r.id)
                        addProperty(PROP_DISTINCTION, r.distinction.label)
                    }
                    Feature.fromGeometry(Point.fromLngLat(r.longitude, r.latitude), props)
                }
                source.setGeoJson(FeatureCollection.fromFeatures(features))

                // Update user dot
                val userSource = style.getSourceAs<GeoJsonSource>(USER_SOURCE_ID)
                val loc = userLatLon
                if (userSource != null) {
                    if (loc != null) {
                        userSource.setGeoJson(
                            Feature.fromGeometry(Point.fromLngLat(loc.second, loc.first))
                        )
                    } else {
                        userSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Filter chips — padded below the status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 8.dp, end = 8.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            Distinction.entries.forEach { d ->
                FilterChip(
                    selected = filters.distinction == d,
                    onClick = {
                        viewModel.updateFilters(
                            filters.copy(distinction = if (filters.distinction == d) null else d)
                        )
                    },
                    label = { Text(d.chipLabel()) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }

        // FAB — centres map on user location and shows a red dot
        FloatingActionButton(
            onClick = { if (!isLocating) viewModel.locateUser() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
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
}

private fun Distinction.chipLabel() = when (this) {
    Distinction.THREE_STAR   -> "3★"
    Distinction.TWO_STAR     -> "2★"
    Distinction.ONE_STAR     -> "1★"
    Distinction.BIB_GOURMAND -> "Bib"
    Distinction.SELECTED     -> "Selected"
}
