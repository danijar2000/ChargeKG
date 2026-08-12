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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StationRepositoryTest {

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

    @Test
    fun `удачный ответ сохраняется вместе с ETag`() = runTest {
        server.enqueue(MockResponse().setBody(BODY).setHeader("ETag", "\"v1\""))
        repo.refresh()

        assertEquals("\"v1\"", cache.etag)
        assertEquals(DataOrigin.NETWORK, repo.state.value.origin)
        assertEquals(2, repo.state.value.stations[0].free)
    }

    @Test
    fun `из кэша занятость отдаётся неизвестной`() = runTest {
        cache.write(BODY, "\"v1\"")
        repo.loadFromCache()

        val station = repo.state.value.stations.single()
        // Показать вчерашние «2 свободно» как сегодняшние — соврать ровно там,
        // ради чего карту и открывают.
        assertNull(station.free)
        assertTrue(station.unknownBusy)
        assertEquals(DataOrigin.CACHE, repo.state.value.origin)
        // Всё, что не протухает, остаётся на месте.
        assertEquals(3, station.total)
        assertEquals("Вефа", station.name)
    }

    @Test
    fun `304 воскрешает настоящую занятость из кэша`() = runTest {
        cache.write(BODY, "\"v1\"")
        repo.loadFromCache()
        assertNull(repo.state.value.stations.single().free)

        // Кэша занятости нет — она появится только после первого удачного
        // /v1/status, — поэтому запрос уходит туда, а оттуда падает на полный
        // список. Сервер подтверждает слепок, и занятость перечитывается.
        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(MockResponse().setResponseCode(304))
        repo.refresh()

        assertEquals("/v1/status", server.takeRequest().path)
        assertEquals("/v1/stations", server.takeRequest().path)
        // Без перечитывания на экране осталась бы копия с обнулёнными счётчиками.
        assertEquals(2, repo.state.value.stations.single().free)
        assertEquals(DataOrigin.NETWORK, repo.state.value.origin)
    }

    @Test
    fun `ошибка сети не стирает показанное`() = runTest {
        cache.write(BODY, "\"v1\"")
        repo.loadFromCache()

        server.shutdown()
        repo.refresh()

        assertEquals(1, repo.state.value.stations.size)
        assertNotNull(repo.state.value.error)
        assertEquals(DataOrigin.CACHE, repo.state.value.origin)
    }

    @Test
    fun `битый ответ не роняет обновление`() = runTest {
        server.enqueue(MockResponse().setBody("не json"))
        repo.refresh()

        assertNotNull(repo.state.value.error)
        assertTrue(repo.state.value.stations.isEmpty())
    }

    private companion object {
        val BODY = """
        {"generated_at":"2026-08-11T10:00:00Z","count":1,"stations":[
          {"id":"spark:1","network":"spark","name":"Вефа","address":"","lat":42.8,"lng":74.6,
           "promotions":[],"free":2,"busy":1,"offline":0,"total":3,"status_stale":false,
           "updated_at":"2026-08-11T09:59:00Z","merged_count":1,
           "connectors":[{"id":"c1","type":"GBT_DC","power":60,"price":14,"price_text":"14"}]}
        ]}
        """.trimIndent()
    }
}
