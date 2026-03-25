package com.github.irdcat.workout.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Dumbbell: ImageVector
    get() {
        if (_dumbbell != null) return _dumbbell!!

        _dumbbell = ImageVector.Builder(
            name = "dumbbell",
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
                moveTo(17.596f, 12.768f)
                arcToRelative(2f, 2f, 0f,
                    isMoreThanHalf = true,
                    isPositiveArc = false,
                    dx1 = 2.829f,
                    dy1 = -2.829f
                )
                lineToRelative(-1.768f, -1.767f)
                arcToRelative(2f, 2f, 0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    dx1 = 2.828f,
                    dy1 = -2.829f
                )
                lineToRelative(-2.828f, -2.828f)
                arcToRelative(2f, 2f, 0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    dx1 = -2.829f,
                    dy1 = 2.828f
                )
                lineToRelative(-1.767f, -1.768f)
                arcToRelative(2f, 2f, 0f,
                    isMoreThanHalf = true,
                    isPositiveArc = false,
                    dx1 = -2.829f,
                    dy1 = 2.829f
                )
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveToRelative(2.5f, 21.5f)
                lineToRelative(1.4f, -1.4f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveToRelative(20.1f, 3.9f)
                lineToRelative(1.4f, -1.4f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5.343f, 21.485f)
                arcToRelative(2f, 2f, 0f,
                    isMoreThanHalf = true,
                    isPositiveArc = false,
                    dx1 = 2.829f,
                    dy1 = -2.828f
                )
                lineToRelative(1.767f, 1.768f)
                arcToRelative(2f, 2f, 0f,
                    isMoreThanHalf = true,
                    isPositiveArc = false,
                    dx1 = 2.829f,
                    dy1 = -2.829f
                )
                lineToRelative(-6.364f, -6.364f)
                arcToRelative(2f, 2f, 0f,
                    isMoreThanHalf = true,
                    isPositiveArc = false,
                    dx1 = -2.829f,
                    dy1 = 2.829f
                )
                lineToRelative(1.768f, 1.767f)
                arcToRelative(2f, 2f, 0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    dx1 = -2.828f,
                    dy1 = 2.829f
                )
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveToRelative(9.6f, 14.4f)
                lineToRelative(4.8f, -4.8f)
            }
        }.build()

        return _dumbbell!!
    }

private var _dumbbell: ImageVector? = null
