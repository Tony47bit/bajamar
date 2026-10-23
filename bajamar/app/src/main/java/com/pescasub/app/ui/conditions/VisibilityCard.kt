package com.pescasub.app.ui.conditions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pescasub.app.domain.ForecastBundle
import com.pescasub.app.domain.visibility.SeaOutlook
import com.pescasub.app.domain.visibility.VisibilityEstimate
import com.pescasub.app.ui.Fmt
import com.pescasub.app.ui.SEA_OUTLOOK_NOTE
import com.pescasub.app.ui.SectionCard
import com.pescasub.app.ui.VISIBILITY_DISCLAIMER
import com.pescasub.app.ui.VISIBILITY_TITLE
import com.pescasub.app.ui.theme.RatingColors
import com.pescasub.app.ui.theme.VisibilityColors
import com.pescasub.app.ui.seaOutlookText
import com.pescasub.app.ui.visibilityConfidenceText
import com.pescasub.app.ui.visibilityFactorText
import com.pescasub.app.ui.visibilityNotEstimableText
import com.pescasub.app.ui.visibilityRangeShort
import com.pescasub.app.ui.visibilityScaleMax
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Presentación de la visibilidad estimada: un gráfico grande y el rango en metros; el detalle (factores y aviso)
 * queda plegado. No contiene lógica de cálculo (vive en domain/visibility).
 */
@Composable
fun VisibilityCard(
    estimate: VisibilityEstimate?,
    loading: Boolean,
    bundle: ForecastBundle,
    modifier: Modifier = Modifier,
    forTomorrow: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(VISIBILITY_TITLE, modifier) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (estimate) {
            null -> if (!loading) Text("Sin estimación todavía.", style = MaterialTheme.typography.bodyMedium)

            is VisibilityEstimate.Estimated -> {
                Headline(visibilityRangeShort(estimate.minMeters, estimate.maxMeters), estimate.upperCapped)
                VisibilityGauge(estimate.minMeters, estimate.maxMeters, solid = true)
                Tag(visibilityConfidenceText(estimate.confidence))
                Caption("Satélite · ${Fmt.dateTime(estimate.observedAt, bundle.zone)}")
            }

            is VisibilityEstimate.Climatological -> {
                Headline(visibilityRangeShort(estimate.minMeters, estimate.maxMeters), estimate.upperCapped)
                VisibilityGauge(estimate.minMeters, estimate.maxMeters, solid = false)
                Tag("Valor típico de la zona · confianza baja")
                val month = java.time.LocalDate.ofInstant(bundle.fetchedAt, bundle.zone).month
                Caption(
                    "No es el valor de hoy: es lo habitual en ${monthName(month)} según años anteriores " +
                        "(sin observación de satélite reciente)."
                )
            }

            is VisibilityEstimate.SeaStateOnly -> {
                val worse = estimate.outlook == SeaOutlook.WORSE_THAN_USUAL
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (worse) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (worse) RatingColors.Fair else RatingColors.Good,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(seaOutlookText(estimate.outlook), style = MaterialTheme.typography.titleLarge)
                }
                Caption(SEA_OUTLOOK_NOTE)
            }

            is VisibilityEstimate.NotEstimable ->
                Text(visibilityNotEstimableText(estimate.reason), style = MaterialTheme.typography.bodyMedium)
        }
        if (forTomorrow && estimate != null && estimate !is VisibilityEstimate.NotEstimable) {
            Caption("No es una previsión de mañana: es la estimación actual, que puede cambiar con el mar.")
        }
        if (estimate != null) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Ocultar detalles" else "¿Cómo se calcula?")
            }
            if (expanded) {
                factorsOf(estimate).forEach {
                    Caption("• ${visibilityFactorText(it)}")
                }
                Caption(VISIBILITY_DISCLAIMER)
            }
        }
    }
}

private fun factorsOf(e: VisibilityEstimate) = when (e) {
    is VisibilityEstimate.Estimated -> e.factors
    is VisibilityEstimate.Climatological -> e.factors
    is VisibilityEstimate.SeaStateOnly -> e.factors
    is VisibilityEstimate.NotEstimable -> emptySet()
}

private fun monthName(m: Month): String = m.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-ES"))

@Composable
private fun Headline(range: String, capped: Boolean) {
    Column {
        Text(range, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        if (capped) {
            Caption("El mar o la lluvia recientes pueden enturbiar el agua: el máximo real tiende a ser menor.")
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Escala horizontal de turbia (rojo) a clara (turquesa) con el rango estimado resaltado. Con `solid = false`
 * (valor típico, no de hoy) el rango se dibuja punteado. Las cifras de los extremos acompañan siempre al color.
 */
@Composable
private fun VisibilityGauge(minMeters: Int, maxMeters: Int, solid: Boolean) {
    val scale = visibilityScaleMax(maxMeters)
    val outline: Color = MaterialTheme.colorScheme.onSurface
    val description = "Escala de 0 a $scale metros; rango estimado de $minMeters a $maxMeters metros"
    Column(Modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(
                brush = Brush.horizontalGradient(VisibilityColors.Scale),
                size = size,
                cornerRadius = radius,
                alpha = if (solid) 1f else 0.55f
            )
            val x0 = size.width * (minMeters.toFloat() / scale)
            val x1 = size.width * (maxMeters.toFloat() / scale)
            drawRoundRect(
                color = outline,
                topLeft = Offset(x0, 0f),
                size = Size((x1 - x0).coerceAtLeast(4f), size.height),
                cornerRadius = radius,
                style = Stroke(
                    width = 4f,
                    pathEffect = if (solid) null else PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                )
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Caption("0 m")
            Caption("${scale / 2} m")
            Caption("$scale m")
        }
    }
}
