package com.chargekg.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Доменные модели. Отдельного слоя DTO нет намеренно: полей мало, а лишний
 * слой стоил бы кода и размера ради перекладывания одного и того же.
 */

enum class Network(val id: String, val title: String, val pkg: String, val scheme: String?) {
    SPARK("spark", "SPARK", "kg.spark.main", "spark"),
    WEWAY("weway", "We way", "tech.weway.app.kg", null),
    EVION("evion", "EVION", "kg.evion.app", null),
    CHARGE24("charge24", "Charge24", "com.pay24.charge24", "charge24");

    companion object {
        fun of(id: String): Network? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Типы разъёмов в том же порядке, что в перечислении сервера. UNKNOWN не
 * входит: для него показывается [Connector.rawType].
 */
enum class ConnectorType(val id: String, val title: String) {
    GBT_DC("GBT_DC", "GB/T DC"),
    GBT_AC("GBT_AC", "GB/T AC"),
    CCS2("CCS2", "CCS2"),
    CCS1("CCS1", "CCS1"),
    CHADEMO("CHADEMO", "CHAdeMO"),
    TESLA("TESLA", "Tesla"),
    TYPE2_AC("TYPE2_AC", "Type 2"),
    TYPE1_AC("TYPE1_AC", "Type 1");

    companion object {
        fun of(id: String): ConnectorType? = entries.firstOrNull { it.id == id }
    }
}

data class Connector(
    val id: String,
    val type: ConnectorType?,
    val rawType: String,
    val power: Double,
    val price: Double?,
    val priceText: String,
)

data class MergedPart(
    val id: String,
    val name: String,
    val address: String,
    val total: Int,
)

data class Station(
    val id: String,
    val network: Network?,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val promotions: List<String>,
    /** null — занятость недостоверна. Не подменять нулём: это разные вещи. */
    val free: Int?,
    val busy: Int?,
    val offline: Int?,
    val total: Int,
    val statusStale: Boolean,
    val connectors: List<Connector>,
    val mergedCount: Int,
    val merged: List<MergedPart>,
    /** Метры по прямой. Приходит только из /v1/nearest. */
    val airM: Int? = null,
    /** Метры по дорогам. Нет, если маршрутизация недоступна. */
    val roadM: Int? = null,
    /** Секунды в пути по свободной дороге. */
    val roadS: Int? = null,
) {
    /** Максимальная мощность станции — по ней работает фильтр скорости. */
    val maxPower: Double get() = connectors.maxOfOrNull { it.power } ?: 0.0

    /**
     * Занятость неизвестна. Станцию при этом всё равно показываем: адрес,
     * разъёмы и цены не протухают, а спрятать существующую зарядку вреднее,
     * чем не показать число свободных портов.
     */
    val unknownBusy: Boolean get() = statusStale || free == null

    /** Копия с заведомо неизвестной занятостью — для отдачи из кэша без сети. */
    fun withUnknownBusy(): Station =
        copy(free = null, busy = null, offline = null, statusStale = true)

    /** Наложить свежую занятость, оставив справочные поля нетронутыми. */
    fun withStatus(s: StationStatus): Station =
        copy(free = s.free, busy = s.busy, offline = s.offline, statusStale = s.statusStale)
}

/**
 * Занятость одной станции из `/v1/status`.
 *
 * free/busy/offline остаются обнуляемыми: null означает «неизвестно», а не
 * «нет свободных». total здесь — признак расхождения: изменился, значит у
 * станции поменялся состав портов и пора за полным списком.
 */
data class StationStatus(
    val id: String,
    val free: Int?,
    val busy: Int?,
    val offline: Int?,
    val total: Int,
    val statusStale: Boolean,
)

data class StationsSnapshot(
    val generatedAt: String,
    val stations: List<Station>,
)

data class NearestResult(
    /** false — расстояния по прямой, маршрутизация была недоступна. */
    val routed: Boolean,
    val stations: List<Station>,
)

data class NetworkMeta(
    val network: String,
    val stations: Int,
    val stale: Boolean,
    val lastError: String,
)

data class Meta(
    val totalStations: Int,
    val networks: List<NetworkMeta>,
)

// --- Разбор JSON -----------------------------------------------------------
//
// Вручную через org.json: он входит в Android, а Moshi/kotlinx-serialization
// стоили бы сотни килобайт ради четырёх моделей.

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key)

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

private fun JSONArray?.strings(): List<String> {
    if (this == null || length() == 0) return emptyList()
    return (0 until length()).map { optString(it) }
}

fun parseConnector(o: JSONObject): Connector = Connector(
    id = o.optString("id"),
    type = ConnectorType.of(o.optString("type")),
    rawType = o.optString("raw_type"),
    power = o.optDouble("power", 0.0),
    price = o.optDoubleOrNull("price"),
    priceText = o.optString("price_text"),
)

fun parseStation(o: JSONObject): Station {
    val connectors = o.optJSONArray("connectors").let { arr ->
        if (arr == null) emptyList() else (0 until arr.length()).map { parseConnector(arr.getJSONObject(it)) }
    }
    val merged = o.optJSONArray("merged").let { arr ->
        if (arr == null) emptyList() else (0 until arr.length()).map {
            val m = arr.getJSONObject(it)
            MergedPart(
                id = m.optString("id"),
                name = m.optString("name"),
                address = m.optString("address"),
                total = m.optInt("total"),
            )
        }
    }
    return Station(
        id = o.optString("id"),
        network = Network.of(o.optString("network")),
        name = o.optString("name"),
        address = o.optString("address"),
        lat = o.optDouble("lat"),
        lng = o.optDouble("lng"),
        promotions = o.optJSONArray("promotions").strings(),
        free = o.optIntOrNull("free"),
        busy = o.optIntOrNull("busy"),
        offline = o.optIntOrNull("offline"),
        total = o.optInt("total"),
        statusStale = o.optBoolean("status_stale"),
        connectors = connectors,
        mergedCount = o.optInt("merged_count", 1),
        merged = merged,
        airM = if (o.has("air_m")) o.optInt("air_m") else null,
        roadM = if (o.has("road_m")) o.optInt("road_m") else null,
        roadS = if (o.has("road_s")) o.optInt("road_s") else null,
    )
}

fun parseStations(body: String): StationsSnapshot {
    val root = JSONObject(body)
    val arr = root.optJSONArray("stations") ?: JSONArray()
    return StationsSnapshot(
        generatedAt = root.optString("generated_at"),
        stations = (0 until arr.length()).map { parseStation(arr.getJSONObject(it)) },
    )
}

fun parseStatus(body: String): List<StationStatus> {
    val arr = JSONObject(body).optJSONArray("stations") ?: JSONArray()
    return (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        StationStatus(
            id = o.optString("id"),
            free = o.optIntOrNull("free"),
            busy = o.optIntOrNull("busy"),
            offline = o.optIntOrNull("offline"),
            total = o.optInt("total"),
            statusStale = o.optBoolean("status_stale"),
        )
    }
}

fun parseNearest(body: String): NearestResult {
    val root = JSONObject(body)
    val arr = root.optJSONArray("stations") ?: JSONArray()
    return NearestResult(
        routed = root.optBoolean("routed"),
        stations = (0 until arr.length()).map { parseStation(arr.getJSONObject(it)) },
    )
}

fun parseMeta(body: String): Meta {
    val root = JSONObject(body)
    val arr = root.optJSONArray("networks") ?: JSONArray()
    return Meta(
        totalStations = root.optInt("total_stations"),
        networks = (0 until arr.length()).map {
            val n = arr.getJSONObject(it)
            NetworkMeta(
                network = n.optString("network"),
                stations = n.optInt("stations"),
                stale = n.optBoolean("stale"),
                lastError = n.optString("last_error"),
            )
        },
    )
}

/** Код ошибки из тела ответа сервера; пустая строка, если тело не разобрать. */
fun parseErrorMessage(body: String): String = try {
    val o = JSONObject(body)
    o.optString("message").ifEmpty { o.optString("error") }
} catch (_: Exception) {
    ""
}
