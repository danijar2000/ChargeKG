plugins {
    alias(libs.plugins.android.application) apply false
    // Kotlin встроен в AGP 9 и в модуле не применяется. Объявление здесь только
    // фиксирует версию Kotlin-плагина: с ней обязан совпадать плагин Compose.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
