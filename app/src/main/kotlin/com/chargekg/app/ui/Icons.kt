package com.chargekg.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Иконки рисуются здесь, а не берутся из material-icons-extended: тот пакет
 * тянет тысячи векторов ради десятка нужных и заметно раздувает APK.
 */
private fun icon(name: String, pathData: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black), pathBuilder = pathData)
    }.build()

object AppIcons {
    val Close = icon("Close") {
        moveTo(19f, 6.41f); lineTo(17.59f, 5f); lineTo(12f, 10.59f); lineTo(6.41f, 5f)
        lineTo(5f, 6.41f); lineTo(10.59f, 12f); lineTo(5f, 17.59f); lineTo(6.41f, 19f)
        lineTo(12f, 13.41f); lineTo(17.59f, 19f); lineTo(19f, 17.59f); lineTo(13.41f, 12f)
        close()
    }

    val Filter = icon("Filter") {
        moveTo(3f, 5f); lineTo(21f, 5f); lineTo(14f, 13f); lineTo(14f, 20f)
        lineTo(10f, 18f); lineTo(10f, 13f); close()
    }

    val List = icon("List") {
        moveTo(3f, 6f); lineTo(21f, 6f); lineTo(21f, 8f); lineTo(3f, 8f); close()
        moveTo(3f, 11f); lineTo(21f, 11f); lineTo(21f, 13f); lineTo(3f, 13f); close()
        moveTo(3f, 16f); lineTo(21f, 16f); lineTo(21f, 18f); lineTo(3f, 18f); close()
    }

    val MyLocation = icon("MyLocation") {
        moveTo(12f, 8f)
        arcToRelative(4f, 4f, 0f, true, false, 0.001f, 8.001f)
        arcToRelative(4f, 4f, 0f, true, false, -0.001f, -8.001f)
        close()
        moveTo(20.94f, 11f)
        curveToRelative(-0.46f, -4.17f, -3.77f, -7.48f, -7.94f, -7.94f)
        lineTo(13f, 1f); lineTo(11f, 1f); lineTo(11f, 3.06f)
        curveTo(6.83f, 3.52f, 3.52f, 6.83f, 3.06f, 11f)
        lineTo(1f, 11f); lineTo(1f, 13f); lineTo(3.06f, 13f)
        curveToRelative(0.46f, 4.17f, 3.77f, 7.48f, 7.94f, 7.94f)
        lineTo(11f, 23f); lineTo(13f, 23f); lineTo(13f, 20.94f)
        curveToRelative(4.17f, -0.46f, 7.48f, -3.77f, 7.94f, -7.94f)
        lineTo(23f, 13f); lineTo(23f, 11f); close()
        moveTo(12f, 19f)
        curveToRelative(-3.87f, 0f, -7f, -3.13f, -7f, -7f)
        reflectiveCurveToRelative(3.13f, -7f, 7f, -7f)
        reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
        reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
        close()
    }

    val Settings = icon("Settings") {
        moveTo(12f, 8f)
        arcToRelative(4f, 4f, 0f, true, false, 0.001f, 8.001f)
        arcToRelative(4f, 4f, 0f, true, false, -0.001f, -8.001f)
        close()
        moveTo(19.4f, 13f)
        lineToRelative(2.1f, 1.6f); lineToRelative(-2f, 3.5f); lineToRelative(-2.5f, -1f)
        arcToRelative(7.6f, 7.6f, 0f, false, true, -1.7f, 1f)
        lineToRelative(-0.4f, 2.6f); lineToRelative(-4f, 0f); lineToRelative(-0.4f, -2.6f)
        arcToRelative(7.6f, 7.6f, 0f, false, true, -1.7f, -1f)
        lineToRelative(-2.5f, 1f); lineToRelative(-2f, -3.5f); lineToRelative(2.1f, -1.6f)
        arcToRelative(7.6f, 7.6f, 0f, false, true, 0f, -2f)
        lineTo(2.5f, 9.4f); lineToRelative(2f, -3.5f); lineToRelative(2.5f, 1f)
        arcToRelative(7.6f, 7.6f, 0f, false, true, 1.7f, -1f)
        lineTo(9.1f, 3.3f); lineToRelative(4f, 0f); lineToRelative(0.4f, 2.6f)
        arcToRelative(7.6f, 7.6f, 0f, false, true, 1.7f, 1f)
        lineToRelative(2.5f, -1f); lineToRelative(2f, 3.5f); lineTo(19.4f, 11f)
        arcToRelative(7.6f, 7.6f, 0f, false, true, 0f, 2f)
        close()
    }

