package app.mmmap.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import app.mmmap.ThemeMode
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import app.mmmap.ui.about.AboutDialog
import app.mmmap.ui.debug.DebugInfoDialog
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
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
import app.mmmap.domain.model.Restaurant
import app.mmmap.ui.detail.RestaurantSheet
import app.mmmap.ui.dotColor
import app.mmmap.ui.settings.CacheSettingsDialog
import app.mmmap.ui.theme.StarGold
import app.mmmap.ui.settings.FoursquareKeyDialog
import app.mmmap.ui.theme.UserGrey
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
private const val VISITED_GLOW_ID = "restaurants-visited-glow"
private const val USER_SOURCE_ID  = "user-location-source"
private const val USER_GLOW_ID    = "user-location-glow"
private const val USER_LAYER_ID   = "user-location-layer"
private const val PROP_RESTAURANT_ID = "id"
private const val PROP_DISTINCTION   = "distinction"
private const val PROP_VISITED       = "visited"

// OpenFreeMap light style; CARTO Dark Matter for dark mode (both free, no API key)
private const val TILE_STYLE_URL      = "https://tiles.openfreemap.org/styles/liberty"
private const val TILE_STYLE_DARK_URL = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

private fun Color.toCssHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

private fun buildCollection(restaurants: List<Restaurant>, visitedIds: Set<String>): FeatureCollection {
    val features = restaurants.map { r ->
        val props = JsonObject().apply {
            addProperty(PROP_RESTAURANT_ID, r.id)
            addProperty(PROP_DISTINCTION, r.distinction.label)
            addProperty(PROP_VISITED, r.id in visitedIds)
        }
        Feature.fromGeometry(Point.fromLngLat(r.longitude, r.latitude), props)
    }
    return FeatureCollection.fromFeatures(features)
}

