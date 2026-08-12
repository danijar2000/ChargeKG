package com.chargekg.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chargekg.app.R
import com.chargekg.app.data.Connector
import com.chargekg.app.data.Station
import com.chargekg.app.util.NavApp
import com.chargekg.app.util.formatDistance
import com.chargekg.app.util.formatDuration
import com.chargekg.app.util.formatPower
import com.chargekg.app.util.NetworkAppResult
import com.chargekg.app.util.openNetworkApp
import com.chargekg.app.util.openRoute
import java.util.Locale

/** Ссылка на станцию: она же уходит в «поделиться» и открывается deep link'ом. */
fun stationLink(station: Station) = "https://chargekg.com/s/${station.id}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSheet(station: Station, nav: NavApp, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(station.name, style = MaterialTheme.typography.headlineSmall)
            if (station.address.isNotBlank()) {
                Text(
                    station.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            station.network?.let {
                Text(
                    it.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))
            Availability(station)

            // Расстояние есть только когда карточка открыта из списка ближайших.
            station.airM?.let { air ->
                Spacer(Modifier.height(6.dp))
                val text = if (station.roadM != null && station.roadS != null) {
                    "${formatDistance(context, station.roadM)} · ${formatDuration(context, station.roadS)}"
                } else {
                    stringResource(R.string.dist_straight, formatDistance(context, air))
                }
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }

            // Акции и ограничения доступа. Показывать обязательно: сюда же
            // попадает «станция доступна только для сотрудников».
            if (station.promotions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                for (promo in station.promotions) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        Text(promo, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (station.mergedCount > 1) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.station_merged, station.mergedCount),
                    style = MaterialTheme.typography.labelLarge,
                )
                for (part in station.merged) {
                    Text(
                        "• ${part.name}" + if (part.address.isNotBlank()) ", ${part.address}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            for (connector in station.connectors) {
                ConnectorRow(connector)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (!openRoute(context, station, nav)) {
                        Toast.makeText(context, R.string.station_no_nav, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(AppIcons.Route, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.station_route))
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                station.network?.let {
                    OutlinedButton(
                        onClick = {
                            // Человек ждёт, что попадёт на свою точку. Если сеть
                            // так не умеет, об этом надо сказать, а не оставлять
                            // его искать станцию глазами на чужой карте.
                            val message = when (openNetworkApp(context, station)) {
                                NetworkAppResult.STATION -> null
                                NetworkAppResult.APP_ONLY -> R.string.station_app_no_link
                                NetworkAppResult.FAILED -> R.string.station_no_app
                            }
                            message?.let {
                                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(AppIcons.OpenApp, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.station_app))
                    }
                }
                OutlinedButton(
                    onClick = { shareStation(context, station) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(AppIcons.Share, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.btn_share))
                }
            }

            TextButton(onClick = { copyCoordinates(context, station) }) {
                Icon(AppIcons.Copy, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.btn_copy))
            }
        }
    }
}

@Composable
private fun Availability(station: Station) {
    // null и 0 — разные вещи: «неизвестно» и «нет свободных». Подменять одно
    // другим значит соврать ровно в том, ради чего карту открывают.
    if (station.unknownBusy) {
        Text(
            stringResource(R.string.station_unknown_busy),
            style = MaterialTheme.typography.titleMedium,
            color = StatusColors.unknown,
        )
        return
    }
    Text(
        stringResource(R.string.station_free_of, station.free ?: 0, station.total),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if ((station.free ?: 0) > 0) StatusColors.free else StatusColors.busy,
    )
}

@Composable
private fun ConnectorRow(connector: Connector) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Значок разъёма — тот же, что на сайте. Для неопознанного типа его
        // нет, и тогда строка начинается сразу с названия.
        ConnectorIcons.forType(connector.type)?.let { icon ->
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(10.dp))
        }
        Column(Modifier.weight(1f)) {
            // Когда сервер не смог опознать разъём, показывается исходное
            // название сети: «прочее» без пояснения бесполезно.
            val title = connector.type?.title
                ?: connector.rawType.ifBlank { stringResource(R.string.type_other) }
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                connector.priceText.ifBlank { stringResource(R.string.station_price_unknown) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AssistChip(onClick = {}, label = { Text(formatPower(context, connector.power)) })
    }
}

private fun shareStation(context: Context, station: Station) {
    val text = buildString {
        append(station.name)
        if (station.address.isNotBlank()) append(", ").append(station.address)
        append('\n').append(stationLink(station))
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, station.name)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_share)))
}

private fun copyCoordinates(context: Context, station: Station) {
    val value = String.format(Locale.US, "%.6f, %.6f", station.lat, station.lng)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(station.name, value))
    Toast.makeText(context, R.string.station_copied, Toast.LENGTH_SHORT).show()
}
