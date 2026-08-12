package com.chargekg.app.util

import android.content.Context
import android.os.LocaleList
import java.util.Locale

/**
 * Подмена локали для контекста.
 *
 * Язык берётся из настроек приложения, а если выбор не сделан — из системной
 * локали (см. ChargeKgApp.language()).
 *
 * Сделано подменой конфигурации, а не per-app language из AppCompat: на
 * Android 13 вызов `AppCompatDelegate.setApplicationLocales` из
 * `Application.onCreate` не доезжает до системы (`cmd locale get-app-locales`
 * показывает пустой список), и интерфейс молча остаётся на языке телефона.
 * Проверено на устройстве с en-GB. Заодно это позволило не тащить appcompat.
 */
fun Context.withLocale(tag: String): Context {
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val config = android.content.res.Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLocales(LocaleList(locale))
    return createConfigurationContext(config)
}