private fun addCustomLayers(style: Style, mapHolder: MapHolder) {
    val restaurantSource = GeoJsonSource(SOURCE_ID)
    style.addSource(restaurantSource)
    val visitedGlow = CircleLayer(VISITED_GLOW_ID, SOURCE_ID).withProperties(
        PropertyFactory.circleRadius(26f),
        PropertyFactory.circleColor(StarGold.toCssHex()),
        PropertyFactory.circleBlur(0.9f),
        PropertyFactory.circleOpacity(0.55f),
    )
    visitedGlow.setFilter(Expression.eq(Expression.get(PROP_VISITED), Expression.literal(true)))
    style.addLayer(visitedGlow)
    style.addLayer(
        CircleLayer(LAYER_ID, SOURCE_ID).withProperties(
            PropertyFactory.circleColor(
                Expression.match(
                    Expression.get(PROP_DISTINCTION),
                    Expression.literal(Distinction.SELECTED.dotColor().toCssHex()),
                    Expression.stop(Distinction.THREE_STAR.label,   Distinction.THREE_STAR.dotColor().toCssHex()),
                    Expression.stop(Distinction.TWO_STAR.label,     Distinction.TWO_STAR.dotColor().toCssHex()),
                    Expression.stop(Distinction.ONE_STAR.label,     Distinction.ONE_STAR.dotColor().toCssHex()),
                    Expression.stop(Distinction.BIB_GOURMAND.label, Distinction.BIB_GOURMAND.dotColor().toCssHex()),
                )
            ),
            PropertyFactory.circleRadius(
                Expression.match(
                    Expression.get(PROP_DISTINCTION),
                    Expression.literal(9f),
                    Expression.stop(Distinction.THREE_STAR.label,   14f),
                    Expression.stop(Distinction.TWO_STAR.label,     13f),
                    Expression.stop(Distinction.ONE_STAR.label,     12f),
                    Expression.stop(Distinction.BIB_GOURMAND.label, 10f),
                )
            ),
            PropertyFactory.circleStrokeWidth(
                Expression.switchCase(
                    Expression.eq(Expression.get(PROP_VISITED), Expression.literal(true)),
                    Expression.literal(2.5f),
                    Expression.literal(1.5f),
                )
            ),
            PropertyFactory.circleStrokeColor(
                Expression.switchCase(
                    Expression.eq(Expression.get(PROP_VISITED), Expression.literal(true)),
                    Expression.literal(StarGold.toCssHex()),
                    Expression.literal("#FFFFFF"),
                )
            ),
            PropertyFactory.circleOpacity(0.9f),
        )
    )
    val userSource = GeoJsonSource(USER_SOURCE_ID)
    style.addSource(userSource)
    // Soft glow halo — blurred large circle behind the crisp dot
    style.addLayer(
        CircleLayer(USER_GLOW_ID, USER_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(32f),
            PropertyFactory.circleColor(UserGrey.toCssHex()),
            PropertyFactory.circleBlur(1f),
            PropertyFactory.circleOpacity(0.55f),
        )
    )
    // Crisp inner dot
    style.addLayer(
        CircleLayer(USER_LAYER_ID, USER_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(UserGrey.toCssHex()),
            PropertyFactory.circleStrokeWidth(2.5f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleOpacity(1f),
        )
    )
    mapHolder.pendingRestaurantCollection?.let {
        restaurantSource.setGeoJson(it)
        mapHolder.pendingRestaurantCollection = null
    }
    mapHolder.pendingUserLocation?.let { (lat, lon) ->
        userSource.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
        mapHolder.pendingUserLocation = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    bottomPadding: Dp = 0.dp,
    isDarkTheme: Boolean = false,
    themeMode: ThemeMode = ThemeMode.AUTO,
    onCycleTheme: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel(),
) {
    val restaurants        by viewModel.restaurants.collectAsState()
    val selectedRestaurant by viewModel.selectedRestaurant.collectAsState()
    val filters            by viewModel.filters.collectAsState()
    val availableCuisines  by viewModel.availableCuisines.collectAsState()
    val visitedIds         by viewModel.visitedRestaurantIds.collectAsState()
    val userLatLon         by viewModel.userLatLon.collectAsState()
    val isLocating         by viewModel.isLocating.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val foursquareKey     by viewModel.foursquareKey.collectAsState()
    val cacheSizeMb       by viewModel.cacheSizeMb.collectAsState()
    val debugState        by viewModel.debugState.collectAsState()
    var menuExpanded      by remember { mutableStateOf(false) }
    var showAbout         by remember { mutableStateOf(false) }
    var showKeyDialog     by remember { mutableStateOf(false) }
    var showCacheDialog   by remember { mutableStateOf(false) }
    var showDebug         by remember { mutableStateOf(false) }
    var showFiltersSheet  by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val importExportMessage by viewModel.importExportMessage.collectAsState()
    LaunchedEffect(importExportMessage) {
        val msg = importExportMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearImportExportMessage()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) viewModel.exportVisited(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importVisited(uri) }

    val context = LocalContext.current
    val mapHolder = remember { MapHolder() }

    LaunchedEffect(userLatLon) {
        val loc = userLatLon ?: return@LaunchedEffect
        // Update the dot
        val source = mapHolder.map?.style?.getSourceAs<GeoJsonSource>(USER_SOURCE_ID)
        if (source != null) {
            source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(loc.second, loc.first)))
        } else {
            mapHolder.pendingUserLocation = loc
        }
        // Auto-zoom only on the very first GPS fix after launch
        if (!viewModel.hasZoomedToUserOnce) {
            viewModel.hasZoomedToUserOnce = true
            val map = mapHolder.map
            if (map != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.first, loc.second), 14.0))
            } else {
                mapHolder.pendingCameraTarget = loc
            }
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

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

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
                        val initialUrl = if (isDarkTheme) TILE_STYLE_DARK_URL else TILE_STYLE_URL
                        mapHolder.appliedStyleUrl = initialUrl
                        libMap.setStyle(Style.Builder().fromUri(initialUrl)) { style ->
                            addCustomLayers(style, mapHolder)
                        }
                        libMap.addOnCameraIdleListener {
                            val pos = libMap.cameraPosition
                            val t = pos.target ?: return@addOnCameraIdleListener
                            viewModel.saveLastCamera(t.latitude, t.longitude, pos.zoom)
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
                        val savedCamera = viewModel.lastCameraPosition
                        val pendingGps = mapHolder.pendingCameraTarget
                        when {
                            savedCamera != null -> libMap.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(savedCamera.first, savedCamera.second))
                                .zoom(savedCamera.third)
                                .build()
                            pendingGps != null -> {
                                libMap.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(pendingGps.first, pendingGps.second))
                                    .zoom(14.0)
                                    .build()
                                mapHolder.pendingCameraTarget = null
                            }
                            else -> libMap.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(51.5074, -0.1278))
                                .zoom(10.0)
                                .build()
                        }
                    }
                }
            },
            update = { _ ->
                val collection = buildCollection(restaurants, visitedIds)
                val map = mapHolder.map
                if (map == null) {
                    // Map not ready yet — stash so getMapAsync callback can apply it
                    mapHolder.pendingRestaurantCollection = collection
                    return@AndroidView
                }

                val expectedUrl = if (isDarkTheme) TILE_STYLE_DARK_URL else TILE_STYLE_URL
                if (mapHolder.appliedStyleUrl != expectedUrl) {
                    // Theme changed — stash data so the reload callback can reapply it
                    mapHolder.pendingRestaurantCollection = collection
                    mapHolder.pendingUserLocation = userLatLon
                    mapHolder.appliedStyleUrl = expectedUrl
                    map.setStyle(Style.Builder().fromUri(expectedUrl)) { style ->
                        addCustomLayers(style, mapHolder)
                    }
                    return@AndroidView
                }

                val source = map.style?.getSourceAs<GeoJsonSource>(SOURCE_ID)
                if (source != null) source.setGeoJson(collection)
                else mapHolder.pendingRestaurantCollection = collection
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top action bar — frosted surface floating over the map
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val anyFilterActive = filters.distinctions != null ||
                        filters.cuisines != null || filters.priceTiers != null ||
                        filters.visitedFilter != null
                val activeCount = listOfNotNull(
                    filters.distinctions?.takeIf { it.isNotEmpty() },
                    filters.priceTiers?.takeIf { it.isNotEmpty() },
                    filters.cuisines?.takeIf { it.isNotEmpty() },
                    filters.visitedFilter,
                ).size
                FilterChip(
                    selected = anyFilterActive,
                    onClick = { showFiltersSheet = true },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = { Text(if (activeCount > 0) "Filters · $activeCount" else "Filters") },
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onCycleTheme) {
                    Icon(
                        imageVector = when (themeMode) {
                            ThemeMode.AUTO  -> Icons.Default.Brightness4
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK  -> Icons.Default.DarkMode
                        },
                        contentDescription = "Toggle theme",
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Foursquare API Key") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            onClick = { showKeyDialog = true; menuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Map cache") },
                            leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                            onClick = { showCacheDialog = true; menuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Export visited") },
                            leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                            onClick = { exportLauncher.launch("mmmap-visited.json"); menuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Import visited") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = { importLauncher.launch(arrayOf("application/json")); menuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Diagnostics") },
                            leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                            onClick = { viewModel.loadDebugInfo(); showDebug = true; menuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = { showAbout = true; menuExpanded = false },
                        )
                    }
                }
            }
        }

        // FAB — zooms to user location on press; dot appears automatically when GPS arrives
        FloatingActionButton(
            onClick = {
                val loc = userLatLon
                if (loc != null) {
                    mapHolder.map?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(loc.first, loc.second), 14.0)
                    )
                }
                if (!isLocating) viewModel.locateUser()
            },
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

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    if (showKeyDialog) {
        FoursquareKeyDialog(
            currentKey = foursquareKey,
            onSave = { key -> viewModel.saveFoursquareKey(key); showKeyDialog = false },
            onDismiss = { showKeyDialog = false },
        )
    }

    if (showCacheDialog) {
        CacheSettingsDialog(
            currentSizeMb = cacheSizeMb,
            onSizeSelected = { mb -> viewModel.setCacheSizeMb(mb) },
            onClearCache = { viewModel.clearTileCache() },
            onDismiss = { showCacheDialog = false },
        )
    }

    val ds = debugState
    if (showDebug && ds != null) {
        DebugInfoDialog(
            state = ds,
            onDismiss = { showDebug = false },
            onForceRefresh = { viewModel.forceRefresh() },
        )
    }

    if (showFiltersSheet) {
        FiltersSheet(
            filters = filters,
            availableCuisines = availableCuisines,
            onFiltersChange = { viewModel.updateFilters(it) },
            onDismiss = { showFiltersSheet = false },
        )
    }
    }  // Scaffold content lambda
}

/** Plain holder — not MutableState, so updating it doesn't trigger recomposition. */
private class MapHolder {
    var mapView: MapView? = null
    var map: MapLibreMap? = null
    var appliedStyleUrl: String? = null
    var pendingRestaurantCollection: FeatureCollection? = null
    var pendingUserLocation: Pair<Double, Double>? = null
    var pendingCameraTarget: Pair<Double, Double>? = null
}

