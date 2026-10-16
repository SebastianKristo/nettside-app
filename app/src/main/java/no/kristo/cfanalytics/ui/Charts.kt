package no.kristo.cfanalytics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import no.kristo.cfanalytics.data.Aggregate
import no.kristo.cfanalytics.data.Metric
import no.kristo.cfanalytics.data.Period
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val NB: Locale = Locale.forLanguageTag("nb-NO")

/** nb-NO number format: space as thousands separator, comma as decimal. */
fun nf(v: Double, decimals: Int = 0): String = NumberFormat.getNumberInstance(NB).apply {
    minimumFractionDigits = decimals
    maximumFractionDigits = decimals
}.format(v)

data class ChartData(
    val bars: List<Double>,
    val total: Double,
    val avgPerDay: Double,
    val max: Double,
    val min: Double,
    val start: LocalDate,
    val hasData: Boolean,
)

/** Daily values for [metric]; visits fall back to uniques on days with no live point stored. */
fun seriesFor(history: Map<Metric, Map<LocalDate, Double>>, metric: Metric): Map<LocalDate, Double> {
    val own = history[metric].orEmpty()
    return if (metric == Metric.VISITS) history[Metric.UNIQUES].orEmpty() + own else own
}

fun buildChart(series: Map<LocalDate, Double>, metric: Metric, period: Period, today: LocalDate = LocalDate.now()): ChartData {
    val span = period.bars * period.bucketDays
    val start = today.minusDays((span - 1).toLong())
    val bars = (0 until period.bars).map { b ->
        val days = (0 until period.bucketDays).mapNotNull { series[start.plusDays((b * period.bucketDays + it).toLong())] }
        if (metric.aggregate == Aggregate.SUM) days.sum() else days.maxOrNull() ?: 0.0
    }
    val daily = (0 until span).mapNotNull { series[start.plusDays(it.toLong())] }
    val total = if (metric.aggregate == Aggregate.SUM) daily.sum() else daily.lastOrNull() ?: 0.0
    return ChartData(
        bars = bars,
        total = total,
        avgPerDay = if (daily.isEmpty()) 0.0 else daily.sum() / daily.size,
        max = daily.maxOrNull() ?: 0.0,
        min = daily.minOrNull() ?: 0.0,
        start = start,
        hasData = daily.isNotEmpty(),
    )
}

fun startLabel(start: LocalDate, period: Period): String =
    if (period == Period.Y1) DateTimeFormatter.ofPattern("MMM yy", NB).format(start)
    else "${start.dayOfMonth}.${start.monthValue}."

/** Bars with the last one in full colour and the rest at 70 % alpha. */
@Composable
fun BarChart(values: List<Double>, color: Color, height: Dp, modifier: Modifier = Modifier) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { i, v ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((v / max).toFloat().coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (i == values.lastIndex) color else color.copy(alpha = 0.7f))
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun Sparkline(points: List<Long>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val max = points.max().coerceAtLeast(1).toFloat()
        val stepX = size.width / (points.size - 1)
        val pad = size.height * 0.1f
        fun y(v: Long) = size.height - pad - (v / max) * (size.height - 2 * pad)
        val line = Path().apply {
            points.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * stepX, y(v)) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, color.copy(alpha = 0.18f))
        drawPath(line, color, style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(color, 3.dp.toPx(), Offset((points.size - 1) * stepX, y(points.last())))
    }
}
