package com.pescasub.app.ui.conditions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pescasub.app.domain.ForecastBundle
import com.pescasub.app.domain.HourlyConditions
import com.pescasub.app.domain.Spot
import com.pescasub.app.ui.AppTopBar
import com.pescasub.app.ui.ChartSeries
import com.pescasub.app.ui.Fmt
import com.pescasub.app.ui.LineChart
import com.pescasub.app.ui.SectionCard
import com.pescasub.app.ui.forecastErrorText
import com.pescasub.app.ui.theme.ChartColors
import com.pescasub.app.ui.tideTypeText
import java.time.Instant
import java.time.LocalDate

@Composable
fun ForecastScreen(vm: ConditionsViewModel, attribution: String) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = state.spot?.let { "Previsión · ${it.name}" } ?: "Previsión",
            actions = { SpotActions(state, vm::refresh) { picker = true } }
        )
        ForecastGate(vm, onPickSpot = { picker = true }, modifier = Modifier.weight(1f)) { s, spot, bundle ->
            ForecastContent(s, spot, bundle, attribution)
        }
    }
    if (picker) SpotPickerDialog(vm) { picker = false }
}

@Composable
private fun ForecastContent(state: ConditionsUiState, spot: Spot, bundle: ForecastBundle, attribution: String) {
    val now = remember(bundle) { Instant.now() }
    // Solo hoy y mañana, en el día local de la ubicación (no el del móvil ni el UTC de la API).
    val window = remember(bundle) { bundle.windowDates(now) }
    val today = window.first()
    if (bundle.hourly.isEmpty()) {
        SectionCard("Sin datos", Modifier.padding(16.dp)) {
            Text("La fuente no devolvió datos horarios para hoy ni mañana en esta ubicación.")
        }
        return
    }
    // La fecha elegida se guarda como texto ISO para sobrevivir a rotaciones; por defecto, hoy.
    var selectedIso by rememberSaveable(spot.coordinates.cacheKey()) { mutableStateOf<String?>(null) }
    val date = selectedIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { it in window } ?: today
    val hours = bundle.hoursOf(date)
    val labels = hours.map { Fmt.hour(it.time, bundle.zone) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let {
                    Text(
                        "No se pudo actualizar: ${forecastErrorText(it)} Se muestran datos guardados.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    window.forEach { d ->
                        FilterChip(
                            selected = d == date,
                            onClick = { selectedIso = d.toString() },
                            label = { Text("${Fmt.dayRelative(d, today)} · ${Fmt.dayShort(d)}") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(Fmt.dayLong(date), style = MaterialTheme.typography.titleMedium)
                Text(
                    "Actualizado: ${Fmt.dateTime(bundle.fetchedAt, bundle.zone)} · ${bundle.sourceName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hours.isEmpty()) {
                    Text(
                        "Sin datos para este día.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        item { VisibilityCard(state.visibility, state.visibilityLoading, bundle, forTomorrow = date != today) }
        item {
            SectionCard("Viento") {
                LineChart(
                    series = listOf(
                        ChartSeries("Viento", ChartColors.Primary, hours.map { it.windSpeedKmh }),
                        ChartSeries("Rachas", ChartColors.Accent, hours.map { it.windGustsKmh })
                    ),
                    xLabels = labels, unit = "km/h", decimals = 0, zeroBased = true,
                    emptyText = "Sin datos de viento para este día."
                )
            }
        }
        item {
            SectionCard("Oleaje") {
                LineChart(
                    series = listOf(
                        ChartSeries("Oleaje", ChartColors.Primary, hours.map { it.waveHeightM }),
                        ChartSeries("Mar de fondo", ChartColors.Secondary, hours.map { it.swellHeightM })
                    ),
                    xLabels = labels, unit = "m", decimals = 1, zeroBased = true,
                    emptyText = "Sin datos de oleaje para este día."
                )
            }
        }
        item {
            SectionCard("Periodo del oleaje") {
                LineChart(
                    series = listOf(
                        ChartSeries("Oleaje", ChartColors.Primary, hours.map { it.wavePeriodS }),
                        ChartSeries("Mar de fondo", ChartColors.Secondary, hours.map { it.swellPeriodS })
                    ),
                    xLabels = labels, unit = "s", decimals = 0, zeroBased = true,
                    emptyText = "Sin datos de periodo para este día."
                )
            }
        }
        item {
            SectionCard("Temperatura") {
                LineChart(
                    series = listOf(
                        ChartSeries("Aire", ChartColors.Accent, hours.map { it.airTempC }),
                        ChartSeries("Agua", ChartColors.Primary, hours.map { it.seaTempC })
                    ),
                    xLabels = labels, unit = "°C", decimals = 0,
                    emptyText = "Sin datos de temperatura para este día."
                )
            }
        }
        item { TidesTable(bundle, date) }
        item {
            SectionCard("Nivel del mar") {
                LineChart(
                    series = listOf(ChartSeries("Nivel", ChartColors.Secondary, hours.map { it.seaLevelM })),
                    xLabels = labels, unit = "m", decimals = 2,
                    emptyText = "Sin datos de nivel del mar para este día."
                )
            }
        }
        item { CompareHours(hours, bundle, date) }
        item {
            SectionCard("Fuente de los datos") {
                Text(
                    "${bundle.sourceName} · obtenidos el ${Fmt.dateTime(bundle.fetchedAt, bundle.zone)}" +
                        if (bundle.fromCache) " (guardados en el dispositivo)" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(attribution, style = MaterialTheme.typography.bodySmall)
                bundle.notes.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Previsiones de modelo con resolución de varios kilómetros: cerca de la costa pueden diferir " +
                        "de lo que verás en el agua.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TidesTable(bundle: ForecastBundle, date: LocalDate) {
    val tides = bundle.tidesOf(date)
    SectionCard("Mareas") {
        if (tides.isEmpty()) {
            Text(
                "Sin datos de marea para este día en esta ubicación.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            tides.forEach { t ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tideTypeText(t.type), style = MaterialTheme.typography.bodyLarge)
                    Text("${Fmt.time(t.time, bundle.zone)} · ${Fmt.height(t.heightM)}")
                }
            }
            Text(
                "Pleamares y bajamares calculadas a partir del nivel del mar del modelo (relativo al nivel medio). " +
                    "No es una tabla oficial de mareas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompareHours(hours: List<HourlyConditions>, bundle: ForecastBundle, date: LocalDate) {
    if (hours.size < 2) return
    val last = hours.lastIndex
    var a by rememberSaveable(date.toString()) { mutableIntStateOf(0) }
    var b by rememberSaveable(date.toString()) { mutableIntStateOf(minOf(12, last)) }
    val ha = hours[a.coerceIn(0, last)]
    val hb = hours[b.coerceIn(0, last)]

    SectionCard("Comparar dos horas") {
        Text("Hora A: ${Fmt.hour(ha.time, bundle.zone)}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = a.toFloat(), onValueChange = { a = it.toInt() },
            valueRange = 0f..last.toFloat(), steps = (last - 1).coerceAtLeast(0)
        )
        Text("Hora B: ${Fmt.hour(hb.time, bundle.zone)}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = b.toFloat(), onValueChange = { b = it.toInt() },
            valueRange = 0f..last.toFloat(), steps = (last - 1).coerceAtLeast(0)
        )
        CompareRow("", Fmt.hour(ha.time, bundle.zone), Fmt.hour(hb.time, bundle.zone), header = true)
        CompareRow("Viento", Fmt.speed(ha.windSpeedKmh), Fmt.speed(hb.windSpeedKmh))
        CompareRow("Rachas", Fmt.speed(ha.windGustsKmh), Fmt.speed(hb.windGustsKmh))
        CompareRow("Dir. viento", Fmt.compass(ha.windDirectionDeg), Fmt.compass(hb.windDirectionDeg))
        CompareRow("Oleaje", Fmt.height(ha.waveHeightM), Fmt.height(hb.waveHeightM))
        CompareRow("Periodo", Fmt.period(ha.wavePeriodS), Fmt.period(hb.wavePeriodS))
        CompareRow("Mar de fondo", Fmt.height(ha.swellHeightM), Fmt.height(hb.swellHeightM))
        CompareRow("Aire", Fmt.temp(ha.airTempC), Fmt.temp(hb.airTempC))
        CompareRow("Agua", Fmt.temp(ha.seaTempC), Fmt.temp(hb.seaTempC))
    }
}

@Composable
private fun CompareRow(label: String, a: String, b: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = style, modifier = Modifier.weight(1.4f))
        Text(a, style = style, modifier = Modifier.weight(1f))
        Text(b, style = style, modifier = Modifier.weight(1f))
    }
}
