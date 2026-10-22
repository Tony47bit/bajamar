package com.pescasub.app.ui.conditions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pescasub.app.domain.ForecastBundle
import com.pescasub.app.domain.visibility.VisibilityEstimate
import com.pescasub.app.ui.Fmt
import com.pescasub.app.ui.SectionCard
import com.pescasub.app.ui.VISIBILITY_DISCLAIMER
import com.pescasub.app.ui.VISIBILITY_TITLE
import com.pescasub.app.ui.visibilityConfidenceText
import com.pescasub.app.ui.visibilityFactorText
import com.pescasub.app.ui.visibilityNotEstimableText
import com.pescasub.app.ui.visibilityRangeText

/** Presentación de la visibilidad estimada. No contiene lógica de cálculo (vive en domain/visibility). */
@Composable
fun VisibilityCard(
    estimate: VisibilityEstimate?,
    loading: Boolean,
    bundle: ForecastBundle,
    modifier: Modifier = Modifier,
    forTomorrow: Boolean = false
) {
    SectionCard(VISIBILITY_TITLE, modifier) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (estimate) {
            null -> if (!loading) {
                Text("Sin estimación todavía.", style = MaterialTheme.typography.bodyMedium)
            }
            is VisibilityEstimate.NotEstimable ->
                Text(visibilityNotEstimableText(estimate.reason), style = MaterialTheme.typography.bodyMedium)
            is VisibilityEstimate.Estimated -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(visibilityRangeText(estimate), style = MaterialTheme.typography.headlineSmall)
                Text(visibilityConfidenceText(estimate.confidence), style = MaterialTheme.typography.titleSmall)
                Text(
                    "Observación de satélite del ${Fmt.dateTime(estimate.observedAt, bundle.zone)} · " +
                        estimate.sourceName,
                    style = MaterialTheme.typography.bodySmall
                )
                if (forTomorrow) {
                    Text(
                        "No es una previsión de mañana: es la estimación actual, que puede cambiar con el mar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                estimate.factors.forEach {
                    Text(
                        "• ${visibilityFactorText(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            VISIBILITY_DISCLAIMER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
