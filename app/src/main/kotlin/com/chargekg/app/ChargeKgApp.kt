package com.chargekg.app

import android.app.Application
import android.content.Context
import android.content.res.Resources
import com.chargekg.app.data.ChargeApi
import com.chargekg.app.data.Settings
import com.chargekg.app.data.StationCache
import com.chargekg.app.data.StationRepository
import com.chargekg.app.update.UpdateChecker
import com.chargekg.app.util.withLocale
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Ручной контейнер зависимостей.
 *
 * Hilt/Dagger здесь не нужны: шесть экранов и пять объектов. Кодогенерация
 * стоила бы места в APK и времени сборки, не давая ничего взамен.
 */
class AppContainer(context: Context) {
    val settings: Settings by lazy { Settings(context) }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Приложение открывают в дороге: лучше быстро показать кэш и
            // ошибку, чем держать человека на пустом экране полминуты.
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // Свой кэш ответов не нужен: слепок станций мы храним файлом сами,
            // а условные запросы делаем через собственный ETag.
            .build()
    }

    val api: ChargeApi by lazy { ChargeApi(http, BuildConfig.API_BASE_URL) }

    val repository: StationRepository by lazy { StationRepository(api, StationCache(context)) }

    val updateChecker: UpdateChecker by lazy { UpdateChecker(http) }
}

class ChargeKgApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Locale.setDefault внутри withLocale задаёт язык и для строк, которые
        // берутся вне активности.
        withLocale(language())
    }

    /**
     * Выбор человека, если он сделан; иначе язык телефона, если приложение его
     * знает; иначе английский.
     *
     * Английский запасным выбран сознательно: незнакомая локаль — это чаще
     * всего приезжий, которому русский или кыргызский не помогут.
     */
    fun language(): String = container.settings.current.language.ifEmpty { systemLanguage() }

    companion object {
        val SUPPORTED = listOf("ru", "ky", "en")

        /** Язык, на котором приложение заговорит без всяких настроек. */
        const val FALLBACK_LANGUAGE = "en"

        /**
         * Системная локаль читается из ресурсов конфигурации, а не из
         * Locale.getDefault(): withLocale() подменяет её при запуске, и на
         * второй заход getDefault() вернул бы уже наш собственный выбор.
         */
        fun systemLanguage(): String = languageOf(
            Resources.getSystem().configuration.locales[0].language
        )

        fun languageOf(tag: String): String =
            SUPPORTED.firstOrNull { it == tag.lowercase() } ?: FALLBACK_LANGUAGE
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ChargeKgApp).container
