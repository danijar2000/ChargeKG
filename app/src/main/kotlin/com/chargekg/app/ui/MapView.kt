package com.chargekg.app.ui

import android.content.Context
import android.preference.PreferenceManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.chargekg.app.domain.CLUSTER_MAX_ZOOM
import com.chargekg.app.domain.Cluster
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

    // onZoom приходит на каждом кадре щипка и анимации. Сырой зум в ключе
    // пересобирал бы все метки и рисовал новые битмапы шестьдесят раз в
    // секунду. Ступень в пол-уровня: ячейка группы меняется не больше чем в
    // полтора раза, а пересборка случается раз за ступень.
    val zoomStep by remember {
        derivedStateOf {
            // Округление не должно дотянуть 14,8 до 15 и тем самым выключить
            // группировку раньше, чем карта до неё доросла.
            if (zoom >= CLUSTER_MAX_ZOOM) {
                CLUSTER_MAX_ZOOM
            } else {
                minOf(Math.round(zoom * 2) / 2.0, CLUSTER_MAX_ZOOM - 0.5)
            }
        }
    }
    val clusters = remember(stations, zoomStep) {
        clusterStations(stations, zoomStep, density = resources.displayMetrics.density)
    }

    // Метки переиспользуются по идентификатору группы: новый Marker заводится
    // только для появившейся группы. Смена выделения поэтому стоит двух
    // иконок из кэша, а не шестисот новых объектов.
    val markers = remember(mapView) { HashMap<String, Marker>() }
    LaunchedEffect(clusters, selectedId) {
        // MapView живёт дольше этой функции. Если карта вернулась в
        // композицию, на ней остались метки прежнего прохода, о которых
        // свежая таблица не знает, — без чистки они задвоились бы.
        if (markers.isEmpty()) mapView.overlays.removeAll { it is Marker }
        val alive = HashSet<String>(clusters.size * 2)
        for (cluster in clusters) {
            alive += cluster.id
            val marker = markers.getOrPut(cluster.id) {
                Marker(mapView).apply {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    // Попап osmdroid не используется: карточка станции живёт в
                    // Compose и умеет тему и выбранный язык.
                    infoWindow = null
                    mapView.overlays.add(this)
                }
            }
            bind(marker, mapView, cluster, cluster.id == selectedId, onSelect)
        }
        val gone = markers.keys.filter { it !in alive }
        for (id in gone) {
            markers.remove(id)?.let { mapView.overlays.remove(it) }
        }
        mapView.invalidate()
    }

    AndroidView(modifier = modifier, factory = { mapView })
}

private fun bind(
    marker: Marker,
    mapView: MapView,
    cluster: Cluster,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    val lat = cluster.lat
    val lng = cluster.lng
    if (marker.position.latitude != lat || marker.position.longitude != lng) {
        marker.position = GeoPoint(lat, lng)
    }
    // Иконка берётся из кэша PinIcons: присвоить готовый объект дёшево,
    // дорого было рисовать новый битмап.
    marker.icon = PinIcons.forCluster(mapView.resources, cluster, selected)
    val single = cluster.single
    marker.setOnMarkerClickListener { _, _ ->
        if (single != null) {
            onSelect(single.id)
        } else {
            // По группе открывать нечего — приближаем, пока она не
            // рассыплется на отдельные станции.
            mapView.controller.setZoom(mapView.zoomLevelDouble + 2.0)
            mapView.controller.animateTo(GeoPoint(lat, lng))
        }
        true
    }
}
