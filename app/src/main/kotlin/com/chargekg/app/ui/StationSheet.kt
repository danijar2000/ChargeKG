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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.chargekg.app.util.NetworkAppResult
import com.chargekg.app.util.formatDistance
import com.chargekg.app.util.formatDuration
import com.chargekg.app.util.formatPower
import com.chargekg.app.util.openNetworkApp
import com.chargekg.app.util.openRoute
import java.util.Locale

/** Ссылка на станцию: она же уходит в «поделиться» и открывается deep link'ом. */
fun stationLink(station: Station) = "https://chargekg.com/s/${station.id}"

/**
 * Карточка станции. Порядок — по тому, что человек ищет: есть ли свободное,
 * подходит ли разъём и почём, как доехать. «Маршрут» закреплён внизу и виден
 * всегда — раньше на больших площадках до него приходилось листать полсотни
 * строк.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSheet(station: Station, nav: NavApp, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Без промежуточного положения: в нём низ панели уходит за экран вместе
    // с закреплённой кнопкой маршрута. Высота и так по содержимому.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMerged by rememberSaveable(station.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
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
                Spacer(Modifier.height(4.dp))
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

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))

            // Одинаковые разъёмы сведены в одну строку «× N»: у площадки на
            // 21 стойку раньше было 42 строки одного и того же GB/T.
            for (group in groupConnectors(station.connectors)) {
                ConnectorRow(group)
            }

            // Состав площадки нужен редко, поэтому свёрнут: раньше этот
            // список целиком занимал первый экран карточки.
            if (station.mergedCount > 1) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { showMerged = !showMerged },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        stringResource(R.string.station_merged, station.mergedCount) + " · " +
                            stringResource(
                                if (showMerged) R.string.station_merged_hide else R.string.station_merged_show
                            ),
                    )
                }
                if (showMerged) {
                    for (part in station.merged) {
                        Text(
                            "• ${part.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
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
                            message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(AppIcons.OpenApp, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(it.title, maxLines = 1)
                    }
                }
                IconButton(onClick = { shareStation(context, station) }) {
                    Icon(AppIcons.Share, contentDescription = stringResource(R.string.btn_share))
                }
                IconButton(onClick = { copyCoordinates(context, station) }) {
                    Icon(AppIcons.Copy, contentDescription = stringResource(R.string.btn_copy))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (!openRoute(context, station, nav)) {
                    Toast.makeText(context, R.string.station_no_nav, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 20.dp)
                .height(52.dp),
        ) {
            Icon(AppIcons.Route, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.station_route), style = MaterialTheme.typography.titleMedium)
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
    // Раньше было «8 из 42», и 26 мёртвых портов читались как занятые.
    // Свободные, занятые и не на связи — разные решения для водителя.
    val free = station.free ?: 0
    val busy = station.busy ?: 0
    val offline = station.offline ?: (station.total - free - busy).coerceAtLeast(0)
    Text(
        stringResource(R.string.station_status_line, free, busy, offline),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (free > 0) StatusColors.free else StatusColors.busy,
    )
}

/** Разъёмы одной станции с одинаковыми типом, мощностью и ценой. */
internal data class ConnectorGroup(val sample: Connector, val count: Int)

internal fun groupConnectors(connectors: List<Connector>): List<ConnectorGroup> =
    connectors
        .groupBy { Triple(it.type?.id ?: it.rawType, it.power, it.priceText) }
        .values
        .map { ConnectorGroup(it.first(), it.size) }
        // Самые мощные сверху: их и ищут.
        .sortedByDescending { it.sample.power }

@Composable
private fun ConnectorRow(group: ConnectorGroup) {
    val context = LocalContext.current
    val connector = group.sample
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
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
            Spacer(Modifier.size(12.dp))
        }
        Column(Modifier.weight(1f)) {
            // Когда сервер не смог опознать разъём, показывается исходное
            // название сети: «прочее» без пояснения бесполезно.
            val title = connector.type?.title
                ?: connector.rawType.ifBlank { stringResource(R.string.type_other) }
            Text(
                "$title · ${formatPower(context, connector.power)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                connector.priceText.ifBlank { stringResource(R.string.station_price_unknown) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (group.count > 1) {
            Text(
                "× ${group.count}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
