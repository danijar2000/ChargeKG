package com.chargekg.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chargekg.app.R
import com.chargekg.app.data.ConnectorType
import com.chargekg.app.data.Network
import com.chargekg.app.domain.Filters
import com.chargekg.app.domain.PowerBand
import com.chargekg.app.util.NavApp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersSheet(
    filters: Filters,
    nav: NavApp,
    onFilters: (Filters) -> Unit,
    onNav: (NavApp) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.filters_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(0.7f),
                )
                if (!filters.isDefault) {
                    TextButton(onClick = { onFilters(Filters()) }) {
                        Text(stringResource(R.string.btn_reset))
                    }
                }
            }

            SectionTitle(R.string.filters_company)
            FlowRow {
                // Чипы отмечают ВКЛЮЧЁННОЕ, но хранятся исключения: новая сеть
                // появится у человека сама, а не останется невидимой навсегда.
                for (network in Network.entries) {
                    val on = network.id !in filters.networksOff
                    Chip(network.title, on) {
                        onFilters(filters.copy(networksOff = filters.networksOff.toggled(network.id, on)))
                    }
                }
            }

            SectionTitle(R.string.filters_type)
            FlowRow {
                for (type in ConnectorType.entries) {
                    val on = type.id !in filters.typesOff
                    Chip(type.title, on) {
                        onFilters(filters.copy(typesOff = filters.typesOff.toggled(type.id, on)))
                    }
                }
                val unknownOn = Filters.UNKNOWN_TYPE !in filters.typesOff
                Chip(stringResource(R.string.type_other), unknownOn) {
                    onFilters(
                        filters.copy(
                            typesOff = filters.typesOff.toggled(Filters.UNKNOWN_TYPE, unknownOn)
                        )
                    )
                }
            }

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

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.filters_only_free),
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
                Switch(
                    checked = filters.onlyFree,
                    onCheckedChange = { onFilters(filters.copy(onlyFree = it)) },
                )
            }

            SectionTitle(R.string.filters_nav)
            FlowRow {
                for (app in NavApp.entries) {
                    Chip(stringResource(app.labelRes), nav == app) { onNav(app) }
                }
            }
            Text(
                stringResource(R.string.filters_nav_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Spacer(Modifier.height(16.dp))
    Text(stringResource(res), style = MaterialTheme.typography.titleSmall)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(end = 8.dp),
    )
}

/** Переключение исключения: было включено — выключаем, и наоборот. */
private fun Set<String>.toggled(id: String, currentlyOn: Boolean): Set<String> =
    if (currentlyOn) this + id else this - id
