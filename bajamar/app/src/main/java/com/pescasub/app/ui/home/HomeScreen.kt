package com.pescasub.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pescasub.app.domain.ConditionsRater
import com.pescasub.app.domain.ForecastBundle
import com.pescasub.app.domain.HourlyConditions
import com.pescasub.app.domain.Rating
import com.pescasub.app.domain.Spot
import com.pescasub.app.ui.AppTopBar
import com.pescasub.app.ui.Fmt
import com.pescasub.app.ui.MetricTile
import com.pescasub.app.ui.RATING_DISCLAIMER
import com.pescasub.app.ui.SectionCard
import com.pescasub.app.ui.conditions.ConditionsUiState
import com.pescasub.app.ui.conditions.ConditionsViewModel
import com.pescasub.app.ui.conditions.ForecastGate
import com.pescasub.app.ui.conditions.SpotActions
import com.pescasub.app.ui.conditions.SpotPickerDialog
import com.pescasub.app.ui.conditions.VisibilityCard
import com.pescasub.app.ui.factorText
import com.pescasub.app.ui.forecastErrorText
import com.pescasub.app.ui.ratingLabel
import com.pescasub.app.ui.theme.RatingColors
import com.pescasub.app.ui.tideTypeText
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(vm: ConditionsViewModel, onOpenForecast: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = state.spot?.name ?: "Bajamar",
            actions = { SpotActions(state, vm::refresh) { picker = true } }
        )
        ForecastGate(vm, onPickSpot = { picker = true }, modifier = Modifier.weight(1f)) { s, spot, bundle ->
            HomeContent(s, spot, bundle, onOpenForecast)
        }
    }
    if (picker) SpotPickerDialog(vm) { picker = false }
}

@Composable
private fun HomeContent(
    state: ConditionsUiState,
    spot: Spot,
    bundle: ForecastBundle,
    onOpenForecast: () -> Unit
) {
    val now = remember(bundle) { Instant.now() }
    val current = bundle.at(now)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { StatusCard(state, bundle, now) }
        if (current != null) {
            item { NowCard(spot, current, bundle) }
            item { RatingCard(current) }
        }
        item { VisibilityCard(state.visibility, state.visibilityLoading, bundle) }
        item { TidesCard(bundle, now) }
        item { HourlyCard(bundle, now) }
        item { DaysCard(bundle, onOpenForecast) }
        item { SourceCard(bundle) }
    }
}

