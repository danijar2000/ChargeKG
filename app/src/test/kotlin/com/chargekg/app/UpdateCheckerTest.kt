package com.chargekg.app

import androidx.test.core.app.ApplicationProvider
import com.chargekg.app.update.UpdateChecker
import com.chargekg.app.update.UpdateChecker.Companion.isNewer
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateCheckerTest {

    @Test
    fun `отсутствие релизов не считается ошибкой`() = runTest {
        // Свежий репозиторий отвечает 404 на releases/latest. Показывать за это
        // человеку «GitHub API: HTTP 404» не за что — обновлять просто нечего.
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(404))

        val checker = UpdateChecker(OkHttpClient(), server.url("/").toString())
        val info = checker.checkForUpdate(
            ApplicationProvider.getApplicationContext(),
            forceCheck = true,
        )

        assertNull(info)
        server.shutdown()
    }

    @Test
    fun `остальные ошибки GitHub долетают до человека`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(500))

        val checker = UpdateChecker(OkHttpClient(), server.url("/").toString())
        val error = runCatching {
            checker.checkForUpdate(ApplicationProvider.getApplicationContext(), forceCheck = true)
        }.exceptionOrNull()

        assertNotNull(error)
        server.shutdown()
    }

    @Test
    fun `сравнение идёт по числам, а не по строкам`() {
        // Строковое сравнение считало бы 3.9.3 новее 3.10.0 — и обновление
        // перестало бы предлагаться ровно на десятом минорном релизе.
        assertTrue(isNewer("3.10.0", "3.9.3"))
        assertFalse(isNewer("3.9.3", "3.10.0"))
    }

    @Test
    fun `одинаковые версии обновлением не считаются`() {
        assertFalse(isNewer("1.2.3", "1.2.3"))
    }

    @Test
    fun `недостающие сегменты считаются нулями`() {
        assertFalse(isNewer("1.2", "1.2.0"))
        assertTrue(isNewer("1.2.1", "1.2"))
    }

    @Test
    fun `суффикс отладочной сборки не ломает сравнение`() {
        // versionName отладочной сборки — «0.1.0-debug»: сегмент не парсится
        // и отбрасывается, но версия не должна вдруг стать «новее».
        assertFalse(isNewer("0.1.0", "0.1.0-debug"))
        assertTrue(isNewer("0.2.0", "0.1.0-debug"))
    }
}
