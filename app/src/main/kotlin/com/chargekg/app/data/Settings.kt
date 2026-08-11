package com.chargekg.app.data

import android.content.Context
import com.chargekg.app.domain.Filters
import com.chargekg.app.domain.PowerBand
import com.chargekg.app.util.NavApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val id: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun of(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

data class Prefs(
    val filters: Filters = Filters(),
    val nav: NavApp = NavApp.DEFAULT,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Пусто — язык ещё не выбран, работает значение по умолчанию (русский). */
    val language: String = "",
)

/**
 * Настройки в SharedPreferences. DataStore здесь не нужен: значений десяток,
 * пишутся они по нажатию, а корутинный Proto-стек стоил бы места в APK.
 */
class Settings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("chargekg", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Prefs> = _state.asStateFlow()

    val current: Prefs get() = _state.value

    private fun load(): Prefs = Prefs(
        filters = Filters(
            networksOff = prefs.getStringSet(KEY_NETWORKS_OFF, emptySet()).orEmpty(),
            typesOff = prefs.getStringSet(KEY_TYPES_OFF, emptySet()).orEmpty(),
            power = PowerBand.of(prefs.getString(KEY_POWER, null)),
            onlyFree = prefs.getBoolean(KEY_ONLY_FREE, false),
        ),
        nav = NavApp.of(prefs.getString(KEY_NAV, null)),
        theme = ThemeMode.of(prefs.getString(KEY_THEME, null)),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
        language = prefs.getString(KEY_LANGUAGE, "").orEmpty(),
    )

    fun setFilters(filters: Filters) {
        prefs.edit()
            // Множества копируются: SharedPreferences хранит переданный
            // экземпляр как есть, и позднейшая мутация ушла бы на диск молча.
            .putStringSet(KEY_NETWORKS_OFF, HashSet(filters.networksOff))
            .putStringSet(KEY_TYPES_OFF, HashSet(filters.typesOff))
            .putString(KEY_POWER, filters.power.id)
            .putBoolean(KEY_ONLY_FREE, filters.onlyFree)
            .apply()
        _state.value = _state.value.copy(filters = filters)
    }

    fun setNav(nav: NavApp) {
        prefs.edit().putString(KEY_NAV, nav.id).apply()
        _state.value = _state.value.copy(nav = nav)
    }

    fun setTheme(theme: ThemeMode) {
        prefs.edit().putString(KEY_THEME, theme.id).apply()
        _state.value = _state.value.copy(theme = theme)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
        _state.value = _state.value.copy(dynamicColor = enabled)
    }

    fun setLanguage(tag: String) {
        prefs.edit().putString(KEY_LANGUAGE, tag).apply()
        _state.value = _state.value.copy(language = tag)
    }

    private companion object {
        const val KEY_NETWORKS_OFF = "networks_off"
        const val KEY_TYPES_OFF = "types_off"
        const val KEY_POWER = "power"
        const val KEY_ONLY_FREE = "only_free"
        const val KEY_NAV = "nav"
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_LANGUAGE = "language"
    }
}
