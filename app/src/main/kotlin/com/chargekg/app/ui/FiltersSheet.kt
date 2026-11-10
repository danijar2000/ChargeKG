package com.chargekg.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chargekg.app.R
import com.chargekg.app.data.ConnectorType
import com.chargekg.app.data.Network
import com.chargekg.app.domain.Filters
import com.chargekg.app.domain.PowerBand

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersSheet(
    filters: Filters,
    matchCount: Int,
    onFilters: (Filters) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.filters_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (!filters.isDefault) {
                    TextButton(onClick = { onFilters(Filters()) }) {
                        Text(stringResource(R.string.btn_reset))
                    }
                }
            }
            Text(
                stringResource(R.string.filters_hint_solo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Скорость и «только свободные» — самое частое, поэтому сверху.
            SectionTitle(R.string.filters_power)
            FlowRow {
                for (band in PowerBand.entries) {
                    val label = when (band) {
                        PowerBand.ALL -> R.string.power_all
                        PowerBand.SLOW -> R.string.power_slow
                        PowerBand.FAST -> R.string.power_fast
                        PowerBand.ULTRA -> R.string.power_ultra
                    }
                    Chip(stringResource(label), filters.power == band) {
                        onFilters(filters.copy(power = band))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filters_only_free), Modifier.weight(1f))
                Switch(
                    checked = filters.onlyFree,
                    onCheckedChange = { onFilters(filters.copy(onlyFree = it)) },
                )
            }

            val typeIds = Filters.allTypeIds
            SectionTitle(R.string.filters_type)
            FlowRow {
                Chip(stringResource(R.string.filters_all), filters.typesOff.isEmpty()) {
                    onFilters(filters.copy(typesOff = emptySet()))
                }
                for (type in ConnectorType.entries) {
                    Chip(
                        type.title,
                        selected = filters.typesOff.isNotEmpty() && type.id !in filters.typesOff,
                        icon = ConnectorIcons.forType(type),
                    ) {
                        onFilters(filters.copy(typesOff = soloToggle(filters.typesOff, type.id, typeIds)))
                    }
                }
                Chip(
                    stringResource(R.string.type_other),
                    selected = filters.typesOff.isNotEmpty() && Filters.UNKNOWN_TYPE !in filters.typesOff,
                ) {
                    onFilters(
                        filters.copy(typesOff = soloToggle(filters.typesOff, Filters.UNKNOWN_TYPE, typeIds))
                    )
                }
            }

            val netIds = Network.entries.map { it.id }
            SectionTitle(R.string.filters_company)
            FlowRow {
                Chip(stringResource(R.string.filters_all), filters.networksOff.isEmpty()) {
                    onFilters(filters.copy(networksOff = emptySet()))
                }
                for (network in Network.entries) {
                    Chip(
                        network.title,
                        selected = filters.networksOff.isNotEmpty() && network.id !in filters.networksOff,
                    ) {
                        onFilters(filters.copy(networksOff = soloToggle(filters.networksOff, network.id, netIds)))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.filters_show, matchCount))
            }
        }
    }
}

/**
 * Нажатие на значение, когда включено «Все», оставляет ТОЛЬКО его: человеку
 * с GB/T не нужно выключать восемь чужих разъёмов по одному. Дальше нажатия
 * добавляют и убирают значения. Убрали последнее — снова «Все», а не пустая
 * карта.
 *
 * Хранятся по-прежнему исключения: новая сеть или тип при «Все» появится сам.
 */
internal fun soloToggle(off: Set<String>, id: String, all: List<String>): Set<String> {
    if (off.isEmpty()) return all.filter { it != id }.toSet()
    val next = if (id in off) off - id else off + id
    return if (all.all { it in next }) emptySet() else next
}

@Composable
private fun SectionTitle(res: Int) {
    Spacer(Modifier.height(16.dp))
    Text(stringResource(res), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        // Выбранное отмечено галочкой, а не только оттенком фона: заливку
        // на солнце не видно, галочку — видно.
        leadingIcon = when {
            selected -> {
                { Icon(AppIcons.Check, contentDescription = null, Modifier.size(18.dp)) }
            }
            icon != null -> {
                { Icon(icon, contentDescription = null, Modifier.size(20.dp)) }
            }
            else -> null
        },
        modifier = Modifier.padding(end = 8.dp),
    )
}
