package com.chargekg.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.chargekg.app.R
import com.chargekg.app.data.Network
import com.chargekg.app.data.Station
import java.util.Locale

/**
 * Запуск чужих приложений: навигаторы и приложения зарядных сетей.
 *
 * Схемы и имена пакетов НЕ выдуманы — это проверенные значения из `web/app.js`,
 * полученные разбором манифестов. Подставлять сюда «логичные» варианты нельзя:
 * ошибка проявится только на устройстве с установленным приложением.
 */
enum class NavApp(val id: String, val labelRes: Int, val pkg: String?) {
    AUTO("auto", R.string.nav_auto, null),
    DGIS("dgis", R.string.nav_dgis, "ru.dublgis.dgismobile"),
    YNAVI("ynavi", R.string.nav_ynavi, "ru.yandex.yandexnavi"),
    YMAPS("ymaps", R.string.nav_ymaps, "ru.yandex.yandexmaps"),
    GMAPS("gmaps", R.string.nav_gmaps, "com.google.android.apps.maps"),
    ASK("ask", R.string.nav_ask, null);

    companion object {
        val DEFAULT = AUTO
        fun of(id: String?): NavApp = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

private fun fmt(v: Double): String = String.format(Locale.US, "%.6f", v)

/**
 * Открывает маршрут до станции. Возвращает false, если ни приложение, ни
 * веб-запасной вариант открыть не удалось — вызывающий покажет сообщение.
 */
fun openRoute(context: Context, station: Station, choice: NavApp): Boolean {
    val la = fmt(station.lat)
    val ln = fmt(station.lng)

    // "Автоматически" на Android — 2ГИС: в Кыргызстане он покрывает города
    // подробнее прочих. Тот же выбор сделан на сайте.
    val app = if (choice == NavApp.AUTO) NavApp.DGIS else choice

    if (app == NavApp.ASK) {
        val uri = "geo:$la,$ln?q=$la,$ln(${Uri.encode(station.name)})"
        return startView(context, uri, pkg = null) || openWebRoute(context, NavApp.GMAPS, la, ln)
    }

    val deepLink = when (app) {
        NavApp.DGIS -> "dgis://2gis.ru/routeSearch/rsType/car/to/$ln,$la"
        NavApp.YNAVI -> "yandexnavi://build_route_on_map?lat_to=$la&lon_to=$ln"
        NavApp.YMAPS -> "yandexmaps://maps.yandex.ru/?rtext=~$la,$ln&rtt=auto"
        NavApp.GMAPS -> "google.navigation:q=$la,$ln&mode=d"
        else -> null
    }

    if (deepLink != null && startView(context, deepLink, app.pkg)) return true
    return openWebRoute(context, app, la, ln)
}

private fun openWebRoute(context: Context, app: NavApp, la: String, ln: String): Boolean {
    val url = when (app) {
        NavApp.DGIS -> "https://2gis.kg/routeSearch/rsType/car/to/$ln,$la"
        NavApp.YNAVI, NavApp.YMAPS -> "https://yandex.ru/maps/?rtext=~$la,$ln&rtt=auto"
        else -> "https://www.google.com/maps/dir/?api=1&destination=$la,$ln&travelmode=driving"
    }
    return startView(context, url, pkg = null)
}

/** Чем закончилась попытка открыть приложение сети. */
enum class NetworkAppResult {
    /** Открылась нужная точка зарядки. */
    STATION,

    /** Приложение открылось, но на главном экране: ссылки на станцию нет. */
    APP_ONLY,

    /** Ни приложения, ни магазина — открывать нечего. */
    FAILED,
}

/**
 * Ссылка на конкретную станцию в приложении сети.
 *
 * Есть только у SPARK. Значение проверено на живом устройстве на трёх станциях:
 * открывается именно нужная карточка с адресом, разъёмом и тарифом. Родной
 * идентификатор — тот же uuid, что приходит от API в нашем ключе `spark:<uuid>`.
 *
 * У остальных сетей ссылки на станцию нет, и это выяснено, а не предположено:
 *
 *  - **Charge24** — схема `charge24://` ловит любой путь, но ни один из десяти
 *    перебранных не открыл карточку. Сходится с его веб-версией: там вообще нет
 *    маршрута станции, одна карта, а «поделиться» сделано только для чеков.
 *  - **We way** — единственный фильтр висит на схеме Firebase Dynamic Links;
 *    такую ссылку снаружи не собрать, её выдаёт сервис по запросу владельца.
 *  - **EVION** — в манифесте нет ни одного фильтра VIEW, принимать ссылки
 *    приложению просто нечем.
 *  - **RedPay** — фильтры есть, но все на https-ссылки Firebase Dynamic Links
 *    (`redpay.page.link/ref-…`) и на хосты Namba: это приглашения и оплата, а
 *    не карточка станции.
 *
 * Подбирать «логичные» пути наугад нельзя: правило «схемы не выдумывать» этот
 * проект уже спасало. Если сеть однажды заведёт ссылку, её сюда добавят той же
 * проверкой на устройстве.
 */
internal fun stationUrl(network: Network, nativeId: String): String? = when (network) {
    Network.SPARK -> "spark://charging-station/$nativeId"
    Network.CHARGE24, Network.WEWAY, Network.EVION, Network.REDPAY -> null
}

/**
 * Открывает приложение зарядной сети — по возможности сразу на нужной точке.
 *
 * У EVION, We way и RedPay собственных browsable-схем нет вовсе, их приложения
 * запускаются по имени пакета.
 */
fun openNetworkApp(context: Context, station: Station): NetworkAppResult {
    val network = station.network ?: return NetworkAppResult.FAILED

    // Родной идентификатор станции в системе сети: наш ключ устроен как
    // «сеть:родной_id».
    val nativeId = station.id.substringAfter(':', "")
    if (nativeId.isNotEmpty()) {
        stationUrl(network, nativeId)?.let { url ->
            if (startView(context, url, network.pkg)) return NetworkAppResult.STATION
        }
    }

    if (network.scheme != null && startView(context, "${network.scheme}://open", network.pkg)) {
        return NetworkAppResult.APP_ONLY
    }
    val launch = context.packageManager.getLaunchIntentForPackage(network.pkg)
    if (launch != null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(launch) }.isSuccess) {
            return NetworkAppResult.APP_ONLY
        }
    }
    val store = "https://play.google.com/store/apps/details?id=${network.pkg}"
    return if (startView(context, store, null)) NetworkAppResult.APP_ONLY else NetworkAppResult.FAILED
}

private fun startView(context: Context, uri: String, pkg: String?): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg != null) setPackage(pkg)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
