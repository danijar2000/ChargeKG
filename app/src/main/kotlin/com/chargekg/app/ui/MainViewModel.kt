package com.chargekg.app.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chargekg.app.container
import com.chargekg.app.data.ApiException
import com.chargekg.app.data.NearestResult
import com.chargekg.app.data.Prefs
import com.chargekg.app.data.Station
import com.chargekg.app.data.StationsState
import com.chargekg.app.data.ThemeMode
import com.chargekg.app.domain.Filters
import com.chargekg.app.util.NavApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Точка отсчёта для списка ближайших: место человека или центр карты. */
data class Anchor(val lat: Double, val lng: Double, val fromUser: Boolean)

sealed interface NearestState {
    data object Idle : NearestState
    data object Loading : NearestState
    data class Ready(val result: NearestResult) : NearestState
    /** Нет связи — объясняется своими словами, а не английским текстом OkHttp. */
    data object Offline : NearestState
    data class Failed(val message: String) : NearestState
}

class MainViewModel(
    app: Application,
    private val saved: SavedStateHandle,
) : AndroidViewModel(app) {
    private val container = app.container

    val stations: StateFlow<StationsState> = container.repository.state
    val prefs: StateFlow<Prefs> = container.settings.state

    private val _nearest = MutableStateFlow<NearestState>(NearestState.Idle)
    val nearest: StateFlow<NearestState> = _nearest.asStateFlow()

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    // Выбранная станция лежит в SavedStateHandle: после смерти процесса
    // человек возвращается к открытой карточке, а не к голой карте.
    val selected: StateFlow<String?> = saved.getStateFlow(KEY_SELECTED, null)

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
        saved[KEY_SELECTED] = id
    }

    fun station(id: String): Station? = container.repository.station(id)

    fun setUserLocation(location: Location?) {
        _userLocation.value = location
    }

    fun setFilters(filters: Filters) = container.settings.setFilters(filters)

    fun setNav(nav: NavApp) = container.settings.setNav(nav)

    fun setTheme(theme: ThemeMode) = container.settings.setTheme(theme)

    fun setDynamicColor(enabled: Boolean) = container.settings.setDynamicColor(enabled)

    /** Сохраняет выбор; применяет его пересозданием активности. */
    fun setLanguage(tag: String) = container.settings.setLanguage(tag)

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
            } catch (e: CancellationException) {
                // Запрос отменён следующим — состояние уже принадлежит тому.
                // Записать сюда Failed значило бы затереть его «Loading» или
                // ответ сообщением «StandaloneCoroutine was cancelled».
                throw e
            } catch (e: ApiException) {
                // Пояснение сервера по-русски — его и показываем.
                _nearest.value = NearestState.Failed(e.message.orEmpty())
            } catch (e: IOException) {
                _nearest.value = NearestState.Offline
            } catch (e: Exception) {
                _nearest.value = NearestState.Failed(
                    e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
                )
            }
        }
    }

    private companion object {
        const val KEY_SELECTED = "selected"
    }
}
