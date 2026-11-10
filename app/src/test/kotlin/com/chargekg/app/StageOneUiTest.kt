package com.chargekg.app

import com.chargekg.app.data.Connector
import com.chargekg.app.data.ConnectorType
import com.chargekg.app.ui.groupConnectors
import com.chargekg.app.ui.soloToggle
import org.junit.Assert.assertEquals
import org.junit.Test

class StageOneUiTest {
    private val all = listOf("a", "b", "c")

    @Test
    fun `нажатие при «Все» оставляет только выбранное`() {
        assertEquals(setOf("b", "c"), soloToggle(emptySet(), "a", all))
    }

    @Test
    fun `дальше нажатия добавляют и убирают значения`() {
        assertEquals(setOf("c"), soloToggle(setOf("b", "c"), "b", all))
        assertEquals(setOf("b", "c"), soloToggle(setOf("c"), "b", all))
    }

    @Test
    fun `убрали последнее значение — снова «Все», а не пустая карта`() {
        assertEquals(emptySet<String>(), soloToggle(setOf("b", "c"), "a", all))
    }

    private fun c(id: String, type: ConnectorType?, kw: Double, price: String = "15 сом") =
        Connector(id, type, type?.title ?: "x", kw, null, price)

    @Test
    fun `одинаковые разъёмы сводятся в одну строку, мощные сверху`() {
        val groups = groupConnectors(
            listOf(
                c("1", ConnectorType.GBT_DC, 120.0),
                c("2", ConnectorType.GBT_DC, 120.0),
                c("3", ConnectorType.TYPE2_AC, 22.0),
                c("4", ConnectorType.GBT_DC, 120.0),
            )
        )
        assertEquals(2, groups.size)
        assertEquals(ConnectorType.GBT_DC, groups[0].sample.type)
        assertEquals(3, groups[0].count)
        assertEquals(1, groups[1].count)
    }

    @Test
    fun `разная цена не склеивается`() {
        val groups = groupConnectors(
            listOf(
                c("1", ConnectorType.CCS2, 60.0, "14 сом"),
                c("2", ConnectorType.CCS2, 60.0, "16 сом"),
            )
        )
        assertEquals(2, groups.size)
    }
}
