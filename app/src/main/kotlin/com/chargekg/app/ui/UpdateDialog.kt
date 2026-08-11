package com.chargekg.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chargekg.app.R
import com.chargekg.app.container
import com.chargekg.app.update.UpdateChecker
import com.chargekg.app.util.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateChecker.UpdateInfo) : UpdateState
    data class NeedsPermission(val info: UpdateChecker.UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateChecker.UpdateInfo, val progress: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Проверка обновления. Ошибки при автопроверке глотаются вызывающим: человек
 * не просил проверять, и сообщать ему о недоступности GitHub не за что.
 */
fun CoroutineScope.launchUpdateCheck(
    context: Context,
    force: Boolean,
    onState: (UpdateState) -> Unit,
) = launch {
    val checker = context.container.updateChecker
    try {
        val info = checker.checkForUpdate(context, force)
        onState(
            when {
                info == null && force -> UpdateState.UpToDate
                info == null -> UpdateState.Idle
                else -> UpdateState.Available(info)
            }
        )
    } catch (e: Exception) {
        onState(
            if (force) UpdateState.Failed(e.message.orEmpty().ifBlank { e::class.java.simpleName })
            else UpdateState.Idle
        )
    }
}

@Composable
fun UpdateDialog(
    state: UpdateState,
    onState: (UpdateState) -> Unit,
    checker: UpdateChecker,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (state is UpdateState.Idle) return

    val dismiss = { onState(UpdateState.Idle) }

    when (state) {
        is UpdateState.Checking -> AlertDialog(
            onDismissRequest = dismiss,
            confirmButton = {},
            text = { Text(stringResource(R.string.update_checking)) },
        )

        is UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = dismiss,
            confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.btn_ok)) } },
            text = { Text(stringResource(R.string.update_up_to_date)) },
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = dismiss,
            confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.btn_ok)) } },
            text = { Text(state.message) },
        )

        is UpdateState.NeedsPermission -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.update_needs_permission)) },
            text = { Text(stringResource(R.string.update_needs_permission_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(checker.installPermissionIntent(context))
                    // Возврат из системных настроек — отдельный переход, поэтому
                    // диалог просто закрывается: человек нажмёт «Установить» снова.
                    onState(UpdateState.Available(state.info))
                }) { Text(stringResource(R.string.update_open_permission)) }
            },
            dismissButton = {
                TextButton(onClick = dismiss) { Text(stringResource(R.string.btn_cancel)) }
            },
        )

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.update_available, state.info.version)) },
            text = {
                Column(Modifier.heightIn(max = 320.dp)) {
                    // Размер показывается всегда: обновление качают руками,
                    // возможно с мобильного интернета.
                    Text(
                        stringResource(R.string.update_size, formatBytes(context, state.info.sizeBytes)),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (state.info.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.info.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Android 8+ требует явного разрешения на установку из
                    // приложения. Без проверки установщик молча не открылся бы.
                    if (!checker.canInstall(context)) {
                        onState(UpdateState.NeedsPermission(state.info))
                        return@TextButton
                    }
                    onState(UpdateState.Downloading(state.info, ""))
                    scope.launch {
                        try {
                            checker.downloadAndInstall(context, state.info) { progress ->
                                onState(UpdateState.Downloading(state.info, progress))
                            }
                            onState(UpdateState.Idle)
                        } catch (e: Exception) {
                            onState(
                                UpdateState.Failed(
                                    e.message.orEmpty().ifBlank { e::class.java.simpleName }
                                )
                            )
                        }
                    }
                }) { Text(stringResource(R.string.update_install)) }
            },
            dismissButton = {
                TextButton(onClick = dismiss) { Text(stringResource(R.string.update_later)) }
            },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.update_available, state.info.version)) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(state.progress.ifBlank { stringResource(R.string.update_downloading_start) })
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
        )

        is UpdateState.Idle -> Unit
    }
}
