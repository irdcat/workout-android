package com.github.irdcat.workout.application.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.TextStyles
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * A week-by-week chart of one metric: planned as a dashed, hollow series, actual as a solid,
 * filled one. Tapping a week selects it. Weeks with nothing are gaps, never zeros.
 */
@Composable
fun AnalysisChart(
    chart: MetricPanel.Chart,
    weeks: List<ChartWeek>,
    selectedWeek: Int?,
    showPlanned: Boolean,
    showActual: Boolean,
    onSelectWeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val planned = if (showPlanned) chart.planned else chart.planned.map { null }
    val actual = if (showActual) chart.actual else chart.actual.map { null }
    val axis = chart.axis ?: niceAxis(planned + actual, fromZero = chart.kind == ChartKind.Bars)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .semantics { contentDescription = describe(chart, weeks, selectedWeek) }
            .pointerInput(weeks.size) {
                detectTapGestures { offset ->
                    val left = AxisWidth.toPx()
                    if (offset.x >= left && weeks.isNotEmpty()) {
                        val slot = (size.width - left) / weeks.size
                        onSelectWeek(((offset.x - left) / slot).toInt().coerceIn(0, weeks.lastIndex))
                    }
                }
            },
    ) {
        val left = AxisWidth.toPx()
        val frame = ChartFrame(
            left = left,
            top = 6.dp.toPx(),
            bottom = size.height - LabelHeight.toPx(),
            slot = (size.width - left) / max(weeks.size, 1),
            axis = axis,
        )
        // The selected week stands out behind everything else.
        selectedWeek?.let { drawSelection(frame, it) }
        drawGrid(frame, measurer)
        drawWeekLabels(frame, weeks, measurer)
        when (chart.kind) {
            ChartKind.Bars -> drawBars(frame, planned, actual, sideBySide = showPlanned && showActual)
            ChartKind.Lines -> {
                drawSeries(planned, frame::x, frame::y, planned = true)
                drawSeries(actual, frame::x, frame::y, planned = false)
            }
        }
        drawLine(Colors.Surface3, Offset(left, frame.bottom), Offset(size.width, frame.bottom), strokeWidth = 1.5.dp.toPx())
    }
}

/** Where things go on the canvas: a slot per week across, [axis] up from [bottom] to [top]. */
private class ChartFrame(
    val left: Float,
    val top: Float,
    val bottom: Float,
    val slot: Float,
    val axis: ClosedFloatingPointRange<Double>,
) {
    fun x(index: Int) = left + slot * index + slot / 2
    fun y(value: Double) = (bottom - (value - axis.start) / (axis.endInclusive - axis.start) * (bottom - top)).toFloat()
}

private fun DrawScope.drawSelection(frame: ChartFrame, index: Int) {
    drawRoundRect(
        color = Colors.Surface2,
        topLeft = Offset(frame.left + frame.slot * index + 2.dp.toPx(), 0f),
        size = Size(frame.slot - 4.dp.toPx(), frame.bottom + 2.dp.toPx()),
        cornerRadius = CornerRadius(6.dp.toPx()),
    )
}

