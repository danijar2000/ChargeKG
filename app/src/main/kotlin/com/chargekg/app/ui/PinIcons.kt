package com.chargekg.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.content.res.Resources
import com.chargekg.app.data.Station

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

    private val cache = HashMap<String, Drawable>()

    fun forStation(resources: Resources, station: Station, selected: Boolean): Drawable {
        val key = if (station.unknownBusy) {
            "u:${station.total}:$selected"
        } else {
            "${station.free}:${station.busy}:${station.offline}:${station.total}:$selected"
        }
        return cache.getOrPut(key) { draw(resources, station, selected) }
    }

    fun clear() = cache.clear()

    private fun draw(resources: Resources, station: Station, selected: Boolean): Drawable {
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

        val total = station.total
        if (station.unknownBusy || total <= 0) {
            stroke.color = OFF
            canvas.drawArc(rect, 0f, 360f, false, stroke)
        } else {
            // Сегменты идут от верхней точки по часовой стрелке; между ними
            // оставлен зазор, иначе на трёх портах кольцо читается как сплошное.
            val gap = if (total > 1) 4f else 0f
            val step = 360f / total
            var angle = -90f
            val free = (station.free ?: 0).coerceIn(0, total)
            val busy = (station.busy ?: 0).coerceIn(0, total - free)
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

        return BitmapDrawable(resources, bitmap)
    }
}
