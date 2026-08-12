package com.chargekg.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Последний удачный ответ `/v1/stations` целиком, как пришёл, плюс его ETag.
 *
 * База данных здесь была бы лишней: кэшируется ровно один документ, никаких
 * запросов по полям к нему не делается. Файл открывается мгновенно и
 * позволяет нарисовать карту до того, как ответит сеть.
 */
class StationCache(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val statusFile = File(appContext.filesDir, STATUS_FILE_NAME)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val etag: String? get() = prefs.getString(KEY_ETAG, null)

    val statusEtag: String? get() = prefs.getString(KEY_STATUS_ETAG, null)

    /** Когда кэш был записан, миллисекунды эпохи; 0 — кэша нет. */
    val savedAt: Long get() = prefs.getLong(KEY_SAVED_AT, 0L)

    suspend fun read(): String? = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) file.readText() else null }.getOrNull()
    }

    suspend fun write(body: String, etag: String?) = withContext(Dispatchers.IO) {
        runCatching {
            file.writeText(body)
            // ETag пишется только после успешной записи тела: иначе после
            // сбоя записи клиент получал бы 304 на пустой кэш и оставался
            // без данных до смены слепка на сервере.
            prefs.edit()
                .putString(KEY_ETAG, etag)
                .putLong(KEY_SAVED_AT, System.currentTimeMillis())
                .apply()
        }
        Unit
    }

    suspend fun readStatus(): String? = withContext(Dispatchers.IO) {
        runCatching { if (statusFile.exists()) statusFile.readText() else null }.getOrNull()
    }

    /**
     * Занятость хранится отдельным файлом вместе со своим ETag. Это и позволяет
     * поверить ответу 304: раз сервер подтвердил тот же слепок, лежащая рядом
     * занятость — сегодняшняя, и её можно показывать как настоящую.
     */
    suspend fun writeStatus(body: String, etag: String?) = withContext(Dispatchers.IO) {
        runCatching {
            statusFile.writeText(body)
            prefs.edit().putString(KEY_STATUS_ETAG, etag).apply()
        }
        Unit
    }

    /**
     * Полный список сменился — значит сохранённая занятость может относиться к
     * другому составу станций. Держать её дальше нельзя: ETag подтвердил бы
     * чужие числа.
     */
    fun clearStatus() {
        runCatching { statusFile.delete() }
        prefs.edit().remove(KEY_STATUS_ETAG).apply()
    }

    fun clear() {
        runCatching { file.delete() }
        runCatching { statusFile.delete() }
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "stations.json"
        const val STATUS_FILE_NAME = "status.json"
        const val PREFS = "station_cache"
        const val KEY_ETAG = "etag"
        const val KEY_STATUS_ETAG = "status_etag"
        const val KEY_SAVED_AT = "saved_at"
    }
}
