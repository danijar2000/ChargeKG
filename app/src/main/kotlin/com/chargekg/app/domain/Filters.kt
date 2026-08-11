package com.chargekg.app.domain

import com.chargekg.app.data.ConnectorType
import com.chargekg.app.data.Network
import com.chargekg.app.data.Station

enum class PowerBand(val id: String) {
    ALL("all"),
    SLOW("slow"),
    FAST("fast"),
    ULTRA("ultra");

    companion object {
        fun of(id: String?): PowerBand = entries.firstOrNull { it.id == id } ?: ALL
    }
}

/**
 * Настройки фильтрации.
 *
 * Хранятся ИСКЛЮЧЕНИЯ, а не выбранное. Если хранить выбранное, то при
 * появлении новой сети или нового типа разъёма человек не увидит их никогда —
 * молча, без единого признака поломки. То же решение принято на сайте.
 */
data class Filters(
    val networksOff: Set<String> = emptySet(),
    val typesOff: Set<String> = emptySet(),
    val power: PowerBand = PowerBand.ALL,
    val onlyFree: Boolean = false,
) {
    val isDefault: Boolean
        get() = networksOff.isEmpty() && typesOff.isEmpty() &&
            power == PowerBand.ALL && !onlyFree

    /**
     * Единственная реализация правил отбора. Она же переводится в параметры
     * `/v1/nearest` через [toQuery] — так карта и список не могут разойтись.
     */
    fun matches(station: Station): Boolean {
        if (station.network != null && station.network.id in networksOff) return false
        // Занятость неизвестна — станция не «свободна». null != 0, но для
        // фильтра «только свободные» обе ситуации означают «не показывать».
        if (onlyFree && (station.free ?: 0) <= 0) return false
        // Разъём и мощность проверяются на ОДНОМ коннекторе: иначе выдача
        // предложила бы станцию, где нужный разъём медленный, а быстрый — чужой.
        return station.connectors.any { c ->
            val typeId = c.type?.id ?: UNKNOWN_TYPE
            typeId !in typesOff && powerFits(c.power)
        }
    }

    /** Пороги те же, что на сайте и на сервере. */
    fun powerFits(kw: Double): Boolean = when (power) {
        PowerBand.SLOW -> kw < 22
        PowerBand.FAST -> kw >= 22 && kw < 100
        PowerBand.ULTRA -> kw >= 100
        PowerBand.ALL -> true
    }

    /** Сети, которые надо запросить. Пустое множество — показывать нечего. */
    val networksOn: List<String> get() = Network.entries.map { it.id }.filter { it !in networksOff }

    val typesOn: List<String> get() = allTypeIds.filter { it !in typesOff }

    /**
     * Ничего не пройдёт фильтр — запрос к серверу бессмыслен. Проверять это
     * обязательно: пустой `network=` сервер понимает как «все сети», и список
     * показал бы станции, которых на карте нет.
     */
    val excludesEverything: Boolean get() = networksOn.isEmpty() || typesOn.isEmpty()

    fun toQuery(): Map<String, String> {
        val q = LinkedHashMap<String, String>(4)
        if (networksOff.isNotEmpty()) q["network"] = networksOn.joinToString(",")
        if (typesOff.isNotEmpty()) q["type"] = typesOn.joinToString(",")
        if (power != PowerBand.ALL) q["power"] = power.id
        if (onlyFree) q["free"] = "1"
        return q
    }

    companion object {
        /** Сервер отдаёт этот тип, когда не смог опознать разъём. */
        const val UNKNOWN_TYPE = "UNKNOWN"

        val allTypeIds: List<String> = ConnectorType.entries.map { it.id } + UNKNOWN_TYPE
    }
}
