package com.chargekg.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.content.res.Resources
import com.chargekg.app.domain.Cluster

/**
 * Метка станции: кольцо, разбитое по числу портов — зелёное свободные,
 * красное занятые, серое не в сети; в центре общее число портов.
 *
 * Ровно так же метка устроена на сайте. Если сеть перестала отвечать, кольцо
 * становится целиком серым: показать устаревшую занятость как настоящую
 * значило бы ввести человека в заблуждение.
 */
object PinIcons {
    private const val SIZE_DP = 40f
    private const val RING_DP = 5f

    private val FREE = Color.rgb(0x16, 0xA3, 0x4A)
    private val BUSY = Color.rgb(0xDC, 0x26, 0x26)
    private val OFF = Color.rgb(0x9C, 0xA3, 0xAF)
    private val ACCENT = Color.rgb(0x2F, 0x6F, 0xED)

    private val cache = HashMap<String, Drawable>()

    fun forCluster(resources: Resources, cluster: Cluster, selected: Boolean): Drawable {
        val count = cluster.stations.size
        val key = if (cluster.unknownBusy) {
            "u:$count:${cluster.totalPorts}:$selected"
        } else {
            "$count:${cluster.free}:${cluster.busy}:${cluster.totalPorts}:$selected"
        }
        return cache.getOrPut(key) { draw(resources, cluster, selected) }
    }

    fun clear() = cache.clear()

    private fun draw(resources: Resources, cluster: Cluster, selected: Boolean): Drawable {
        val density = resources.displayMetrics.density
        val size = (SIZE_DP * density * if (selected) 1.25f else 1f)
        val px = size.toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val ring = RING_DP * density * if (selected) 1.25f else 1f
        val pad = ring / 2f + density
        val rect = RectF(pad, pad, px - pad, px - pad)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawCircle(px / 2f, px / 2f, px / 2f - density, fill)

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ring
            strokeCap = Paint.Cap.BUTT
        }

        val total = cluster.totalPorts
        if (cluster.unknownBusy || total <= 0) {
            stroke.color = OFF
            canvas.drawArc(rect, 0f, 360f, false, stroke)
        } else {
            // Сегменты идут от верхней точки по часовой стрелке; между ними
            // оставлен зазор, иначе на трёх портах кольцо читается как сплошное.
            //
            // Зазор обязан быть долей шага, а не константой: у группы из
            // 86 портов шаг равен 4,2°, и фиксированные 4° съедали сегмент
            // целиком — кольцо пропадало с карты.
            val step = 360f / total
            val gap = if (total > 1) minOf(4f, step * 0.3f) else 0f
            var angle = -90f
            val free = cluster.free.coerceIn(0, total)
            val busy = cluster.busy.coerceIn(0, total - free)
            // Остаток кольца всегда серый. Если сумма счётчиков меньше total,
            // непокрашенная дуга выглядела бы как дефект отрисовки.
            val offline = total - free - busy
            val order = listOf(FREE to free, BUSY to busy, OFF to offline)
            for ((color, count) in order) {
                repeat(count) {
                    stroke.color = color
                    canvas.drawArc(rect, angle + gap / 2f, step - gap, false, stroke)
                    angle += step
                }
            }
        }

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x1F, 0x29, 0x37)
            textAlign = Paint.Align.CENTER
            textSize = px * 0.42f
            isFakeBoldText = true
        }
        val baseline = px / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(total.toString(), px / 2f, baseline, text)

        // В центре всегда число ПОРТОВ — так же, как на сайте. Сколько станций
        // свёрнуто в метку, показывает отдельный значок в углу: иначе одно
        // число означало бы то порты, то станции, и метка врала бы.
        val count = cluster.stations.size
        if (count > 1) {
            val badgeR = px * 0.20f
            val cx = px - badgeR - density
            val cy = badgeR + density
            val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = ACCENT
            }
            canvas.drawCircle(cx, cy, badgeR, badge)
            val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = badgeR * 1.25f
                isFakeBoldText = true
            }
            canvas.drawText(
                count.toString(),
                cx,
                cy - (badgeText.descent() + badgeText.ascent()) / 2f,
                badgeText,
            )
        }

        return BitmapDrawable(resources, bitmap)
    }
}
