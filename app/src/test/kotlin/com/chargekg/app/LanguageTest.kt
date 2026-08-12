package com.chargekg.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageTest {

    @Test
    fun `знакомые языки берутся как есть`() {
        assertEquals("ru", ChargeKgApp.languageOf("ru"))
        assertEquals("ky", ChargeKgApp.languageOf("ky"))
        assertEquals("en", ChargeKgApp.languageOf("en"))
    }

    @Test
    fun `незнакомая локаль падает на английский`() {
        // Казахский, узбекский, турецкий — приложение их не знает. Приезжему
        // английский полезнее русского, поэтому запасной именно он.
        assertEquals("en", ChargeKgApp.languageOf("kk"))
        assertEquals("en", ChargeKgApp.languageOf("uz"))
        assertEquals("en", ChargeKgApp.languageOf("tr"))
        assertEquals("en", ChargeKgApp.languageOf(""))
    }

    @Test
    fun `регистр не важен`() {
        // Locale может отдать язык как угодно; сравнение по строке без
        // приведения регистра молча отправило бы человека на английский.
        assertEquals("ru", ChargeKgApp.languageOf("RU"))
        assertEquals("ky", ChargeKgApp.languageOf("Ky"))
    }
}
