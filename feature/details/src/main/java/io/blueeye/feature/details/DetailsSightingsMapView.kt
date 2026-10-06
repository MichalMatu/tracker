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
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

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
            MapView(context).apply { onCreate(null) }
        }

    DisposableEffect(mapView, lifecycleOwner) {
        val lifecycleController = MapViewLifecycleController(mapView)
        var disposed = false

        val failureListener =
            MapView.OnDidFailLoadingMapListener { message ->
                if (!disposed) {
                    loadError = message.ifBlank { "Map tiles could not be loaded." }
                }
            }
        mapView.addOnDidFailLoadingMapListener(failureListener)

        val observer =
            LifecycleEventObserver { _, event ->
                lifecycleController.onEvent(event)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        lifecycleController.sync(lifecycleOwner.lifecycle.currentState)

        mapView.getMapAsync { mapLibreMap ->
            if (!disposed) {
                mapLibreMap.setStyle(MapConfig.styleUrl) {
                    if (!disposed) {
                        loadError = null
                        map = mapLibreMap
                    }
                }
            }
        }

        onDispose {
            disposed = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.removeOnDidFailLoadingMapListener(failureListener)
            lifecycleController.dispose()
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
                .height(MapConfig.heightDp.dp),
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

private class MapViewLifecycleController(
    private val mapView: MapView,
) {
    private var started = false
    private var resumed = false
    private var destroyed = false

    fun sync(state: Lifecycle.State) {
        if (state.isAtLeast(Lifecycle.State.STARTED)) {
            start()
        }
        if (state.isAtLeast(Lifecycle.State.RESUMED)) {
            resume()
        }
    }

    fun onEvent(event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> start()
            Lifecycle.Event.ON_RESUME -> resume()
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_STOP -> stop()
            Lifecycle.Event.ON_DESTROY -> dispose()
            else -> Unit
        }
    }

    fun dispose() {
        pause()
        stop()
        if (!destroyed) {
            mapView.onDestroy()
            destroyed = true
        }
    }

    private fun start() {
        if (!started && !destroyed) {
            mapView.onStart()
            started = true
        }
    }

    private fun resume() {
        if (!resumed && !destroyed) {
            mapView.onResume()
            resumed = true
        }
    }

    private fun pause() {
        if (resumed) {
            mapView.onPause()
            resumed = false
        }
    }

    private fun stop() {
        if (started) {
            mapView.onStop()
            started = false
        }
    }
}

@Suppress("DEPRECATION")
private fun renderSightings(
    map: MapLibreMap,
    clusters: List<DetailsSightingCluster>,
) {
    map.clear()
    val points =
        clusters.map { cluster ->
            LatLng(cluster.latitude, cluster.longitude).also { point ->
                map.addMarker(
                    MarkerOptions()
                        .position(point)
                        .title(clusterTitle(cluster))
                        .snippet(clusterSnippet(cluster)),
                )
            }
        }

    when (points.size) {
        0 -> Unit
        1 ->
            map.cameraPosition =
                CameraPosition.Builder()
                    .target(points.single())
                    .zoom(MapConfig.singlePointZoom)
                    .build()
        else -> fitCameraToPoints(map, points)
    }
}

private fun fitCameraToPoints(
    map: MapLibreMap,
    points: List<LatLng>,
) {
    val boundsBuilder = LatLngBounds.Builder()
    points.forEach(boundsBuilder::include)
    map.getCameraForLatLngBounds(
        boundsBuilder.build(),
        IntIntArray(MapConfig.cameraPaddingSides) { MapConfig.cameraPaddingPx },
    )?.let { camera ->
        map.cameraPosition = camera
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

private object MapConfig {
    const val styleUrl = "https://tiles.openfreemap.org/styles/liberty"
    const val heightDp = 280
    const val singlePointZoom = 16.0
    const val cameraPaddingPx = 48
    const val cameraPaddingSides = 4
}
