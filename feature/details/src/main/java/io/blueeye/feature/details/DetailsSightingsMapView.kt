package io.blueeye.feature.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.math.abs

@Composable
internal fun DetailsSightingsMapView(
    clusters: List<DetailsSightingCluster>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val mapView =
        remember(context) {
            MapLibre.getInstance(context.applicationContext)
            MapView(context).apply {
                onCreate(null)
            }
        }

    DisposableEffect(mapView, lifecycleOwner) {
        var started = false
        var resumed = false
        var destroyed = false

        fun pauseIfNeeded() {
            if (resumed) {
                mapView.onPause()
                resumed = false
            }
        }

        fun stopIfNeeded() {
            if (started) {
                mapView.onStop()
                started = false
            }
        }

        fun destroyIfNeeded() {
            if (!destroyed) {
                mapView.onDestroy()
                destroyed = true
            }
        }

        val failureListener =
            MapView.OnDidFailLoadingMapListener { message ->
                loadError = message.ifBlank { "Map tiles could not be loaded." }
            }
        mapView.addOnDidFailLoadingMapListener(failureListener)

        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        if (!started) {
                            mapView.onStart()
                            started = true
                        }
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        if (!resumed) {
                            mapView.onResume()
                            resumed = true
                        }
                    }

                    Lifecycle.Event.ON_PAUSE -> pauseIfNeeded()
                    Lifecycle.Event.ON_STOP -> stopIfNeeded()
                    Lifecycle.Event.ON_DESTROY -> {
                        pauseIfNeeded()
                        stopIfNeeded()
                        destroyIfNeeded()
                    }

                    else -> Unit
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
            started = true
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
            resumed = true
        }

        mapView.getMapAsync { mapLibreMap ->
            mapLibreMap.setStyle(OPEN_FREE_MAP_STYLE_URL) {
                loadError = null
                map = mapLibreMap
            }
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.removeOnDidFailLoadingMapListener(failureListener)
            pauseIfNeeded()
            stopIfNeeded()
            destroyIfNeeded()
            map = null
        }
    }

    LaunchedEffect(map, clusters) {
        map?.let { mapLibreMap ->
            renderSightings(
                map = mapLibreMap,
                clusters = clusters,
            )
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(MAP_HEIGHT_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
        loadError?.let {
            Text(
                text = "Map unavailable. Check your internet connection and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun renderSightings(
    map: MapLibreMap,
    clusters: List<DetailsSightingCluster>,
) {
    map.clear()
    clusters.forEach { cluster ->
        map.addMarker(
            MarkerOptions()
                .position(LatLng(cluster.latitude, cluster.longitude))
                .title(clusterTitle(cluster))
                .snippet(clusterSnippet(cluster)),
        )
    }

    if (clusters.isNotEmpty()) {
        map.cameraPosition = cameraPositionFor(clusters)
    }
}

private fun clusterTitle(cluster: DetailsSightingCluster): String =
    if (cluster.observationCount == 1) {
        "1 phone observation"
    } else {
        "${cluster.observationCount} phone observations"
    }

private fun clusterSnippet(cluster: DetailsSightingCluster): String =
    "GPS accuracy ${cluster.bestAccuracyMeters.toInt()}–${cluster.worstAccuracyMeters.toInt()} m · " +
        "avg RSSI ${cluster.averageRssi.toInt()} dBm"

private fun cameraPositionFor(clusters: List<DetailsSightingCluster>): CameraPosition {
    val centerLatitude = clusters.map(DetailsSightingCluster::latitude).average()
    val centerLongitude = clusters.map(DetailsSightingCluster::longitude).average()
    val latitudeSpan =
        clusters.maxOf(DetailsSightingCluster::latitude) -
            clusters.minOf(DetailsSightingCluster::latitude)
    val longitudeSpan =
        clusters.maxOf(DetailsSightingCluster::longitude) -
            clusters.minOf(DetailsSightingCluster::longitude)
    val span = maxOf(abs(latitudeSpan), abs(longitudeSpan))

    return CameraPosition.Builder()
        .target(LatLng(centerLatitude, centerLongitude))
        .zoom(zoomForSpan(span))
        .build()
}

private fun zoomForSpan(spanDegrees: Double): Double =
    when {
        spanDegrees <= VERY_CLOSE_SPAN_DEGREES -> VERY_CLOSE_ZOOM
        spanDegrees <= NEARBY_SPAN_DEGREES -> NEARBY_ZOOM
        spanDegrees <= LOCAL_SPAN_DEGREES -> LOCAL_ZOOM
        spanDegrees <= CITY_SPAN_DEGREES -> CITY_ZOOM
        spanDegrees <= REGIONAL_SPAN_DEGREES -> REGIONAL_ZOOM
        else -> WIDE_ZOOM
    }

private const val OPEN_FREE_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val MAP_HEIGHT_DP = 280
private const val VERY_CLOSE_SPAN_DEGREES = 0.002
private const val NEARBY_SPAN_DEGREES = 0.01
private const val LOCAL_SPAN_DEGREES = 0.05
private const val CITY_SPAN_DEGREES = 0.2
private const val REGIONAL_SPAN_DEGREES = 1.0
private const val VERY_CLOSE_ZOOM = 16.0
private const val NEARBY_ZOOM = 14.0
private const val LOCAL_ZOOM = 12.0
private const val CITY_ZOOM = 10.0
private const val REGIONAL_ZOOM = 8.0
private const val WIDE_ZOOM = 5.0
