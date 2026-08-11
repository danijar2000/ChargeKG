package com.chargekg.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.chargekg.app.data.ChargeApi
import com.chargekg.app.data.Settings
import com.chargekg.app.data.StationCache
import com.chargekg.app.data.StationRepository
import com.chargekg.app.update.UpdateChecker
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
        applySavedLanguage()
    }

    /**
     * Язык интерфейса берётся ТОЛЬКО из настроек приложения; системная локаль
     * намеренно не учитывается. В Кыргызстане английский язык телефона сплошь
     * и рядом стоит у тех, кто читает по-русски. Пока выбор не сделан,
     * работает язык по умолчанию — русский.
     */
    private fun applySavedLanguage() {
        val tag = container.settings.current.language
        if (tag.isNotEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ChargeKgApp).container
