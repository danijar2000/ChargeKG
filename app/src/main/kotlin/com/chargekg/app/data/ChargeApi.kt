package com.chargekg.app.data

import com.chargekg.app.domain.Filters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Ответ на условный запрос: либо новые данные, либо «не изменилось». */
sealed interface StationsFetch {
    data class Fresh(val body: String, val etag: String?) : StationsFetch
    data object NotModified : StationsFetch
}

class ApiException(message: String) : IOException(message)

/**
 * Клиент ChargeKG API. Все вызовы — GET, авторизации нет.
 *
 * Список станций берётся ТОЛЬКО беспараметрическим `/v1/stations`. Это
 * принципиально: сервер держит готовый слепок в памяти сразу в двух видах —
 * обычном и gzip — и отдаёт его байтами вместе с ETag. Любой параметр
 * (`network`, `since`) уводит запрос на сборку из базы, где нет ни ETag, ни
 * сжатия. Дельта по `since` вдобавок бесполезна: условие отбора смотрит на
 * время обновления занятости, а оно меняется у всех станций каждый цикл
 * синхронизации. Фильтрация по сети делается на устройстве.
 */
class ChargeApi(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
) {
    constructor(client: OkHttpClient, baseUrl: String) : this(client, baseUrl.toHttpUrl())

    suspend fun stations(etag: String?): StationsFetch = withContext(Dispatchers.IO) {
        val url = baseUrl.newBuilder().addPathSegments("v1/stations").build()
        val builder = Request.Builder().url(url)
        if (!etag.isNullOrEmpty()) builder.header("If-None-Match", etag)

        client.newCall(builder.build()).execute().use { response ->
            when {
                response.code == 304 -> StationsFetch.NotModified
                response.isSuccessful -> StationsFetch.Fresh(
                    body = response.body?.string().orEmpty(),
                    etag = response.header("ETag"),
                )
                else -> throw ApiException(errorText(response.code, response.body?.string()))
            }
        }
    }

    suspend fun nearest(
        lat: Double,
        lng: Double,
        filters: Filters,
        limit: Int = 10,
    ): NearestResult = withContext(Dispatchers.IO) {
        val url = baseUrl.newBuilder()
            .addPathSegments("v1/nearest")
            .addQueryParameter("lat", lat.toString())
            .addQueryParameter("lng", lng.toString())
            .addQueryParameter("limit", limit.toString())
            .apply { filters.toQuery().forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()

        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(errorText(response.code, response.body?.string()))
            }
            parseNearest(response.body?.string().orEmpty())
        }
    }

    suspend fun meta(): Meta = withContext(Dispatchers.IO) {
        val url = baseUrl.newBuilder().addPathSegments("v1/meta").build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(errorText(response.code, response.body?.string()))
            }
            parseMeta(response.body?.string().orEmpty())
        }
    }

    /** Сервер объясняет ошибку по-русски в теле — показываем это, а не голый код. */
    private fun errorText(code: Int, body: String?): String {
        val message = body?.let { parseErrorMessage(it) }.orEmpty()
        return if (message.isNotEmpty()) message else "HTTP $code"
    }
}
