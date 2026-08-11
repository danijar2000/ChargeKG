package com.chargekg.app.ui

import android.content.Context
import android.preference.PreferenceManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chargekg.app.BuildConfig
import com.chargekg.app.data.Station
import org.osmdroid.config.Configuration
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

    // Маркеры пересобираются только при смене набора станций или выделения —
    // не на каждой рекомпозиции: их шесть сотен.
    LaunchedEffect(stations, selectedId) {
        mapView.overlays.removeAll { it is Marker }
        for (station in stations) {
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(station.lat, station.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = PinIcons.forStation(resources, station, station.id == selectedId)
                    // Попап osmdroid не используется: карточка станции живёт в
                    // Compose и умеет тему и выбранный язык.
                    infoWindow = null
                    setOnMarkerClickListener { _, _ ->
                        onSelect(station.id)
                        true
                    }
                }
            )
        }
        mapView.invalidate()
    }

    AndroidView(modifier = modifier, factory = { mapView })
}
