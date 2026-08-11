package com.chargekg.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Откуда взяты станции, лежащие сейчас в [StationsState.stations]. */
enum class DataOrigin { NONE, CACHE, NETWORK }

/**
 * Что именно не получилось. Нужно, чтобы не показывать человеку сырой текст
 * исключения: он приходит от OkHttp по-английски и при выбранном русском
 * выглядит как чужой мусор в интерфейсе.
 */
sealed interface LoadError {
    /** Нет связи — самый частый случай, объясняется своими словами. */
    data object Offline : LoadError

    /** Всё остальное: ответ сервера или разбор. Текст осмысленный. */
    data class Message(val text: String) : LoadError
}

data class StationsState(
    val stations: List<Station> = emptyList(),
    val origin: DataOrigin = DataOrigin.NONE,
    val loading: Boolean = false,
    /** Последняя ошибка обновления; null — ошибок не было. */
    val error: LoadError? = null,
) {
    /** Сколько станций сервер пометил недостоверными по занятости. */
    val staleCount: Int get() = stations.count { it.unknownBusy }
}

/**
 * Единственный источник станций для интерфейса.
 *
 * Порядок работы — сначала кэш, потом сеть: карта рисуется мгновенно, даже
 * если связи нет вовсе. Периодического опроса нет: приложение открывают на
 * минуту-две, и таймер только жёг бы трафик.
 */
class StationRepository(
    private val api: ChargeApi,
    private val cache: StationCache,
) {
    private val _state = MutableStateFlow(StationsState())
    val state: StateFlow<StationsState> = _state.asStateFlow()

    /** Показать сохранённое. Вызывается один раз при старте, до сети. */
    suspend fun loadFromCache() {
        if (_state.value.stations.isNotEmpty()) return
        val body = cache.read() ?: return
        val parsed = runCatching { parseStations(body) }.getOrNull() ?: return
        _state.value = _state.value.copy(
            // Занятость из кэша обнуляется принудительно. Показать вчерашние
            // «3 свободно» как сегодняшние — соврать ровно там, ради чего
            // карту и открывают.
            stations = parsed.stations.map { it.withUnknownBusy() },
            origin = DataOrigin.CACHE,
        )
    }

    /**
     * Сходить за свежими данными. При 304 и при ошибке сети то, что уже
     * показано, остаётся на экране.
     */
    suspend fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        try {
            when (val fetch = api.stations(cache.etag)) {
                is StationsFetch.NotModified -> {
                    // Тела нет, но сервер подтвердил, что слепок тот же — значит
                    // занятость В КЭШЕ актуальна. Перечитать его обязательно:
                    // на экране сейчас может лежать копия с намеренно обнулённой
                    // занятостью, и одной сменой origin её не воскресить.
                    val stations = if (_state.value.origin == DataOrigin.NETWORK &&
                        _state.value.stations.isNotEmpty()
                    ) {
                        _state.value.stations
                    } else {
                        cache.read()?.let { parseStations(it).stations } ?: emptyList()
                    }
                    _state.value = StationsState(stations, DataOrigin.NETWORK, loading = false)
                }

                is StationsFetch.Fresh -> {
                    val parsed = parseStations(fetch.body)
                    cache.write(fetch.body, fetch.etag)
                    _state.value = StationsState(parsed.stations, DataOrigin.NETWORK, loading = false)
                }
            }
        } catch (e: Exception) {
            // Ловим и IOException, и JSONException: битый ответ для человека
            // ничем не отличается от отсутствия связи — показанное остаётся.
            _state.value = _state.value.copy(loading = false, error = toLoadError(e))
        }
    }

    fun station(id: String): Station? = _state.value.stations.firstOrNull { it.id == id }

    private fun toLoadError(e: Exception): LoadError = when (e) {
        // ApiException несёт пояснение сервера по-русски — его и показываем.
        is ApiException -> LoadError.Message(e.message.orEmpty())
        is UnknownHostException, is ConnectException, is SocketTimeoutException -> LoadError.Offline
        else -> LoadError.Message(e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName)
    }
}
