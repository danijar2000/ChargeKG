package com.chargekg.app.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargekg.app.R
import com.chargekg.app.container
import com.chargekg.app.data.DataOrigin
import com.chargekg.app.data.LoadError
import com.chargekg.app.update.UpdateChecker
import com.chargekg.app.util.getLocationOnce
import com.chargekg.app.util.hasLocationPermission
import com.chargekg.app.util.lastKnownLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.TileSystem

/** Какая панель открыта поверх карты. Роутер не нужен: экранов шесть. */
private enum class Panel { NONE, STATION, NEAREST, FILTERS, SETTINGS, ABOUT }

/** Зум «вокруг меня»: видно свой район и ближайшие зарядки, а не весь город. */
private const val ME_ZOOM = 15.0

/** Зум выбранной станции: метка отдельная, не в группе. */
private const val STATION_ZOOM = 16.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val stationsState by viewModel.stations.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val selectedId by viewModel.selected.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()

    val mapView = rememberMapView()
    // Открытая панель переживает смену языка (recreate) и смерть процесса:
    // иначе человек возвращался бы в приложение к пустой карте.
    var panel by rememberSaveable { mutableStateOf(Panel.NONE) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    // Автопроверка обновления при запуске: один запрос примерно на 5 КБ,
    // с троттлингом в 10 минут. Ошибки — сеть, лимит GitHub — глотаются молча
    // внутри launchUpdateCheck: человек не просил проверять.
    LaunchedEffect(Unit) {
        if (UpdateChecker.isAutoCheckEnabled(context)) {
            scope.launchUpdateCheck(context, force = false) { updateState = it }
        }
    }

    val visible = remember(stationsState.stations, prefs.filters) {
        viewModel.visible(stationsState, prefs.filters)
    }

    // Карточка станции открывается и по deep link — тогда выделение приходит
    // до того, как что-то нажали на карте. При холодном старте станций ещё
    // нет, поэтому эффект перезапускается, когда они появятся: иначе карточка
    // открылась бы позже, а карта так и осталась бы над Бишкеком.
    val stationsReady = stationsState.stations.isNotEmpty()
    LaunchedEffect(selectedId, stationsReady) {
        val id = selectedId
        if (id != null) {
            panel = Panel.STATION
            val station = viewModel.station(id)
            if (station != null) {
                // Приближаем, а не только сдвигаем: из группы «338» иначе не
                // понять, какая из меток выбрана.
                val zoom = maxOf(mapView.zoomLevelDouble, STATION_ZOOM)
                mapView.controller.animateTo(aboveSheet(station.lat, station.lng, zoom, mapView.height), zoom, null)
            } else if (stationsState.origin == DataOrigin.NETWORK) {
                // Свежий список пришёл, а такой станции в нём нет — ссылка
                // устарела. Пустая панель без содержимого висела бы вечно.
                Toast.makeText(context, R.string.station_not_found, Toast.LENGTH_SHORT).show()
                viewModel.select(null)
            }
        } else if (panel == Panel.STATION) {
            panel = Panel.NONE
        }
    }

    // Одно определение места за раз: каждое нажатие добавляло бы ещё одного
    // слушателя GPS, а ответа можно ждать до пятнадцати секунд.
    var locating by remember { mutableStateOf<Job?>(null) }
    fun locate(silent: Boolean, move: Boolean = true) {
        if (locating != null) return
        locating = scope.launch {
            try {
                val location = getLocationOnce(context)
                if (location != null) {
                    viewModel.setUserLocation(location)
                    if (move) {
                        mapView.controller.animateTo(
                            GeoPoint(location.latitude, location.longitude), ME_ZOOM, null,
                        )
                    }
                } else if (!silent) {
                    Toast.makeText(context, R.string.geo_unavailable, Toast.LENGTH_SHORT).show()
                }
            } finally {
                // Сброс — это и сигнал кнопке перестать крутиться: состояние
                // самой Job Compose не наблюдает.
                locating = null
            }
        }
    }

    // Запуск: если доступ к месту уже есть, карта сразу встаёт над человеком.
    // Последнее известное место — мгновенно, свежая точка уточняет позже.
    // Раньше приложение открывало весь Бишкек и ждало нажатия кнопки.
    // Флаг сохраняется: поворот экрана и смена языка не должны снова
    // утаскивать карту, которую человек уже подвинул.
    var autoCentered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (autoCentered) return@LaunchedEffect
        autoCentered = true
        if (!hasLocationPermission(context)) return@LaunchedEffect
        // Deep link на станцию важнее: карта уже едет к ней.
        val followMe = viewModel.selected.value == null
        lastKnownLocation(context)?.let {
            viewModel.setUserLocation(it)
            if (followMe) {
                mapView.controller.setZoom(ME_ZOOM)
                mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
            }
        }
        locate(silent = true, move = followMe)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            locate(silent = false)
        } else {
            Toast.makeText(context, R.string.geo_denied, Toast.LENGTH_LONG).show()
        }
    }

    val requestLocation: () -> Unit = {
        if (hasLocationPermission(context)) locate(silent = false)
        else permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        )
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Box(Modifier.fillMaxSize()) {
        StationMap(
            mapView = mapView,
            stations = visible,
            selectedId = selectedId,
            onSelect = viewModel::select,
            me = userLocation?.let { GeoPoint(it.latitude, it.longitude) },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(insets)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
        ) {
            // Частое — слева под большим пальцем при чтении карты; редкое —
            // справа. Определение места уехало вниз, к пальцу.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MapButton(
                    AppIcons.Filter,
                    R.string.btn_filters,
                    // Без отметки человек забывал о включённом фильтре и
                    // думал, что станции пропали.
                    dot = !prefs.filters.isDefault,
                ) { panel = Panel.FILTERS }
                Spacer(Modifier.size(8.dp))
                MapButton(AppIcons.List, R.string.btn_list) {
                    panel = Panel.NEAREST
                    val anchor = userLocation?.let { Anchor(it.latitude, it.longitude, true) }
                        ?: (mapView.mapCenter as GeoPoint).let { Anchor(it.latitude, it.longitude, false) }
                    viewModel.loadNearest(anchor)
                }
                Spacer(Modifier.weight(1f))
                MapButton(AppIcons.Refresh, R.string.btn_refresh) { viewModel.refresh() }
                Spacer(Modifier.size(8.dp))
                MapButton(AppIcons.Settings, R.string.settings_title) { panel = Panel.SETTINGS }
            }
            Spacer(Modifier.size(8.dp))
            Banner(stationsState)
        }

        if (stationsState.loading && stationsState.stations.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        CountsBadge(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(insets)
                .padding(16.dp),
        )

        FloatingActionButton(
            onClick = requestLocation,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(insets)
                .padding(16.dp),
        ) {
            // Пока ждём спутники, кнопка крутится: иначе полминуты тишины
            // выглядели бы как неработающая кнопка.
            if (locating != null) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(AppIcons.MyLocation, contentDescription = stringResource(R.string.btn_locate))
            }
        }
    }

    when (panel) {
        Panel.STATION -> selectedId?.let { id ->
            viewModel.station(id)?.let { station ->
                StationSheet(
                    station = station,
                    nav = prefs.nav,
                    onDismiss = { viewModel.select(null) },
                )
            }
        }

        Panel.NEAREST -> NearestSheet(
            viewModel = viewModel,
            fromUser = userLocation != null,
            onSelect = { id ->
                panel = Panel.NONE
                viewModel.select(id)
            },
            onDismiss = { panel = Panel.NONE },
        )

        Panel.FILTERS -> FiltersSheet(
            filters = prefs.filters,
            matchCount = visible.size,
            onFilters = viewModel::setFilters,
            onDismiss = { panel = Panel.NONE },
        )

        Panel.SETTINGS -> SettingsSheet(
            viewModel = viewModel,
            prefs = prefs,
            onAbout = { panel = Panel.ABOUT },
            onDismiss = { panel = Panel.NONE },
        )

        Panel.ABOUT -> AboutSheet(onDismiss = { panel = Panel.NONE })

        Panel.NONE -> Unit
    }

    UpdateDialog(
        state = updateState,
        onState = { updateState = it },
        checker = context.container.updateChecker,
    )
}

