package pl.kaucjapro.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import pl.kaucjapro.app.net.MapPlace
import pl.kaucjapro.app.net.OverpassClient
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private const val STORE_MARKER_COLOR = 0xFFE08A00.toInt()

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val mapView = remember {
        val cfg = Configuration.getInstance()
        cfg.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        cfg.userAgentValue = "KaucjaPRO/1.0 (${context.packageName})"
        cfg.osmdroidBasePath = File(context.cacheDir, "osmdroid")
        cfg.osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 4.0
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(52.0, 19.4)) // środek Polski
            overlays.add(CopyrightOverlay(context))     // wymagane przez licencję OSM
        }
    }

    var results by remember { mutableStateOf<List<MapPlace>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    var includeStores by remember { mutableStateOf(false) }
    var refPoint by remember { mutableStateOf<GeoPoint?>(null) }

    fun search(center: GeoPoint) {
        scope.launch {
            loading = true
            error = null
            refPoint = center
            try {
                val found = OverpassClient.find(center.latitude, center.longitude, includeStores)
                results = found.sortedBy { distanceM(center, it) }
                searched = true
            } catch (e: Exception) {
                error = "Nie udało się pobrać punktów: ${e.message ?: "brak połączenia"}"
            } finally {
                loading = false
            }
        }
    }

    fun mapCenterPoint() = GeoPoint(mapView.mapCenter.latitude, mapView.mapCenter.longitude)

    // --- lokalizacja ---
    var hasLocation by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasLocation = hasLocationPermission(context) }

    LaunchedEffect(Unit) {
        if (!hasLocation) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    val myLocation = remember { MyLocationNewOverlay(GpsMyLocationProvider(context), mapView) }

    LaunchedEffect(hasLocation) {
        if (hasLocation) {
            myLocation.enableMyLocation()
            if (!mapView.overlays.contains(myLocation)) mapView.overlays.add(myLocation)
            myLocation.runOnFirstFix {
                // wywoływane w tle - wracamy na główny wątek
                mapView.post {
                    val loc = myLocation.myLocation
                    if (loc != null) {
                        mapView.controller.setZoom(13.0)
                        mapView.controller.animateTo(loc)
                        search(GeoPoint(loc.latitude, loc.longitude))
                    }
                }
            }
        }
    }

    // --- cykl życia mapy ---
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            myLocation.disableMyLocation()
            mapView.onDetach()
        }
    }

    // --- znaczniki ---
    LaunchedEffect(results) {
        mapView.overlays.removeAll { it is Marker }
        results.forEach { place ->
            val marker = Marker(mapView)
            marker.position = GeoPoint(place.lat, place.lon)
            marker.title = place.name
            marker.snippet = place.details
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            if (place.isStore) marker.icon?.mutate()?.setTint(STORE_MARKER_COLOR)
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { search(mapCenterPoint()) }) { Text("Szukaj tutaj") }
                FilterChip(
                    selected = includeStores,
                    onClick = {
                        includeStores = !includeStores
                        search(refPoint ?: mapCenterPoint())
                    },
                    label = { Text("Pokaż sklepy") },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        ResultsPanel(
            results = results,
            ref = refPoint,
            searched = searched,
            error = error,
            onPick = { place ->
                mapView.controller.setZoom(17.0)
                mapView.controller.animateTo(GeoPoint(place.lat, place.lon))
            }
        )
    }
}

@Composable
private fun ResultsPanel(
    results: List<MapPlace>,
    ref: GeoPoint?,
    searched: Boolean,
    error: String?,
    onPick: (MapPlace) -> Unit
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                !searched -> Text("Włącz lokalizację albo przesuń mapę i naciśnij „Szukaj tutaj”.")
                results.isEmpty() -> Text(
                    "W tej okolicy nie ma kaucjomatów w danych OpenStreetMap. " +
                        "Zaznacz „Pokaż sklepy”, aby zobaczyć supermarkety — " +
                        "często przyjmują zwrot opakowań, ale sprawdź to na miejscu."
                )
                else -> {
                    Text("Najbliższe punkty", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    results.take(8).forEach { place ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(place) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(place.name, fontWeight = FontWeight.Medium)
                                Text(
                                    if (place.isStore) "Sklep (pomarańczowy znacznik)" else "Kaucjomat",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (ref != null) Text(formatDistance(distanceM(ref, place)))
                        }
                    }
                }
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun distanceM(from: GeoPoint, place: MapPlace): Float {
    val out = FloatArray(1)
    Location.distanceBetween(from.latitude, from.longitude, place.lat, place.lon, out)
    return out[0]
}

private fun formatDistance(meters: Float): String =
    if (meters < 1000f) "${meters.roundToInt()} m"
    else String.format(Locale.forLanguageTag("pl-PL"), "%.1f km", meters / 1000f)
