package com.chargekg.app.util

import android.content.Context
import com.chargekg.app.R
import java.util.Locale

/** Разделитель дробной части: запятая для ru и ky, точка для en — как на сайте. */
private fun decimalSeparator(context: Context): Char =
    if (context.resources.configuration.locales[0].language == "en") '.' else ','

private fun localized(context: Context, value: String): String =
    value.replace('.', decimalSeparator(context))

fun formatDistance(context: Context, meters: Int): String = when {
    meters < 1000 -> "$meters ${context.getString(R.string.unit_m)}"
    meters < 10_000 -> localized(
        context,
        String.format(Locale.US, "%.1f", meters / 1000.0),
    ) + " " + context.getString(R.string.unit_km)
    else -> "${meters / 1000} ${context.getString(R.string.unit_km)}"
}

fun formatDuration(context: Context, seconds: Int): String {
    if (seconds < 60) return context.getString(R.string.time_under_minute)
    val minutes = seconds / 60
    if (minutes < 60) return "$minutes ${context.getString(R.string.unit_min)}"
    val hours = minutes / 60
    val rest = minutes % 60
    val h = "$hours ${context.getString(R.string.unit_hour)}"
    return if (rest == 0) h else "$h $rest ${context.getString(R.string.unit_min)}"
}

fun formatPower(context: Context, kw: Double): String {
    val value = if (kw % 1.0 == 0.0) kw.toInt().toString()
    else localized(context, String.format(Locale.US, "%.1f", kw))
    return "$value ${context.getString(R.string.unit_kw)}"
}

/** Размер загрузки обновления: человек должен видеть, сколько качать. */
fun formatBytes(context: Context, bytes: Long): String {
    if (bytes <= 0) return "—"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1) localized(context, String.format(Locale.US, "%.1f", mb)) + " MB"
    else "${bytes / 1024} KB"
}
