package com.chargekg.app

import androidx.test.core.app.ApplicationProvider
import com.chargekg.app.data.ChargeApi
import com.chargekg.app.data.DataOrigin
import com.chargekg.app.data.LoadError
import com.chargekg.app.data.StationCache
import com.chargekg.app.data.StationRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
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
    private lateinit var cacheFile: java.io.File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        cache = StationCache(context)
        cacheFile = java.io.File(context.filesDir, "stations.json")
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

    @Test
    fun `304 на пропавший кэш забывает ETag и берёт список целиком`() = runTest {
        cache.write(BODY, "\"v1\"")
        cacheFile.delete()

        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(MockResponse().setBody(BODY).setHeader("ETag", "\"v2\""))
        repo.refresh()

        assertEquals("\"v1\"", server.takeRequest().getHeader("If-None-Match"))
        // Без повтора карта осталась бы пустой, пока сервер отвечает 304.
        // Таймаут — чтобы поломка валила тест, а не вешала его.
        val retry = server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS)
        assertNotNull("после 304 на пустой кэш список не перезапрошен", retry)
        assertNull(retry!!.getHeader("If-None-Match"))
        assertEquals(1, repo.state.value.stations.size)
        assertEquals("\"v2\"", cache.etag)
        assertNull(repo.state.value.error)
    }

    @Test
    fun `304 на битый кэш не застревает на ошибке разбора`() = runTest {
        cache.write(BODY, "\"v1\"")
        cacheFile.writeText("{обрезано")

        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(MockResponse().setBody(BODY).setHeader("ETag", "\"v2\""))
        repo.refresh()

        assertEquals(2, server.requestCount)
        assertEquals(2, repo.state.value.stations.single().free)
        assertNull(repo.state.value.error)
    }

    @Test
    fun `обрыв соединения показывается как нет связи, а не текстом OkHttp`() = runTest {
        // OkHttp сам повторяет запрос после обрыва — обрываем с запасом.
        repeat(3) {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        }
        repo.refresh()

        assertEquals(LoadError.Offline, repo.state.value.error)
    }

    @Test
    fun `второе обновление во время первого не уходит в сеть`() = runTest {
        server.enqueue(MockResponse().setBody(BODY).setHeader("ETag", "\"v1\""))
        coroutineScope {
            launch { repo.refresh() }
            launch { repo.refresh() }
        }

        assertEquals(1, server.requestCount)
        assertEquals(1, repo.state.value.stations.size)
        assertTrue(!repo.state.value.loading)
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
