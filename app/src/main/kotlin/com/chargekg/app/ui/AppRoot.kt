package com.chargekg.app.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

/** Какая панель открыта поверх карты. Роутер не нужен: экранов шесть. */
private enum class Panel { NONE, STATION, NEAREST, FILTERS, SETTINGS, ABOUT }

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
    var panel by remember { mutableStateOf(Panel.NONE) }
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
    // до того, как что-то нажали на карте.
    LaunchedEffect(selectedId) {
        if (selectedId != null) {
            panel = Panel.STATION
            viewModel.station(selectedId!!)?.let {
                mapView.controller.animateTo(GeoPoint(it.lat, it.lng))
            }
        } else if (panel == Panel.STATION) {
            panel = Panel.NONE
        }
    }

    val locate: () -> Unit = {
        scope.launch {
            val location = getLocationOnce(context)
            viewModel.setUserLocation(location)
            if (location != null) {
                mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                mapView.controller.setZoom(14.0)
            } else {
                Toast.makeText(context, R.string.geo_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
        Unit
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            locate()
        } else {
            Toast.makeText(context, R.string.geo_denied, Toast.LENGTH_LONG).show()
        }
    }

    val requestLocation: () -> Unit = {
        if (hasLocationPermission(context)) locate()
        else permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        )
    }

    Box(Modifier.fillMaxSize()) {
        StationMap(
            mapView = mapView,
            stations = visible,
            selectedId = selectedId,
            onSelect = viewModel::select,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Banner(stationsState)
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapButton(AppIcons.Filter, R.string.btn_filters) { panel = Panel.FILTERS }
                MapButton(AppIcons.List, R.string.btn_list) {
                    panel = Panel.NEAREST
                    val anchor = userLocation?.let { Anchor(it.latitude, it.longitude, true) }
                        ?: (mapView.mapCenter as GeoPoint).let { Anchor(it.latitude, it.longitude, false) }
                    viewModel.loadNearest(anchor)
                }
                MapButton(AppIcons.MyLocation, R.string.btn_locate, onClick = requestLocation)
                MapButton(AppIcons.Refresh, R.string.btn_refresh) { viewModel.refresh() }
                MapButton(AppIcons.Settings, R.string.settings_title) { panel = Panel.SETTINGS }
            }
        }

        if (stationsState.loading && stationsState.stations.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        CountsBadge(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(16.dp),
        )
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
            nav = prefs.nav,
            onFilters = viewModel::setFilters,
            onNav = viewModel::setNav,
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

@Composable
private fun MapButton(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(icon, contentDescription = stringResource(labelRes))
    }
}

/**
 * Полоса состояния данных. Молчит, когда всё в порядке: постоянный баннер на
 * карте только мешает.
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

        state.staleCount > 0 -> stringResource(R.string.banner_stale, state.staleCount)

        else -> null
    } ?: return

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
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
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(0.dp))
        }
    }
}
