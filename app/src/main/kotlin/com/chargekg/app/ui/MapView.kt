package com.chargekg.app.ui

import android.content.Context
import android.preference.PreferenceManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chargekg.app.BuildConfig
import com.chargekg.app.data.Station
import com.chargekg.app.domain.clusterStations
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** Бишкек — стартовый вид карты, как и на сайте. */
val BISHKEK = GeoPoint(42.8746, 74.6122)

const val START_ZOOM = 12.0

/**
 * Настройка osmdroid. Кэш тайлов ограничен потолком: это единственное, что
 * приложение заметно пишет на диск, и без ограничения он рос бы бесконечно.
 */
fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    @Suppress("DEPRECATION")
    config.load(context, PreferenceManager.getDefaultSharedPreferences(context))
    // Публичные тайлы OSM отдают 429 без внятного User-Agent.
    config.userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
    config.osmdroidBasePath = context.filesDir
    config.osmdroidTileCache = context.cacheDir.resolve("tiles")
    config.tileFileSystemCacheMaxBytes = 40L * 1024 * 1024
    config.tileFileSystemCacheTrimBytes = 32L * 1024 * 1024
}

/**
 * MapView живёт вне рекомпозиции и получает события жизненного цикла: без
 * onPause() osmdroid продолжает грузить тайлы свёрнутым, тратя чужой трафик.
 */
@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            // Кнопки зума не нужны: жесты есть у всех, а панель перекрывает карту.
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(START_ZOOM)
            controller.setCenter(BISHKEK)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
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
            mapView.onDetach()
        }
    }
    return mapView
}

@Composable
fun StationMap(
    mapView: MapView,
    stations: List<Station>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    var zoom by remember { mutableDoubleStateOf(mapView.zoomLevelDouble) }

    // Состав групп зависит только от зума, поэтому слушаем именно его. При
    // прокрутке пересобирать нечего — и метки не «прыгают» под пальцем.
    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onZoom(event: ZoomEvent?): Boolean {
                zoom = event?.zoomLevel ?: mapView.zoomLevelDouble
                return false
            }

            override fun onScroll(event: ScrollEvent?): Boolean = false
        }
        mapView.addMapListener(listener)
        onDispose { mapView.removeMapListener(listener) }
    }

    // Метки пересобираются только при смене набора станций, зума или
    // выделения — не на каждой рекомпозиции: станций больше четырёхсот.
    LaunchedEffect(stations, selectedId, zoom) {
        val clusters = clusterStations(stations, zoom, density = resources.displayMetrics.density)
        mapView.overlays.removeAll { it is Marker }
        for (cluster in clusters) {
            val single = cluster.single
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(cluster.lat, cluster.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = PinIcons.forCluster(resources, cluster, cluster.id == selectedId)
                    // Попап osmdroid не используется: карточка станции живёт в
                    // Compose и умеет тему и выбранный язык.
                    infoWindow = null
                    setOnMarkerClickListener { _, _ ->
                        if (single != null) {
                            onSelect(single.id)
                        } else {
                            // По группе открывать нечего — приближаем, пока она
                            // не рассыплется на отдельные станции.
                            mapView.controller.setZoom(mapView.zoomLevelDouble + 2.0)
                            mapView.controller.animateTo(GeoPoint(cluster.lat, cluster.lng))
                        }
                        true
                    }
                }
            )
        }
        mapView.invalidate()
    }

    AndroidView(modifier = modifier, factory = { mapView })
}
