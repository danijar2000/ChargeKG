package com.chargekg.app

import com.chargekg.app.data.Connector
import com.chargekg.app.data.ConnectorType
import com.chargekg.app.data.Network
import com.chargekg.app.data.Station
import com.chargekg.app.domain.Filters
import com.chargekg.app.domain.PowerBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {

    private fun connector(type: ConnectorType?, power: Double, raw: String = "") =
        Connector("c", type, raw, power, null, "")

    private fun station(
        network: Network = Network.SPARK,
        free: Int? = 1,
        total: Int = 2,
        stale: Boolean = false,
        connectors: List<Connector> = listOf(connector(ConnectorType.GBT_DC, 60.0)),
    ) = Station(
        id = "${network.id}:1",
        network = network,
        name = "test",
        address = "",
        lat = 42.0,
        lng = 74.0,
        promotions = emptyList(),
        free = free,
        busy = 0,
        offline = 0,
        total = total,
        statusStale = stale,
        connectors = connectors,
        mergedCount = 1,
        merged = emptyList(),
    )

    @Test
    fun `по умолчанию проходит всё`() {
        assertTrue(Filters().matches(station()))
        assertTrue(Filters().isDefault)
    }

    @Test
    fun `исключённая сеть отсеивается`() {
        val filters = Filters(networksOff = setOf("spark"))
        assertFalse(filters.matches(station(network = Network.SPARK)))
        assertTrue(filters.matches(station(network = Network.EVION)))
    }

    @Test
    fun `тип и мощность проверяются на одном коннекторе`() {
        // Станция с медленным нужным разъёмом и быстрым чужим не должна
        // проходить фильтр «быстрая + GB_T DC»: приехав, человек не зарядится.
        val mixed = station(
            connectors = listOf(
                connector(ConnectorType.GBT_DC, 7.0),
                connector(ConnectorType.CCS2, 120.0),
            ),
        )
        val filters = Filters(
            typesOff = Filters.allTypeIds.toSet() - "GBT_DC",
            power = PowerBand.FAST,
        )
        assertFalse(filters.matches(mixed))
    }

    @Test
    fun `пороги мощности совпадают с серверными`() {
        assertTrue(Filters(power = PowerBand.SLOW).powerFits(21.9))
        assertFalse(Filters(power = PowerBand.SLOW).powerFits(22.0))
        assertTrue(Filters(power = PowerBand.FAST).powerFits(22.0))
        assertFalse(Filters(power = PowerBand.FAST).powerFits(100.0))
        assertTrue(Filters(power = PowerBand.ULTRA).powerFits(100.0))
    }

    @Test
    fun `неизвестная занятость не считается свободной`() {
        // free = null означает «неизвестно», а не «ноль свободных». Для
        // фильтра «только свободные» обе ситуации значат «не показывать».
        val filters = Filters(onlyFree = true)
        assertFalse(filters.matches(station(free = null, stale = true)))
        assertFalse(filters.matches(station(free = 0)))
        assertTrue(filters.matches(station(free = 1)))
    }

    @Test
    fun `неопознанный разъём фильтруется отдельным ключом`() {
        val unknown = station(connectors = listOf(connector(null, 50.0, raw = "АС Тип 2")))
        assertTrue(Filters().matches(unknown))
        assertFalse(Filters(typesOff = setOf(Filters.UNKNOWN_TYPE)).matches(unknown))
    }

    @Test
    fun `параметры запроса не отправляются, когда фильтр по умолчанию`() {
        // Беспараметрический запрос уходит на дешёвый путь сервера с ETag,
        // поэтому лишних параметров быть не должно.
        assertTrue(Filters().toQuery().isEmpty())
    }

    @Test
    fun `в запрос уходят включённые значения, а хранятся исключения`() {
        val filters = Filters(networksOff = setOf("weway"), onlyFree = true)
        val query = filters.toQuery()
        assertEquals("spark,evion,charge24", query["network"])
        assertEquals("1", query["free"])
    }

    @Test
    fun `полностью выключенный фильтр помечается отдельно`() {
        // Пустой network= сервер трактует как «все сети»; без этой проверки
        // список показал бы станции, которых на карте нет.
        val all = Filters(networksOff = Network.entries.map { it.id }.toSet())
        assertTrue(all.excludesEverything)
        assertFalse(Filters().excludesEverything)
    }
}
