package com.chargekg.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargekg.app.R
import com.chargekg.app.data.Station
import com.chargekg.app.util.formatDistance
import com.chargekg.app.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearestSheet(
    viewModel: MainViewModel,
    fromUser: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.nearest.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.list_head), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(if (fromUser) R.string.geo_from_me else R.string.geo_map_center),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            when (val s = state) {
                is NearestState.Loading, NearestState.Idle ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(20.dp))
                        Spacer(Modifier.fillMaxWidth(0.05f))
                        Text(stringResource(R.string.list_loading))
                    }

                is NearestState.Failed ->
                    Text(stringResource(R.string.banner_error, s.message))

                is NearestState.Ready -> {
                    if (s.result.stations.isEmpty()) {
                        Text(stringResource(R.string.list_no_match))
                    } else {
                        LazyColumn(Modifier.heightIn(max = 460.dp)) {
                            items(s.result.stations, key = { it.id }) { station ->
                                NearestRow(station) { onSelect(station.id) }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NearestRow(station: Station, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.fillMaxWidth(0.68f)) {
            Text(station.name, style = MaterialTheme.typography.bodyLarge)
            if (station.address.isNotBlank()) {
                Text(
                    station.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // «по прямой» подписывается явно, когда маршрутизация недоступна:
            // иначе человек примет расстояние по воздуху за дорожное.
            val distance = when {
                station.roadM != null && station.roadS != null ->
                    "${formatDistance(context, station.roadM)} · ${formatDuration(context, station.roadS)}"

                station.airM != null ->
                    stringResource(R.string.dist_straight, formatDistance(context, station.airM))

                else -> ""
            }
            if (distance.isNotEmpty()) {
                Text(distance, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (station.unknownBusy) {
            Text(
                stringResource(R.string.station_unknown_busy),
                style = MaterialTheme.typography.labelMedium,
                color = StatusColors.unknown,
            )
        } else {
            Text(
                stringResource(R.string.station_free_of, station.free ?: 0, station.total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if ((station.free ?: 0) > 0) StatusColors.free else StatusColors.busy,
            )
        }
    }
}
