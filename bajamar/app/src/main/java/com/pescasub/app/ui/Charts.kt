package com.pescasub.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class ChartSeries(val name: String, val color: Color, val values: List<Double?>)

/**
 * Gráfico de líneas sencillo. Los valores nulos dejan huecos (no se interpolan ni se dibujan como 0).
 * Si no hay ningún dato muestra un texto en su lugar.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    xLabels: List<String>,
    unit: String,
    modifier: Modifier = Modifier,
    decimals: Int = 1,
    zeroBased: Boolean = false,
    emptyText: String = "Sin datos disponibles para este día."
) {
    val all = series.flatMap { it.values }.filterNotNull()
    if (all.isEmpty()) {
        Text(
            emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }
    val dataMin = all.min()
    val dataMax = all.max()
    var lo = if (zeroBased) minOf(0.0, dataMin) else dataMin
    var hi = dataMax
    if (hi - lo < 0.001) hi = lo + 1.0
    val pad = (hi - lo) * 0.08
    if (!zeroBased) lo -= pad
    hi += pad
    val span = hi - lo

    val grid = MaterialTheme.colorScheme.outlineVariant
    val description = series.joinToString(", ") { it.name } +
        ": de ${Fmt.number(dataMin, decimals, unit)} a ${Fmt.number(dataMax, decimals, unit)}"

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                    Spacer(Modifier.width(4.dp))
                    Text(s.name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            "Máx. ${Fmt.number(dataMax, decimals, unit)} · mín. ${Fmt.number(dataMin, decimals, unit)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(
            Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }
        ) {
            val w = size.width
            val h = size.height
            for (i in 0..2) {
                val y = h * i / 2f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }
            series.forEach { s ->
                val n = s.values.size
                val path = Path()
                var started = false
                s.values.forEachIndexed { i, v ->
                    if (v == null) {
                        started = false
                    } else {
                        val x = if (n <= 1) w / 2f else w * i / (n - 1)
                        val y = h - ((v - lo) / span).toFloat() * h
                        if (!started) {
                            path.moveTo(x, y)
                            started = true
                        } else {
                            path.lineTo(x, y)
                        }
                        drawCircle(s.color, radius = 2.5.dp.toPx(), center = Offset(x, y))
                    }
                }
                drawPath(
                    path,
                    s.color,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
        if (xLabels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val picks = listOf(0, xLabels.lastIndex / 2, xLabels.lastIndex).distinct()
                picks.forEach { Text(xLabels[it], style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

/** Barra horizontal proporcional (para distribución por especie, etc.). */
@Composable
fun HorizontalBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(10.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}
