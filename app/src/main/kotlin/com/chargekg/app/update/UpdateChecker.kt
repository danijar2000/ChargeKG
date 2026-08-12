package com.chargekg.app.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.chargekg.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Обновление приложения через GitHub Releases.
 *
 * Приложение не публикуется в Google Play, поэтому обновление — единственный
 * способ доставить исправления. Логика перенесена из BYDMate, где она
 * отработана на живых устройствах; отличия отмечены комментариями.
 */
class UpdateChecker(
    private val httpClient: OkHttpClient,
    /** Адрес вынесен параметром только ради тестов: в приложении он один. */
    private val apiUrl: String = GITHUB_API,
) {

    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String,
        /** Размер APK в байтах: человек должен видеть, сколько качать. */
        val sizeBytes: Long,
    )

    /**
     * Проверить наличие обновления. Возвращает null, если установлена
     * последняя версия или проверка отложена троттлингом.
     */
    suspend fun checkForUpdate(context: Context, forceCheck: Boolean = false): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (!forceCheck && now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MS) {
                return@withContext null
            }
            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ChargeKG-UpdateCheck")
                .build()
            val body = httpClient.newCall(request).execute().use { response ->
                // 404 у releases/latest означает «релизов ещё нет», а не сбой:
                // так отвечает свежий репозиторий. Показывать человеку
                // «GitHub API: HTTP 404» здесь не за что — обновлять просто нечего.
                if (response.code == 404) return@withContext null
                if (!response.isSuccessful) {
                    throw Exception(context.getString(R.string.update_error_http, response.code))
                }
                response.body?.string()
                    ?: throw Exception(context.getString(R.string.update_error_empty))
            }

            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").removePrefix("v")
            if (tagName.isEmpty()) {
                throw Exception(context.getString(R.string.update_error_no_tag))
            }
            if (!isNewer(tagName, appVersion(context))) return@withContext null

            val assets = json.optJSONArray("assets")
                ?: throw Exception(context.getString(R.string.update_error_no_apk, tagName))
            var apkUrl: String? = null
            var apkSize = 0L
            // Берётся ПЕРВЫЙ .apk в релизе. Поэтому релиз обязан нести ровно
            // один APK: ABI-сплиты сломали бы обновление молча.
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name", "").endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url")
                    apkSize = asset.optLong("size")
                    break
                }
            }
            val url = apkUrl
                ?: throw Exception(context.getString(R.string.update_error_no_apk, tagName))

            UpdateInfo(
                version = tagName,
                downloadUrl = url,
                releaseNotes = json.optString("body", ""),
                sizeBytes = apkSize,
            )
        }

    /**
     * Скачать APK и открыть установщик. [onProgress] получает готовые строки
     * для показа в диалоге.
     */
    suspend fun downloadAndInstall(
        context: Context,
        update: UpdateInfo,
        onProgress: (String) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = apkName(update.version)

        val destFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName,
        )
        if (destFile.exists()) destFile.delete()

        // Уведомление о загрузке рисует системный загрузчик от своего имени,
        // поэтому POST_NOTIFICATIONS приложению не нужен.
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("ChargeKG ${update.version}")
            .setDescription(context.getString(R.string.update_download_notification))
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

        val downloadId = downloadManager.enqueue(request)
        onProgress(context.getString(R.string.update_downloading_start))

        var finished = false
        while (!finished) {
            // .use{} закрывает курсор на каждом проходе. Закрытие только внутри
            // moveToFirst() течёт по курсору на каждый опрос.
            val rowPresent = downloadManager.query(
                DownloadManager.Query().setFilterById(downloadId)
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use false
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_RUNNING -> {
                        val total = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        )
                        val done = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        )
                        if (total > 0) {
                            onProgress(
                                context.getString(
                                    R.string.update_downloading_progress,
                                    (done * 100 / total).toInt(),
                                )
                            )
                        }
                    }

                    DownloadManager.STATUS_SUCCESSFUL -> {
                        finished = true
                        onProgress(context.getString(R.string.update_downloading_done))
                    }

                    DownloadManager.STATUS_FAILED -> {
                        finished = true
                        val reason = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                        )
                        throw Exception(context.getString(R.string.update_download_error, reason))
                    }

                    DownloadManager.STATUS_PAUSED ->
                        onProgress(context.getString(R.string.update_downloading_paused))
                }
                true
            } ?: false

            // Строка загрузки исчезла (человек убрал её из «Загрузок»): без этой
            // проверки цикл крутился бы вечно, ведь finished уже не выставит никто.
            if (!rowPresent) {
                throw Exception(context.getString(R.string.update_download_error, -1))
            }
            if (!finished) delay(500)
        }

        withContext(Dispatchers.Main) { installApk(context, update.version) }
    }

    /**
     * Начиная с Android 8 установка своего обновления требует явного
     * разрешения. В BYDMate этой проверки нет — там targetSdk 29 и другой
     * сценарий поставки.
     */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    private fun installApk(context: Context, version: String) {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            apkName(version),
        )
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun apkName(version: String) = "ChargeKG-v$version.apk"

    private fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    } catch (_: Exception) {
        "0.0.0"
    }

    companion object {
        const val GITHUB_API =
            "https://api.github.com/repos/danijar2000/ChargeKG/releases/latest"
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_AUTO_CHECK = "auto_check_enabled"

        /** Защищает от повторной проверки при быстрых перезапусках. */
        private const val CHECK_INTERVAL_MS = 10 * 60 * 1000L

        fun isAutoCheckEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_CHECK, true)

        fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
        }

        /**
         * Посегментное сравнение версий: 3.10.0 новее 3.9.3, чего строковое
         * сравнение не даёт. Недостающие сегменты считаются нулями.
         */
        fun isNewer(remote: String, local: String): Boolean {
            val r = remote.split(".").mapNotNull { it.toIntOrNull() }
            val l = local.split(".").mapNotNull { it.toIntOrNull() }
            for (i in 0 until maxOf(r.size, l.size)) {
                val rv = r.getOrElse(i) { 0 }
                val lv = l.getOrElse(i) { 0 }
                if (rv > lv) return true
                if (rv < lv) return false
            }
            return false
        }
    }
}
