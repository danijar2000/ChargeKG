package com.chargekg.app

import com.chargekg.app.update.UpdateChecker.Companion.isNewer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

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
