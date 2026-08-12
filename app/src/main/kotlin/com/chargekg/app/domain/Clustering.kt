package com.chargekg.app.domain

import com.chargekg.app.data.Station
import kotlin.math.cos
import kotlin.math.floor

/**
 * Группа станций, показываемая на карте одной меткой.
 *
 * Это ЭКРАННАЯ группировка, она никак не связана со склейкой на сервере
 * (`merged_count`): там объединяются станции одной сети, стоящие в пределах
 * 15 метров, и такая точка приходит уже единой. Здесь же в кучу попадают
 * соседи по видимой области, в том числе из разных сетей — только чтобы
 * центр Бишкека не превращался в кашу из перекрытых меток.
 */
data class Cluster(
    val stations: List<Station>,
    val lat: Double,
    val lng: Double,
) {
    val single: Station? get() = stations.singleOrNull()

    /**
     * Идентификатор берётся наименьшим среди входящих: он не должен зависеть
     * от порядка выборки, иначе метка «прыгала» бы между перерисовками.
     */
    val id: String get() = stations.minOf { it.id }

    val totalPorts: Int get() = stations.sumOf { it.total }

    /** Занятость неизвестна, если её нет хотя бы у одной станции группы. */
    val unknownBusy: Boolean get() = stations.any { it.unknownBusy }

    val free: Int get() = stations.sumOf { it.free ?: 0 }
    val busy: Int get() = stations.sumOf { it.busy ?: 0 }
}

/** Ниже этого зума метки заведомо налезают друг на друга. */
const val CLUSTER_MAX_ZOOM = 15.0

/**
 * Сводит станции в группы по географической сетке, шаг которой подобран под
 * текущий зум.
 *
 * Сетка географическая, а не экранная, намеренно: при прокрутке карты
 * состав групп не меняется, поэтому метки не пересобираются и не «прыгают»
 * под пальцем. Пересчёт нужен только при смене зума.
 */
fun clusterStations(
    stations: List<Station>,
    zoom: Double,
    cellPx: Double = 72.0,
    density: Float = 1f,
): List<Cluster> {
    if (stations.isEmpty()) return emptyList()
    if (zoom >= CLUSTER_MAX_ZOOM) {
        return stations.map { Cluster(listOf(it), it.lat, it.lng) }
    }

    // Размер тайла — 256 точек, и на зуме z по долготе он покрывает
    // 360 / 2^z градусов.
    val degPerPixel = 360.0 / (256.0 * Math.pow(2.0, zoom))
    val cellLng = cellPx * density * degPerPixel
    // В проекции Меркатора градус широты «короче» градуса долготы в cos(lat)
    // раз. Широта берётся одна на всю страну: Кыргызстан по ней узкий, и
    // поправка на каждую станцию только сделала бы ячейки разными по размеру.
    val cellLat = cellLng * cos(Math.toRadians(REFERENCE_LAT))

    val buckets = LinkedHashMap<Long, MutableList<Station>>()
    for (station in stations) {
        val row = floor(station.lat / cellLat).toLong()
        val col = floor(station.lng / cellLng).toLong()
        buckets.getOrPut(row * 1_000_003L + col) { ArrayList(4) }.add(station)
    }

    return buckets.values.map { group ->
        if (group.size == 1) {
            val only = group[0]
            Cluster(group, only.lat, only.lng)
        } else {
            Cluster(
                stations = group,
                lat = group.sumOf { it.lat } / group.size,
                lng = group.sumOf { it.lng } / group.size,
            )
        }
    }
}

/** Широта Бишкека: вокруг него сосредоточена почти вся выдача. */
private const val REFERENCE_LAT = 42.87
