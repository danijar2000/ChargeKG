package com.chargekg.app

import com.chargekg.app.data.ApiException
import com.chargekg.app.data.ChargeApi
import com.chargekg.app.data.ConnectorType
import com.chargekg.app.data.Network
import com.chargekg.app.data.StationsFetch
import com.chargekg.app.data.parseStations
import com.chargekg.app.domain.Filters
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Под Robolectric, а не на голой JVM: org.json в android.jar — заглушка, и с
 * `isReturnDefaultValues = true` её методы молча возвращают null вместо того,
 * чтобы упасть. Настоящую реализацию даёт Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class ChargeApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ChargeApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ChargeApi(OkHttpClient(), server.url("/"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `список станций запрашивается без параметров`() = runTest {
        server.enqueue(MockResponse().setBody(STATIONS_JSON).setHeader("ETag", "\"abc\""))
        val fetch = api.stations(null)

        val request = server.takeRequest()
        // Любой параметр уводит сервер со слепка в памяти на сборку из базы,
        // где нет ни ETag, ни готового gzip.
        assertEquals("/v1/stations", request.path)
        assertNull(request.getHeader("If-None-Match"))

        assertTrue(fetch is StationsFetch.Fresh)
        assertEquals("\"abc\"", (fetch as StationsFetch.Fresh).etag)
    }

    @Test
    fun `сохранённый ETag уходит условным запросом, а 304 не приносит тела`() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))
        val fetch = api.stations("\"abc\"")

        assertEquals("\"abc\"", server.takeRequest().getHeader("If-None-Match"))
        assertTrue(fetch is StationsFetch.NotModified)
    }

    @Test
    fun `ошибка показывается пояснением сервера, а не голым кодом`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setBody("""{"error":"not_ready","message":"данные ещё не загружены"}""")
        )
        val error = runCatching { api.stations(null) }.exceptionOrNull()
        assertTrue(error is ApiException)
        assertEquals("данные ещё не загружены", error?.message)
    }

    @Test
    fun `фильтры превращаются в параметры ближайших`() = runTest {
        server.enqueue(MockResponse().setBody("""{"routed":true,"count":0,"stations":[]}"""))
        api.nearest(42.8, 74.6, Filters(networksOff = setOf("weway"), onlyFree = true))

        val path = server.takeRequest().path.orEmpty()
        assertTrue(path.startsWith("/v1/nearest"))
        assertTrue(path.contains("lat=42.8"))
        assertTrue(path.contains("free=1"))
        assertTrue(path.contains("network=spark%2Cevion%2Ccharge24"))
    }

    @Test
    fun `устаревшая занятость разбирается как неизвестная, а не как ноль`() {
        val stations = parseStations(STATIONS_JSON).stations
        val stale = stations.first { it.id == "weway:9" }
        assertNull(stale.free)
        assertTrue(stale.unknownBusy)
        // Станция при этом остаётся в выдаче: адрес, разъёмы и цены не протухают.
        assertEquals(2, stale.total)
    }

    @Test
    fun `разбор станции сохраняет всё, что нужно карточке`() {
        val station = parseStations(STATIONS_JSON).stations.first()
        assertEquals(Network.SPARK, station.network)
        assertEquals(3, station.free)
        assertEquals(listOf("Скидка 10 % ночью"), station.promotions)
        assertEquals(ConnectorType.GBT_DC, station.connectors[0].type)
        assertEquals(120.0, station.connectors[0].power, 0.001)
        // Цена может отсутствовать — это не ноль.
        assertNull(station.connectors[1].price)
        assertEquals("АС Тип 2", station.connectors[1].rawType)
        assertNull(station.connectors[1].type)
        assertEquals(2, station.mergedCount)
        assertEquals("spark:2", station.merged[0].id)
    }

    private companion object {
        val STATIONS_JSON = """
        {
          "generated_at": "2026-08-11T10:00:00Z",
          "count": 2,
          "stations": [
            {
              "id": "spark:1", "network": "spark", "name": "Вефа", "address": "Горького 27",
              "lat": 42.86, "lng": 74.6, "promotions": ["Скидка 10 % ночью"],
              "free": 3, "busy": 1, "offline": 0, "total": 4,
              "status_stale": false, "updated_at": "2026-08-11T09:59:00Z",
              "merged_count": 2,
              "merged": [{"id":"spark:2","name":"Вефа 2","address":"","total":2}],
              "connectors": [
                {"id":"c1","type":"GBT_DC","power":120,"price":14.5,"price_text":"14,5 сом/кВт·ч"},
                {"id":"c2","type":"UNKNOWN","raw_type":"АС Тип 2","power":7,"price":null}
              ]
            },
            {
              "id": "weway:9", "network": "weway", "name": "Ala-Too", "address": "",
              "lat": 42.87, "lng": 74.61, "promotions": [],
              "free": null, "busy": null, "offline": null, "total": 2,
              "status_stale": true, "updated_at": "2026-08-11T08:00:00Z",
              "merged_count": 1,
              "connectors": [{"id":"c3","type":"CCS2","power":60,"price":16,"price_text":"16 сом"}]
            }
          ]
        }
        """.trimIndent()
    }
}
