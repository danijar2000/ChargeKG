package com.chargekg.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp
import com.chargekg.app.data.ConnectorType

/**
 * Значки разъёмов — те же, что на сайте (`web/app.js`, таблицы CONNECTORS,
 * CCS_UPPER и CCS_PINS).
 *
 * Формы перенесены дословно, теми же строками пути и в том же поле 32×32:
 * они выверены под настоящие разъёмы, и «поправленный» пин превращает GB/T в
 * несуществующий тип. Поэтому пути разбираются PathParser'ом как есть, а не
 * переписываются в вызовы Compose — так их можно сверить с сайтом глазами.
 *
 * Рисуются обводкой чёрным; цвет задаёт Icon(tint = …), как и в [AppIcons].
 */
object ConnectorIcons {

    private const val OUTLINE_WIDTH = 2.1f
    private const val PIN_WIDTH = 1.9f

    /** Корпус с плоским скосом сверху — общий у GB/T и Type 2. */
    private const val BODY_FLAT = "M4.8 12.4 L8.6 7.6 H23.4 L27.2 12.4 A12 12 0 1 1 4.8 12.4 Z"

    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float): String =
        "M${x + rx},$y H${x + w - rx} A$rx,$rx 0 0 1 ${x + w},${y + rx} " +
            "V${y + h - rx} A$rx,$rx 0 0 1 ${x + w - rx},${y + h} " +
            "H${x + rx} A$rx,$rx 0 0 1 $x,${y + h - rx} " +
            "V${y + rx} A$rx,$rx 0 0 1 ${x + rx},$y Z"

    /** Путь и его толщина: часть пинов на сайте тоньше общей группы. */
    private data class Stroke(val d: String, val width: Float)

    private fun outline(d: String) = Stroke(d, OUTLINE_WIDTH)
    private fun pin(d: String, width: Float = PIN_WIDTH) = Stroke(d, width)

    private fun build(name: String, strokes: List<Stroke>): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 32f,
            viewportHeight = 32f,
        ).apply {
            strokes.forEach { addStroke(it) }
        }.build()

    private fun ImageVector.Builder.addStroke(s: Stroke) {
        addPath(
            pathData = PathParser().parsePathString(s.d).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = s.width,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    // У CCS нижняя часть общая: два усика и «карман» с двумя силовыми пинами.
    private val ccsLower = listOf(
        outline("M13.8 21 v2.6"),
        outline("M18.2 21 v2.6"),
        outline(rect(3.4f, 22.8f, 25.2f, 8.2f, 4.1f)),
        pin(circle(10.6f, 26.9f, 2.8f)),
        pin(circle(21.4f, 26.9f, 2.8f)),
    )

    private val type2 = build(
        "Type2", listOf(
            outline(BODY_FLAT),
            pin(circle(13f, 11.8f, 1.5f), 1.5f),
            pin(circle(19f, 11.8f, 1.5f), 1.5f),
            pin(circle(9.6f, 16.8f, 2.8f)),
            pin(circle(16f, 16.8f, 2.8f)),
            pin(circle(22.4f, 16.8f, 2.8f)),
            pin(circle(12.8f, 22.4f, 2.5f)),
            pin(circle(19.2f, 22.4f, 2.5f)),
        )
    )

    private val gbtAc = build(
        "GBT_AC", listOf(
            outline(BODY_FLAT),
            pin(circle(11.6f, 11.4f, 1.4f), 1.5f),
            pin(circle(16f, 11.4f, 1.4f), 1.5f),
            pin(circle(20.4f, 11.4f, 1.4f), 1.5f),
            pin(circle(16f, 15.6f, 1.2f), 1.4f),
            pin(circle(10f, 19f, 3.3f)),
            pin(circle(22f, 19f, 3.3f)),
            pin(circle(12.4f, 24.4f, 1.4f), 1.5f),
            pin(circle(16f, 23.4f, 2.1f)),
            pin(circle(19.6f, 24.4f, 1.4f), 1.5f),
        )
    )

    private val gbtDc = build(
        "GBT_DC", listOf(
            outline(BODY_FLAT),
            pin(circle(16f, 11.2f, 1.4f), 1.5f),
            pin(circle(9.6f, 17.2f, 4f)),
            pin(circle(22.4f, 17.2f, 4f)),
            pin(circle(16f, 17.2f, 1.4f), 1.5f),
            pin(circle(12.6f, 24f, 1.4f), 1.5f),
            pin(circle(16f, 24.8f, 1.4f), 1.5f),
            pin(circle(19.4f, 24f, 1.4f), 1.5f),
        )
    )

    // У CHAdeMO два верхних ушка развёрнуты — на сайте это rotate() у rect.
    private val chademo = ImageVector.Builder(
        name = "CHAdeMO",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 32f, viewportHeight = 32f,
    ).apply {
        addStroke(outline(circle(16f, 16f, 12f)))
        group(rotate = -38f, pivotX = 7.7f, pivotY = 4.9f) {
            addStroke(outline(rect(5.4f, 3.6f, 4.6f, 2.6f, 1.1f)))
        }
        group(rotate = 38f, pivotX = 24.3f, pivotY = 4.9f) {
            addStroke(outline(rect(22f, 3.6f, 4.6f, 2.6f, 1.1f)))
        }
        addStroke(outline(rect(13.6f, 27.2f, 4.8f, 2.6f, 1.1f)))
        addStroke(pin(circle(16f, 9.6f, 3.3f)))
        addStroke(pin(circle(16f, 9.6f, 0.9f), 1.3f))
        addStroke(pin(circle(8.8f, 16.8f, 3.5f)))
        addStroke(pin(circle(8.8f, 16.8f, 1.5f), 1.5f))
        addStroke(pin(circle(23.2f, 16.8f, 3.5f)))
        addStroke(pin(circle(23.2f, 16.8f, 1.5f), 1.5f))
        addStroke(pin(circle(16f, 24f, 3.3f)))
        addStroke(pin(circle(16f, 24f, 0.9f), 1.3f))
    }.build()

    private val type1 = build(
        "Type1", listOf(
            outline(circle(16f, 16.4f, 11.6f)),
            outline(rect(13.4f, 2.6f, 5.2f, 3f, 1f)),
            outline(rect(13.8f, 27.2f, 4.4f, 2.6f, 1f)),
            pin(circle(11.4f, 13f, 3.1f)),
            pin(circle(20.6f, 13f, 3.1f)),
            pin(circle(9.6f, 19.6f, 1.5f), 1.5f),
            pin(circle(22.4f, 19.6f, 1.5f), 1.5f),
            pin(circle(16f, 21.4f, 2.7f)),
        )
    )

    private val tesla = build(
        "Tesla", listOf(
            outline(rect(3.4f, 5.6f, 25.2f, 21.6f, 9.4f)),
            pin(circle(11.2f, 14.4f, 3.9f)),
            pin(circle(20.8f, 14.4f, 3.9f)),
            pin(circle(16f, 22.6f, 2.1f)),
            pin(circle(11.2f, 23.6f, 1.2f), 1.4f),
            pin(circle(20.8f, 23.6f, 1.2f), 1.4f),
        )
    )

    private val ccs2 = build(
        "CCS2", listOf(
            outline("M7.4 9.4 L10.2 6 H21.8 L24.6 9.4 A9.4 9.4 0 1 1 7.4 9.4 Z"),
            pin(circle(13.6f, 8.6f, 1.2f), 1.4f),
            pin(circle(18.4f, 8.6f, 1.2f), 1.4f),
            pin(circle(11.2f, 12.6f, 2.2f)),
            pin(circle(16f, 12.6f, 2.2f)),
            pin(circle(20.8f, 12.6f, 2.2f)),
            pin(circle(13.4f, 16.8f, 2f)),
            pin(circle(18.6f, 16.8f, 2f)),
        ) + ccsLower
    )

    private val ccs1 = build(
        "CCS1", listOf(
            outline(circle(16f, 12.6f, 9.4f)),
            outline(rect(13.6f, 1.6f, 4.8f, 2.6f, 1f)),
            pin(circle(12.6f, 10f, 2.4f)),
            pin(circle(19.4f, 10f, 2.4f)),
            pin(circle(11.4f, 15.4f, 1.2f), 1.4f),
            pin(circle(20.6f, 15.4f, 1.2f), 1.4f),
            pin(circle(16f, 16.4f, 2.1f)),
        ) + ccsLower
    )

    /** null — для UNKNOWN значка нет, у него остаётся только подпись сети. */
    fun forType(type: ConnectorType?): ImageVector? = when (type) {
        ConnectorType.TYPE2_AC -> type2
        ConnectorType.GBT_AC -> gbtAc
        ConnectorType.GBT_DC -> gbtDc
        ConnectorType.CHADEMO -> chademo
        ConnectorType.TYPE1_AC -> type1
        ConnectorType.TESLA -> tesla
        ConnectorType.CCS2 -> ccs2
        ConnectorType.CCS1 -> ccs1
        null -> null
    }
}