    val Share = icon("Share") {
        moveTo(18f, 16.08f)
        curveToRelative(-0.76f, 0f, -1.44f, 0.3f, -1.96f, 0.77f)
        lineTo(8.91f, 12.7f)
        curveToRelative(0.05f, -0.23f, 0.09f, -0.46f, 0.09f, -0.7f)
        reflectiveCurveToRelative(-0.04f, -0.47f, -0.09f, -0.7f)
        lineToRelative(7.05f, -4.11f)
        curveTo(16.5f, 7.69f, 17.21f, 8f, 18f, 8f)
        arcToRelative(3f, 3f, 0f, true, false, -3f, -3f)
        curveToRelative(0f, 0.24f, 0.04f, 0.47f, 0.09f, 0.7f)
        lineTo(8.04f, 9.81f)
        curveTo(7.5f, 9.31f, 6.79f, 9f, 6f, 9f)
        arcToRelative(3f, 3f, 0f, true, false, 0f, 6f)
        curveToRelative(0.79f, 0f, 1.5f, -0.31f, 2.04f, -0.81f)
        lineToRelative(7.12f, 4.16f)
        curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
        curveToRelative(0f, 1.61f, 1.31f, 2.92f, 2.92f, 2.92f)
        reflectiveCurveToRelative(2.92f, -1.31f, 2.92f, -2.92f)
        reflectiveCurveToRelative(-1.31f, -2.92f, -2.92f, -2.92f)
        close()
    }

    val Route = icon("Route") {
        moveTo(21.7f, 11.3f); lineToRelative(-9f, -9f)
        curveToRelative(-0.4f, -0.4f, -1f, -0.4f, -1.4f, 0f)
        lineToRelative(-9f, 9f)
        curveToRelative(-0.4f, 0.4f, -0.4f, 1f, 0f, 1.4f)
        lineToRelative(9f, 9f)
        curveToRelative(0.4f, 0.4f, 1f, 0.4f, 1.4f, 0f)
        lineToRelative(9f, -9f)
        curveToRelative(0.4f, -0.4f, 0.4f, -1f, 0f, -1.4f)
        close()
        moveTo(14f, 14.5f); lineTo(14f, 12f); lineTo(10f, 12f); lineTo(10f, 15f)
        lineTo(8f, 15f); lineTo(8f, 11f)
        curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
        lineToRelative(5f, 0f); lineTo(14f, 7.5f); lineTo(17.5f, 11f)
        close()
    }

    val Refresh = icon("Refresh") {
        moveTo(17.65f, 6.35f)
        arcTo(7.958f, 7.958f, 0f, false, false, 12f, 4f)
        curveToRelative(-4.42f, 0f, -7.99f, 3.58f, -8f, 8f)
        reflectiveCurveToRelative(3.58f, 8f, 8f, 8f)
        curveToRelative(3.73f, 0f, 6.84f, -2.55f, 7.73f, -6f)
        horizontalLineToRelative(-2.08f)
        arcToRelative(5.99f, 5.99f, 0f, false, true, -5.65f, 4f)
        curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
        reflectiveCurveToRelative(2.69f, -6f, 6f, -6f)
        curveToRelative(1.66f, 0f, 3.14f, 0.69f, 4.22f, 1.78f)
        lineTo(13f, 11f); lineToRelative(7f, 0f); lineTo(20f, 4f)
        close()
    }

    val Info = icon("Info") {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
        reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
        reflectiveCurveTo(17.52f, 2f, 12f, 2f)
        close()
        moveTo(13f, 17f); lineTo(11f, 17f); lineTo(11f, 11f); lineTo(13f, 11f); close()
        moveTo(13f, 9f); lineTo(11f, 9f); lineTo(11f, 7f); lineTo(13f, 7f); close()
    }

    val Copy = icon("Copy") {
        moveTo(16f, 1f); lineTo(4f, 1f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        lineTo(2f, 17f); lineTo(4f, 17f); lineTo(4f, 3f); lineTo(16f, 3f); close()
        moveTo(19f, 5f); lineTo(8f, 5f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        lineTo(6f, 21f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        lineTo(19f, 23f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        lineTo(21f, 7f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        close()
        moveTo(19f, 21f); lineTo(8f, 21f); lineTo(8f, 7f); lineTo(19f, 7f); close()
    }

    val OpenApp = icon("OpenApp") {
        moveTo(19f, 19f); lineTo(5f, 19f); lineTo(5f, 5f); lineTo(12f, 5f); lineTo(12f, 3f)
        lineTo(5f, 3f)
        curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
        lineTo(3f, 19f)
        curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
        lineTo(19f, 21f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        lineTo(21f, 12f); lineTo(19f, 12f); close()
        moveTo(14f, 3f); lineTo(14f, 5f); lineTo(17.59f, 5f); lineTo(7.76f, 14.83f)
        lineTo(9.17f, 16.24f); lineTo(19f, 6.41f); lineTo(19f, 10f); lineTo(21f, 10f)
        lineTo(21f, 3f); close()
    }
}
