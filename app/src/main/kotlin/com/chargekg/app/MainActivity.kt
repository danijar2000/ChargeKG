package com.chargekg.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargekg.app.ui.AppRoot
import com.chargekg.app.ui.ChargeKgTheme
import com.chargekg.app.ui.MainViewModel
import com.chargekg.app.ui.configureOsmdroid

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOsmdroid(this)
        enableEdgeToEdge()

        setContent {
            val prefs by viewModel.prefs.collectAsStateWithLifecycle()
            ChargeKgTheme(mode = prefs.theme, dynamicColor = prefs.dynamicColor) {
                AppRoot(viewModel)
            }
        }

        openDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openDeepLink(intent)
    }

    /**
     * `chargekg://station/{id}` и `https://chargekg.com/s/{id}` открывают
     * карточку станции. Идентификатор может содержать двоеточие («spark:123»),
     * поэтому берётся последний сегмент пути целиком.
     */
    private fun openDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val id = when {
            data.scheme == "chargekg" -> data.lastPathSegment ?: data.host
            data.path?.startsWith("/s/") == true -> data.lastPathSegment
            else -> null
        }
        if (!id.isNullOrBlank()) viewModel.select(id)
    }
}