@Composable
private fun StatusCard(state: ConditionsUiState, bundle: ForecastBundle, now: Instant) {
    val outdated = bundle.hourly.lastOrNull()?.time?.isBefore(now) == true
    SectionCard(null) {
        if (state.loading) {
            Text("Actualizando…", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        val saved = if (bundle.fromCache) " · guardados en el dispositivo" else ""
        Text(
            "Última actualización: ${Fmt.dateTime(bundle.fetchedAt, bundle.zone)}$saved",
            style = MaterialTheme.typography.bodyMedium
        )
        state.error?.let {
            Text(
                "No se pudo actualizar: ${forecastErrorText(it)} Se muestran los últimos datos guardados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (outdated) {
            Text(
                "Estos datos ya no cubren la hora actual: conéctate para actualizarlos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun NowCard(spot: Spot, c: HourlyConditions, bundle: ForecastBundle) {
    SectionCard("Condiciones · ${Fmt.time(c.time, bundle.zone)}") {
        Text(
            Fmt.coordinates(spot.coordinates.latitude, spot.coordinates.longitude),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricTile("Aire", Fmt.temp(c.airTempC), modifier = Modifier.weight(1f))
            MetricTile(
                "Agua",
                Fmt.temp(c.seaTempC),
                detail = if (c.seaTempC == null) "Sin datos" else null,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricTile(
                "Viento",
                Fmt.speed(c.windSpeedKmh),
                detail = "De ${Fmt.compass(c.windDirectionDeg)} · rachas ${Fmt.speed(c.windGustsKmh)}",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                "Oleaje",
                Fmt.height(c.waveHeightM),
                detail = if (c.waveHeightM == null) {
                    "Sin datos"
                } else {
                    "De ${Fmt.compass(c.waveDirectionDeg)} · ${Fmt.period(c.wavePeriodS)}"
                },
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricTile(
                "Mar de fondo",
                Fmt.height(c.swellHeightM),
                detail = if (c.swellHeightM == null) {
                    "Sin datos"
                } else {
                    "De ${Fmt.compass(c.swellDirectionDeg)} · ${Fmt.period(c.swellPeriodS)}"
                },
                modifier = Modifier.weight(1f)
            )
            MetricTile("", "", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RatingCard(c: HourlyConditions) {
    val assessment = ConditionsRater.assess(c)
    val (color, icon) = when (assessment.rating) {
        Rating.GOOD -> RatingColors.Good to Icons.Filled.CheckCircle
        Rating.FAIR -> RatingColors.Fair to Icons.Filled.Warning
        Rating.POOR -> RatingColors.Poor to Icons.Filled.Cancel
        Rating.UNKNOWN -> RatingColors.Unknown to Icons.Filled.Info
    }
    SectionCard("Pesca submarina y apnea") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Text(ratingLabel(assessment.rating), style = MaterialTheme.typography.titleLarge, color = color)
        }
        assessment.factors.forEach { Text("• ${factorText(it)}", style = MaterialTheme.typography.bodyMedium) }
        Text(
            RATING_DISCLAIMER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TidesCard(bundle: ForecastBundle, now: Instant) {
    val next = bundle.tides.filter { it.time.isAfter(now) }.take(4)
    SectionCard("Próximas mareas") {
        if (next.isEmpty()) {
            Text(
                "Sin datos de marea para esta ubicación.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            next.forEach { t ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${tideTypeText(t.type)} · ${Fmt.dayShort(bundle.localDate(t.time))}")
                    Text("${Fmt.time(t.time, bundle.zone)} · ${Fmt.height(t.heightM)}")
                }
            }
            Text(
                "Altura relativa al nivel medio del mar, según el modelo. No es una tabla oficial de mareas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HourlyCard(bundle: ForecastBundle, now: Instant) {
    val from = now.truncatedTo(ChronoUnit.HOURS)
    val hours = bundle.hourly.filter { !it.time.isBefore(from) }.take(24)
    SectionCard("Próximas 24 horas") {
        if (hours.isEmpty()) {
            Text("Sin datos horarios.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(hours) { h ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Fmt.hour(h.time, bundle.zone), style = MaterialTheme.typography.labelLarge)
                        Text(Fmt.temp(h.airTempC), style = MaterialTheme.typography.bodyMedium)
                        Text(Fmt.speed(h.windSpeedKmh), style = MaterialTheme.typography.bodySmall)
                        Text(Fmt.height(h.waveHeightM), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(
                "Cada columna: hora, temperatura del aire, viento y oleaje.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DaysCard(bundle: ForecastBundle, onOpenForecast: () -> Unit) {
    SectionCard("Hoy y mañana") {
        val today = bundle.windowDates(Instant.now()).first()
        bundle.daily().forEach { d ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Fmt.dayRelative(d.date, today), modifier = Modifier.weight(1f))
                Text(
                    "${Fmt.temp(d.tempMinC)} / ${Fmt.temp(d.tempMaxC)}",
                    modifier = Modifier.weight(1.4f)
                )
                Text("${Fmt.speed(d.windMaxKmh)} · ${Fmt.height(d.waveMaxM)}", modifier = Modifier.weight(1.6f))
            }
        }
        Text(
            "Por día: temperatura mín./máx., viento máximo y oleaje máximo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onOpenForecast, modifier = Modifier.fillMaxWidth()) {
            Text("Ver previsión detallada")
        }
    }
}

@Composable
private fun SourceCard(bundle: ForecastBundle) {
    SectionCard("Fuente de los datos") {
        Text(
            "${bundle.sourceName} · ${Fmt.dateTime(bundle.fetchedAt, bundle.zone)}",
            style = MaterialTheme.typography.bodyMedium
        )
        bundle.notes.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
