package com.pescasub.app.ui.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pescasub.app.domain.PeriodComparison
import com.pescasub.app.domain.PeriodTotals
import com.pescasub.app.ui.AppTopBar
import com.pescasub.app.ui.ChartSeries
import com.pescasub.app.ui.EmptyView
import com.pescasub.app.ui.Fmt
import com.pescasub.app.ui.HorizontalBar
import com.pescasub.app.ui.LineChart
import com.pescasub.app.ui.LoadingView
import com.pescasub.app.ui.MetricTile
import com.pescasub.app.ui.SectionCard
import com.pescasub.app.ui.theme.ChartColors
import java.time.LocalDate

/** Estadísticas calculadas solo con las jornadas reales (y los filtros de actividad/ubicación activos). */
@Composable
fun StatsScreen(vm: LogbookViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar("Estadísticas", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding)) {
            when {
                !state.loaded -> LoadingView()
                state.totalCount == 0 -> EmptyView(
                    "Sin datos todavía",
                    "Las estadísticas se calculan con tus jornadas. Registra alguna en la Bitácora."
                )
                else -> {
                    FilterBar(state, vm::setFilter, compact = true)
                    if (state.stats.sessions == 0) {
                        EmptyView("Sin resultados", "Ninguna jornada coincide con los filtros de actividad o ubicación.")
                    } else {
                        StatsContent(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsContent(state: LogbookUiState) {
    val s = state.stats
    val comparison = remember(state.sessions) { PeriodComparison.of(state.sessions, LocalDate.now(), 30) }
    val apneaSessions = state.sessions.filter { it.apnea != null }.sortedBy { it.startedAt }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("Resumen") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricTile("Jornadas", s.sessions.toString(), modifier = Modifier.weight(1f))
                    MetricTile("Tiempo total", Fmt.duration(s.totalMinutes), modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricTile("Capturas", s.totalCatches.toString(), modifier = Modifier.weight(1f))
                    MetricTile("Peso total", Fmt.kg(s.totalWeightKg), modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            SectionCard("Jornadas por mes") {
                val max = s.byMonth.maxOf { it.sessions }.coerceAtLeast(1)
                s.byMonth.forEach { m ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(Fmt.month(m.month), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${m.sessions} jornada(s) · ${m.catches} captura(s)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        HorizontalBar(m.sessions.toFloat() / max, ChartColors.Primary)
                    }
                }
            }
        }
        item {
            SectionCard("Evolución de capturas y peso") {
                if (s.byMonth.size < 2) {
                    Text(
                        "Hacen falta jornadas de al menos dos meses distintos para ver una evolución.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val labels = s.byMonth.map { Fmt.month(it.month) }
                    LineChart(
                        series = listOf(ChartSeries("Capturas", ChartColors.Primary, s.byMonth.map { it.catches.toDouble() })),
                        xLabels = labels, unit = "", decimals = 0, zeroBased = true
                    )
                    LineChart(
                        series = listOf(ChartSeries("Peso (kg)", ChartColors.Secondary, s.byMonth.map { it.weightKg })),
                        xLabels = labels, unit = "kg", decimals = 1, zeroBased = true
                    )
                }
            }
        }
        item {
            SectionCard("Capturas por especie") {
                if (s.bySpecies.isEmpty()) {
                    Text("Aún no has registrado capturas.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    val max = s.bySpecies.maxOf { it.count }.coerceAtLeast(1)
                    s.bySpecies.forEach { sp ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(sp.species, style = MaterialTheme.typography.bodyMedium)
                                Text("${sp.count} · ${Fmt.kg(sp.weightKg)}", style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalBar(sp.count.toFloat() / max, ChartColors.Secondary)
                        }
                    }
                }
            }
        }
        item {
            SectionCard("Apnea") {
                if (apneaSessions.isEmpty()) {
                    Text(
                        "Sin registros de apnea. Añade profundidad o tiempo máximos en tus jornadas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetricTile("Mejor profundidad", Fmt.number(s.bestApneaDepthM, 1, "m"), modifier = Modifier.weight(1f))
                        MetricTile("Mejor tiempo", Fmt.seconds(s.bestApneaTimeSec), modifier = Modifier.weight(1f))
                    }
                    if (apneaSessions.size >= 2) {
                        LineChart(
                            series = listOf(
                                ChartSeries("Profundidad máx.", ChartColors.Primary, apneaSessions.map { it.apnea?.maxDepthM })
                            ),
                            xLabels = apneaSessions.map { Fmt.dayShort(it.localDate) },
                            unit = "m", decimals = 1, zeroBased = true,
                            emptyText = "Sin datos de profundidad."
                        )
                    } else {
                        Text(
                            "Con más registros verás aquí tu evolución.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            SectionCard("Últimos 30 días frente a los 30 anteriores") {
                CompareRow("", "Últimos 30", "Anteriores", header = true)
                CompareRow("Jornadas", comparison.current.sessions.toString(), comparison.previous.sessions.toString())
                CompareRow("Capturas", comparison.current.catches.toString(), comparison.previous.catches.toString())
                CompareRow("Peso", Fmt.kg(comparison.current.weightKg), Fmt.kg(comparison.previous.weightKg))
                CompareRow("Tiempo", Fmt.duration(comparison.current.minutes), Fmt.duration(comparison.previous.minutes))
                Text(
                    deltaText(comparison.current, comparison.previous),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun deltaText(current: PeriodTotals, previous: PeriodTotals): String = when {
    previous.sessions == 0 && current.sessions == 0 -> "No hay jornadas en estos dos periodos."
    previous.sessions == 0 -> "No hay jornadas en el periodo anterior con las que comparar."
    else -> {
        val d = current.catches - previous.catches
        when {
            d > 0 -> "Has registrado $d captura(s) más que en el periodo anterior."
            d < 0 -> "Has registrado ${-d} captura(s) menos que en el periodo anterior."
            else -> "Mismo número de capturas que en el periodo anterior."
        }
    }
}

@Composable
private fun CompareRow(label: String, a: String, b: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(a, style = style, modifier = Modifier.weight(1f))
        Text(b, style = style, modifier = Modifier.weight(1f))
    }
}
