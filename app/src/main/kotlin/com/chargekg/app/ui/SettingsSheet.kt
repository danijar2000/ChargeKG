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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chargekg.app.R
import com.chargekg.app.ChargeKgApp
import com.chargekg.app.container
import com.chargekg.app.data.Prefs
import com.chargekg.app.data.ThemeMode
import com.chargekg.app.update.UpdateChecker

private val LANGUAGES = listOf("ru" to "Русский", "ky" to "Кыргызча", "en" to "English")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    viewModel: MainViewModel,
    prefs: Prefs,
    onAbout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var autoCheck by remember { mutableStateOf(UpdateChecker.isAutoCheckEnabled(context)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
            FlowRow {
                for ((tag, title) in LANGUAGES) {
                    // Пока выбор не сделан, подсвечивается фактически
                    // применённый язык, а не «первый в списке».
                    val selected = prefs.language.ifEmpty { ChargeKgApp.systemLanguage() } == tag
                    FilterChip(
                        selected = selected,
                        onClick = {
                            viewModel.setLanguage(tag)
                            // Локаль подменяется в attachBaseContext, поэтому
                            // новый язык подхватывается пересозданием активности.
                            (context as? ComponentActivity)?.recreate()
                        },
                        label = { Text(title) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
            FlowRow {
                for (mode in ThemeMode.entries) {
                    val label = when (mode) {
                        ThemeMode.SYSTEM -> R.string.theme_system
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    }
                    FilterChip(
                        selected = prefs.theme == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                hint = stringResource(R.string.settings_dynamic_color_hint),
                checked = prefs.dynamicColor,
                onChange = viewModel::setDynamicColor,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.update_section), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = stringResource(R.string.update_auto_check),
                hint = null,
                checked = autoCheck,
                onChange = {
                    autoCheck = it
                    UpdateChecker.setAutoCheckEnabled(context, it)
                },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    updateState = UpdateState.Checking
                    scope.launchUpdateCheck(context, force = true) { updateState = it }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.update_check_now))
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            TextButton(onClick = onAbout) {
                Text(stringResource(R.string.about_title))
            }
        }
    }

    UpdateDialog(
        state = updateState,
        onState = { updateState = it },
        checker = context.container.updateChecker,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    hint: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.fillMaxWidth(0.8f)) {
            Text(title)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
