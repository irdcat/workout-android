package com.github.irdcat.workout.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Timer: ImageVector
    get() {
        if (_timer != null) return _timer!!

        _timer = ImageVector.Builder(
            name = "timer",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 2f)
                lineTo(14f, 2f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 14f)
                lineTo(15f, 11f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 14f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 12f, y1 = 22f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 4f, y1 = 14f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 20f, y1 = 14f)
                close()
            }
        }.build()

        return _timer!!
    }

private var _timer: ImageVector? = null