/**
 * Центр карты, при котором станция окажется в верхней четверти экрана.
 * Карточка закрывает нижнюю половину, и при обычном центрировании выбранная
 * метка пряталась ровно под ней.
 */
private fun aboveSheet(lat: Double, lng: Double, zoom: Double, mapHeightPx: Int): GeoPoint {
    if (mapHeightPx <= 0) return GeoPoint(lat, lng)
    val shiftPx = mapHeightPx * 0.25
    // Градусов долготы на пиксель на экваторе; по широте в Меркаторе
    // множитель cos(lat).
    val degPerPx = 360.0 / (TileSystem.getTileSize() * Math.pow(2.0, zoom))
    val dLat = shiftPx * degPerPx * Math.cos(Math.toRadians(lat))
    return GeoPoint(lat - dLat, lng)
}

@Composable
private fun MapButton(
    icon: ImageVector,
    labelRes: Int,
    dot: Boolean = false,
    onClick: () -> Unit,
) {
    Box {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(icon, contentDescription = stringResource(labelRes))
        }
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

/**
 * Полоса состояния данных. Молчит, когда всё в порядке, и закрывается
 * крестиком: про одну устаревшую станцию раньше кричал баннер на три строки,
 * закрывавший треть карты навсегда.
 */
@Composable
private fun Banner(state: com.chargekg.app.data.StationsState) {
    val error = state.error
    val hasData = state.stations.isNotEmpty()
    val text = when {
        // Сырой текст исключения OkHttp приходит по-английски: при выбранном
        // русском он выглядел бы в интерфейсе чужим мусором.
        error is LoadError.Offline && hasData -> stringResource(R.string.banner_offline)
        error is LoadError.Offline -> stringResource(R.string.banner_no_connection)

        error is LoadError.Message && hasData ->
            stringResource(R.string.banner_error_cached, error.text)

        error is LoadError.Message -> stringResource(R.string.banner_error, error.text)

        state.origin == DataOrigin.CACHE -> stringResource(R.string.banner_offline)

        state.staleCount > 0 -> stringResource(R.string.banner_stale)

        else -> null
    } ?: return

    // Закрытие привязано к тексту и переживает перезапуск: та же самая
    // «устаревшая станция» не должна кричать при каждом открытии. Новая
    // беда — другой текст — покажется снова.
    val context = LocalContext.current
    val store = remember { context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(store.getString("banner_dismissed", null)) }
    if (dismissed == text) return

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
            IconButton(
                onClick = {
                    dismissed = text
                    store.edit().putString("banner_dismissed", text).apply()
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    AppIcons.Close,
                    contentDescription = stringResource(R.string.btn_close),
                    Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Сколько станций видно и сколько портов свободно — как счётчик на сайте. */
@Composable
private fun CountsBadge(
    visible: List<com.chargekg.app.data.Station>,
    modifier: Modifier = Modifier,
) {
    if (visible.isEmpty()) return
    val known = visible.filter { !it.unknownBusy }
    val text = if (known.isEmpty()) {
        stringResource(R.string.counts_unknown, visible.size)
    } else {
        stringResource(
            R.string.counts_known,
            visible.size,
            known.sumOf { it.free ?: 0 },
            known.sumOf { it.total },
        )
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
