package com.chargekg.app

import androidx.test.core.app.ApplicationProvider
import com.chargekg.app.data.ChargeApi
import com.chargekg.app.data.DataOrigin
import com.chargekg.app.data.StationCache
import com.chargekg.app.data.StationRepository
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Обновление карты идёт за `/v1/status`: он в восемь с лишним раз легче
 * полного списка. Здесь проверяется, что лёгкий путь не врёт про занятость и
 * вовремя уступает место полному.
 */
@RunWith(RobolectricTestRunner::class)
class StatusRefreshTest {

    private lateinit var server: MockWebServer
    private lateinit var cache: StationCache
    private lateinit var repo: StationRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        cache = StationCache(context)
        cache.clear()
        repo = StationRepository(ChargeApi(OkHttpClient(), server.url("/")), cache)
    }

    @After
    fun tearDown() {
        cache.clear()
        server.shutdown()
    }

    private fun paths(count: Int): List<String> =
        (1..count).map { server.takeRequest().path.orEmpty() }

    private fun full(free: Int, total: Int) = """
        {"generated_at":"2026-08-12T10:00:00Z","count":1,"stations":[
          {"id":"spark:1","network":"spark","name":"Вефа","address":"","lat":42.8,"lng":74.6,
           "promotions":[],"free":$free,"busy":0,"offline":0,"total":$total,
           "status_stale":false,"updated_at":"2026-08-12T09:59:00Z","merged_count":1,
           "connectors":[{"id":"c1","type":"GBT_DC","power":60,"price":14,"price_text":"14"}]}
        ]}
    """.trimIndent()

    private fun status(free: String, total: Int, stale: Boolean = false) = """
        {"generated_at":"2026-08-12T10:05:00Z","count":1,"stations":[
          {"id":"spark:1","free":$free,"busy":0,"offline":0,"total":$total,
           "status_stale":$stale}
        ]}
    """.trimIndent()

    @Test
    fun `первое обновление берёт полный список, следующее — только занятость`() = runTest {
        server.enqueue(MockResponse().setBody(full(2, 3)).setHeader("ETag", "\"full\""))
        repo.refresh()
        server.enqueue(MockResponse().setBody(status("1", 3)).setHeader("ETag", "\"st\""))
        repo.refresh()

        assertEquals(listOf("/v1/stations", "/v1/status"), paths(2))
        assertEquals(1, repo.state.value.stations.single().free)
        // Справочные поля пришли из полного ответа и никуда не делись.
        assertEquals("Вефа", repo.state.value.stations.single().name)
        assertEquals(1, repo.state.value.stations.single().connectors.size)
    }

    @Test
    fun `изменившееся число портов уводит за полным списком`() = runTest {
        server.enqueue(MockResponse().setBody(full(2, 3)).setHeader("ETag", "\"full\""))
        repo.refresh()

        // У станции стало четыре порта: значит поменялся и состав разъёмов.
        // Наложить занятость на старый список нельзя — кольцо метки рисовалось
        // бы по старому количеству, молча и неделями.
        server.enqueue(MockResponse().setBody(status("1", 4)).setHeader("ETag", "\"st\""))
        server.enqueue(MockResponse().setBody(full(1, 4)).setHeader("ETag", "\"full2\""))
        repo.refresh()

        assertEquals(listOf("/v1/stations", "/v1/status", "/v1/stations"), paths(3))
        assertEquals(4, repo.state.value.stations.single().total)
    }

    @Test
    fun `незнакомая станция уводит за полным списком`() = runTest {
        server.enqueue(MockResponse().setBody(full(2, 3)).setHeader("ETag", "\"full\""))
        repo.refresh()

        val two = """
            {"generated_at":"2026-08-12T10:05:00Z","count":2,"stations":[
              {"id":"spark:1","free":1,"busy":0,"offline":0,"total":3,"status_stale":false},
              {"id":"spark:9","free":2,"busy":0,"offline":0,"total":2,"status_stale":false}
            ]}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(two).setHeader("ETag", "\"st\""))
        server.enqueue(MockResponse().setBody(full(1, 3)).setHeader("ETag", "\"full2\""))
        repo.refresh()

        assertEquals(listOf("/v1/stations", "/v1/status", "/v1/stations"), paths(3))
    }

    @Test
    fun `устаревшая занятость приходит как неизвестная, а не как ноль`() = runTest {
        server.enqueue(MockResponse().setBody(full(2, 3)).setHeader("ETag", "\"full\""))
        repo.refresh()
        server.enqueue(MockResponse().setBody(status("null", 3, stale = true)).setHeader("ETag", "\"st\""))
        repo.refresh()

        val station = repo.state.value.stations.single()
        assertNull(station.free)
        assertTrue(station.unknownBusy)
        // Станция остаётся в выдаче: адрес и разъёмы не протухают.
        assertEquals(3, station.total)
    }

    @Test
    fun `304 на занятость воскрешает её из кэша после холодного старта`() = runTest {
        server.enqueue(MockResponse().setBody(full(2, 3)).setHeader("ETag", "\"full\""))
        repo.refresh()
        server.enqueue(MockResponse().setBody(status("1", 3)).setHeader("ETag", "\"st\""))
        repo.refresh()
        paths(2)

        // Новый запуск: станции читаются из кэша с намеренно обнулённой
        // занятостью, потому что она могла устареть за ночь.
        val cold = StationRepository(ChargeApi(OkHttpClient(), server.url("/")), cache)
        cold.loadFromCache()
        assertNull(cold.state.value.stations.single().free)
        assertEquals(DataOrigin.CACHE, cold.state.value.origin)

        // Сервер подтверждает тот же слепок занятости — значит лежащая рядом
        // копия сегодняшняя, и её можно показать как настоящую.
        server.enqueue(MockResponse().setResponseCode(304))
        cold.refresh()

        val request: RecordedRequest = server.takeRequest()
        assertEquals("/v1/status", request.path)
        assertEquals("\"st\"", request.getHeader("If-None-Match"))
        assertEquals(1, cold.state.value.stations.single().free)
        assertEquals(DataOrigin.NETWORK, cold.state.value.origin)
    }

    @Test
    fun `новый полный список выбрасывает сохранённую занятость`() = runTest {
        server.enqueue(MockResponse().setBody(full(2, 3)).setHeader("ETag", "\"full\""))
        repo.refresh()
        server.enqueue(MockResponse().setBody(status("1", 3)).setHeader("ETag", "\"st\""))
        repo.refresh()
        paths(2)

        // Список станций сменился — прежняя занятость относилась к другому
        // составу, и её ETag подтверждал бы чужие числа.
        server.enqueue(MockResponse().setBody(status("1", 9)).setHeader("ETag", "\"st2\""))
        server.enqueue(MockResponse().setBody(full(1, 9)).setHeader("ETag", "\"full2\""))
        repo.refresh()
        paths(2)

        assertNull(cache.statusEtag)
    }
}
