package com.chargekg.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chargekg.app.BuildConfig
import com.chargekg.app.R

const val REPO_URL = "https://github.com/danijar2000/ChargeKG"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.about_intro), style = MaterialTheme.typography.bodyMedium)

            Section(R.string.about_source_h, R.string.about_source1, R.string.about_source2)
            Section(R.string.about_pin_h, R.string.about_pin1, R.string.about_pin2)
            Section(R.string.about_price_h, R.string.about_price)

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
            }) {
                Text(stringResource(R.string.about_repo))
            }
        }
    }
}

@Composable
private fun Section(titleRes: Int, vararg bodyRes: Int) {
    Spacer(Modifier.height(20.dp))
    Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    for (res in bodyRes) {
        Text(
            stringResource(res),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}
