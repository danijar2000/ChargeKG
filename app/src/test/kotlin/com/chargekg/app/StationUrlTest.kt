package com.chargekg.app

import com.chargekg.app.data.Network
import com.chargekg.app.util.stationUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ссылки в чужие приложения не выдумываются: значение для SPARK проверено на
 * живом устройстве на трёх станциях, у остальных сетей ссылки нет вовсе.
 */
class StationUrlTest {

    @Test
    fun `у SPARK ссылка на станцию есть`() {
        assertEquals(
            "spark://charging-station/003451c1-b3b2-4741-b1b1-0452c89867ca",
            stationUrl(Network.SPARK, "003451c1-b3b2-4741-b1b1-0452c89867ca"),
        )
    }

    @Test
    fun `у остальных сетей ссылки нет`() {
        // Charge24 не открыл карточку ни на одном из десяти перебранных путей,
        // у We way фильтр только на схеме Firebase Dynamic Links, у EVION в
        // манифесте нет ни одного фильтра VIEW, а у RedPay все фильтры — на
        // https-ссылки приглашений и оплаты.
        assertNull(stationUrl(Network.CHARGE24, "1"))
        assertNull(stationUrl(Network.WEWAY, "10"))
        assertNull(stationUrl(Network.EVION, "646c7cf1b24a83a132b62969"))
        assertNull(stationUrl(Network.REDPAY, "loc-001"))
    }
}
