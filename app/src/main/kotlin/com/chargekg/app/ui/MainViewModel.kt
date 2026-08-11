package com.chargekg.app.ui

import android.app.Application
import android.location.Location
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chargekg.app.container
import com.chargekg.app.data.NearestResult
import com.chargekg.app.data.Prefs
import com.chargekg.app.data.Station
import com.chargekg.app.data.StationsState
import com.chargekg.app.data.ThemeMode
import com.chargekg.app.domain.Filters
import com.chargekg.app.util.NavApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Точка отсчёта для списка ближайших: место человека или центр карты. */
data class Anchor(val lat: Double, val lng: Double, val fromUser: Boolean)

sealed interface NearestState {
    data object Idle : NearestState
    data object Loading : NearestState
    data class Ready(val result: NearestResult) : NearestState
    data class Failed(val message: String) : NearestState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.container

    val stations: StateFlow<StationsState> = container.repository.state
    val prefs: StateFlow<Prefs> = container.settings.state

    private val _nearest = MutableStateFlow<NearestState>(NearestState.Idle)
    val nearest: StateFlow<NearestState> = _nearest.asStateFlow()

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    /** Предыдущий запрос ближайших отменяется: иначе ответы приходят вразнобой. */
    private var nearestJob: Job? = null

    init {
        viewModelScope.launch {
            container.repository.loadFromCache()
            container.repository.refresh()
        }
    }

    fun refresh() = viewModelScope.launch { container.repository.refresh() }

    fun select(id: String?) {
        _selected.value = id
    }

    fun station(id: String): Station? = container.repository.station(id)

    fun setUserLocation(location: Location?) {
        _userLocation.value = location
    }

    fun setFilters(filters: Filters) = container.settings.setFilters(filters)

    fun setNav(nav: NavApp) = container.settings.setNav(nav)

    fun setTheme(theme: ThemeMode) = container.settings.setTheme(theme)

    fun setDynamicColor(enabled: Boolean) = container.settings.setDynamicColor(enabled)

    /**
     * Язык применяется сразу через per-app language. Системная локаль при этом
     * не учитывается никогда: выбор языка — за человеком.
     */
    fun setLanguage(tag: String) {
        container.settings.setLanguage(tag)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** Станции, прошедшие фильтр. Та же функция используется для запроса списка. */
    fun visible(state: StationsState, filters: Filters): List<Station> =
        state.stations.filter { filters.matches(it) }

    fun loadNearest(anchor: Anchor) {
        val filters = prefs.value.filters
        nearestJob?.cancel()
        // Пустой network= сервер понимает как «все сети»; при полностью
        // отключённых фильтрах список показал бы станции, которых нет на карте.
        if (filters.excludesEverything) {
            _nearest.value = NearestState.Ready(NearestResult(routed = false, stations = emptyList()))
            return
        }
        _nearest.value = NearestState.Loading
        nearestJob = viewModelScope.launch {
            try {
                val result = container.api.nearest(anchor.lat, anchor.lng, filters)
                _nearest.value = NearestState.Ready(result)
            } catch (e: Exception) {
                _nearest.value = NearestState.Failed(
                    e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
                )
            }
        }
    }
}