/** Three recessive gridlines with their values. */
private fun DrawScope.drawGrid(frame: ChartFrame, measurer: TextMeasurer) {
    val axis = frame.axis
    listOf(axis.start, (axis.start + axis.endInclusive) / 2, axis.endInclusive).forEach { tick ->
        val y = frame.y(tick)
        drawLine(Colors.Border, Offset(frame.left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        drawLabel(measurer, formatValue(tick), Offset(frame.left - 6.dp.toPx(), y), AxisStyle, alignEnd = true)
    }
}

/** This week's label highlighted, ones still to come dimmed; thinned out when crowded. */
private fun DrawScope.drawWeekLabels(frame: ChartFrame, weeks: List<ChartWeek>, measurer: TextMeasurer) {
    val every = ceil(weeks.size / MAX_WEEK_LABELS.toDouble()).toInt().coerceAtLeast(1)
    weeks.forEachIndexed { index, week ->
        if (index % every != 0 && !week.current) return@forEachIndexed
        val style = when {
            week.current -> WeekStyle.copy(color = Colors.Primary, fontWeight = FontWeight.Bold)
            week.future -> WeekStyle.copy(color = Colors.TextSecondary.copy(alpha = 0.6f))
            else -> WeekStyle
        }
        drawLabel(measurer, week.label, Offset(frame.x(index), size.height - LabelHeight.toPx() / 2), style, center = true)
    }
}

/** Planned as a dashed outline, actual filled; [sideBySide] when both are shown. */
private fun DrawScope.drawBars(frame: ChartFrame, planned: List<Double?>, actual: List<Double?>, sideBySide: Boolean) {
    val width = min(12.dp.toPx(), frame.slot * 0.34f)
    val gap = 2.dp.toPx()
    planned.indices.forEach { index ->
        val center = frame.x(index)
        planned[index]?.let { value ->
            val start = if (sideBySide) center - width - gap / 2 else center - width / 2
            drawRoundRect(
                color = Colors.TextSecondary,
                topLeft = Offset(start, frame.y(value)),
                size = Size(width, frame.bottom - frame.y(value)),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashed()),
            )
        }
        actual[index]?.let { value ->
            val start = if (sideBySide) center + gap / 2 else center - width / 2
            drawRoundRect(
                color = Colors.Primary,
                topLeft = Offset(start, frame.y(value)),
                size = Size(width, frame.bottom - frame.y(value)),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}

/** More weeks than this and only every other (third, …) is labelled. */
private const val MAX_WEEK_LABELS = 8

/** From here on values are rounded and grouped: "2,185". */
private const val GROUPED_FROM = 1000

private val AxisWidth = 36.dp
private val LabelHeight = 20.dp
private val AxisStyle = TextStyle(
    fontFamily = TextStyles.dataValue.fontFamily,
    fontSize = 10.sp,
    fontWeight = FontWeight.SemiBold,
    color = Colors.TextSecondary,
)
private val WeekStyle = TextStyle(
    fontFamily = TextStyles.bodySm.fontFamily,
    fontSize = 10.5.sp,
    fontWeight = FontWeight.Medium,
    color = Colors.TextSecondary,
)

private fun DrawScope.dashed() = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))

/** Lines join neighbouring weeks only, so a gap stays a gap. */
private fun DrawScope.drawSeries(values: List<Double?>, x: (Int) -> Float, y: (Double) -> Float, planned: Boolean) {
    val color = if (planned) Colors.TextSecondary else Colors.Primary
    val stroke = Stroke(
        width = if (planned) 2.dp.toPx() else 2.5.dp.toPx(),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
        pathEffect = if (planned) dashed() else null,
    )
    var path: Path? = null
    values.forEachIndexed { index, value ->
        if (value == null) {
            path?.let { drawPath(it, color, style = stroke) }
            path = null
        } else {
            val point = Offset(x(index), y(value))
            path = (path ?: Path().apply { moveTo(point.x, point.y) }).apply { lineTo(point.x, point.y) }
        }
    }
    path?.let { drawPath(it, color, style = stroke) }
    values.forEachIndexed { index, value ->
        value ?: return@forEachIndexed
        val center = Offset(x(index), y(value))
        if (planned) {
            drawCircle(Colors.Surface1, radius = 4.dp.toPx(), center = center)
            drawCircle(color, radius = 4.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
        } else {
            // A surface ring keeps the marker clear of the planned line beneath it.
            drawCircle(Colors.Surface1, radius = 6.5.dp.toPx(), center = center)
            drawCircle(color, radius = 4.5.dp.toPx(), center = center)
        }
    }
}

private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    anchor: Offset,
    style: TextStyle,
    alignEnd: Boolean = false,
    center: Boolean = false,
) {
    val layout = measurer.measure(text, style)
    val x = when {
        alignEnd -> anchor.x - layout.size.width
        center -> anchor.x - layout.size.width / 2
        else -> anchor.x
    }
    drawText(layout, topLeft = Offset(x, anchor.y - layout.size.height / 2))
}

/** A flat line still gets an axis: a fifth of its value. */
private const val FLAT_SPAN = 0.2

// Fine enough that the top gridline sits just above the data, e.g. 2,400 for 2,250.
private val NiceSteps = listOf(1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)

/** Round bounds holding every value: from 0 for bars, around the data for lines. */
internal fun niceAxis(values: List<Double?>, fromZero: Boolean): ClosedFloatingPointRange<Double> {
    val present = values.filterNotNull()
    val high = present.maxOrNull() ?: return 0.0..1.0
    val low = if (fromZero) 0.0 else present.min()
    val span = (high - low).takeIf { it > 0 } ?: (max(high, 1.0) * FLAT_SPAN)
    val step = niceStep(span / 2)
    val start = if (fromZero) 0.0 else floor(low / step) * step
    var end = ceil(high / step) * step
    // An even number of steps keeps the middle gridline on a round value.
    if (((end - start) / step).roundToLong() % 2 != 0L) end += step
    return start..end
}

private fun niceStep(raw: Double): Double {
    val magnitude = 10.0.pow(floor(log10(raw)))
    return NiceSteps.map { it * magnitude }.first { it >= raw }
}

/** "2,185" for big numbers, else up to one decimal: "92.5", "8". */
internal fun formatValue(value: Double): String =
    if (abs(value) >= GROUPED_FROM) {
        String.format(Locale.ENGLISH, "%,d", value.roundToLong())
    } else {
        BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

private fun describe(chart: MetricPanel.Chart, weeks: List<ChartWeek>, selectedWeek: Int?): String {
    val index = selectedWeek ?: return "${chart.title} by week, planned and actual"
    val week = weeks.getOrNull(index) ?: return "${chart.title} by week, planned and actual"
    fun value(v: Double?) = v?.let { "${formatValue(it)} ${chart.suffix}".trim() } ?: "none"
    return "${chart.title} by week, planned and actual. Week of ${week.label}: planned ${value(chart.planned[index])}, " +
        "actual ${value(chart.actual[index])}. Tap a week for its numbers."
}
