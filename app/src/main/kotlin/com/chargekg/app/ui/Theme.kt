package com.chargekg.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.chargekg.app.data.ThemeMode

/** Цвета статусов взяты с сайта: они же используются в пинах и подписях. */
object StatusColors {
    val free = Color(0xFF16A34A)
    val busy = Color(0xFFDC2626)
    val offline = Color(0xFF9CA3AF)
    val unknown = Color(0xFF9CA3AF)
}

private val Accent = Color(0xFF2F6FED)

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF4A6194),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9FC0FF),
    onPrimary = Color(0xFF00306B),
    secondary = Color(0xFFB9C7EA),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
)

@Composable
fun ChargeKgTheme(
    mode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
