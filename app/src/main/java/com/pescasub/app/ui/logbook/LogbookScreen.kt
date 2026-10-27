package com.pescasub.app.ui.logbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pescasub.app.domain.Activity
import com.pescasub.app.domain.LogbookFilter
import com.pescasub.app.domain.Session
import com.pescasub.app.domain.SortOrder
import com.pescasub.app.ui.AppTopBar
import com.pescasub.app.ui.DropdownChip
import com.pescasub.app.ui.EmptyView
import com.pescasub.app.ui.Fmt
import com.pescasub.app.ui.LoadingView
import com.pescasub.app.ui.showDatePicker
import java.time.LocalDate

@Composable
fun LogbookScreen(
    vm: LogbookViewModel,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onStats: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar("Bitácora", actions = {
                IconButton(onClick = onStats) {
                    Icon(Icons.Filled.BarChart, contentDescription = "Estadísticas")
                }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nueva jornada") }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when {
                !state.loaded -> LoadingView()
                state.totalCount == 0 -> EmptyView(
                    title = "Aún no hay jornadas",
                    message = "Registra tu primera jornada de pesca submarina, apnea o buceo: capturas, fotos, " +
                        "condiciones y notas. Todo se guarda solo en tu dispositivo.",
                    actionLabel = "Nueva jornada",
                    onAction = onNew
                )
                else -> {
                    FilterBar(state, vm::setFilter)
                    if (state.sessions.isEmpty()) {
                        EmptyView(
                            title = "Sin resultados",
                            message = "Ninguna jornada coincide con los filtros activos.",
                            actionLabel = "Quitar filtros",
                            onAction = { vm.setFilter(LogbookFilter(sort = state.filter.sort)) }
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.sessions, key = { it.id }) { s -> SessionCard(s) { onOpen(s.id) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterBar(state: LogbookUiState, onChange: (LogbookFilter) -> Unit, compact: Boolean = false) {
    val f = state.filter
    val context = LocalContext.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            DropdownChip(
                label = f.activity?.label ?: "Actividad",
                active = f.activity != null,
                options = listOf<Pair<String, Activity?>>("Todas" to null) + Activity.entries.map { it.label to it },
                onSelect = { onChange(f.copy(activity = it)) }
            )
        }
        item {
            DropdownChip(
                label = state.locationOptions.firstOrNull { it.id == f.locationId }?.name ?: "Ubicación",
                active = f.locationId != null,
                options = listOf<Pair<String, Long?>>("Todas" to null) + state.locationOptions.map { it.name to it.id },
                onSelect = { onChange(f.copy(locationId = it)) }
            )
        }
        if (!compact) {
            item {
                DropdownChip(
                    label = f.species ?: "Especie",
                    active = f.species != null,
                    options = listOf<Pair<String, String?>>("Todas" to null) + state.speciesOptions.map { it to it },
                    onSelect = { onChange(f.copy(species = it)) }
                )
            }
            item {
                DateChip("Desde", f.from, context) { onChange(f.copy(from = it)) }
            }
            item {
                DateChip("Hasta", f.to, context) { onChange(f.copy(to = it)) }
            }
            item {
                DropdownChip(
                    label = f.sort.label,
                    active = f.sort != SortOrder.NEWEST,
                    options = SortOrder.entries.map { it.label to it },
                    onSelect = { onChange(f.copy(sort = it)) }
                )
            }
        }
    }
}

@Composable
private fun DateChip(
    label: String,
    value: LocalDate?,
    context: android.content.Context,
    onChange: (LocalDate?) -> Unit
) {
    FilterChip(
        selected = value != null,
        onClick = { showDatePicker(context, value ?: LocalDate.now(), onChange) },
        label = { Text(if (value == null) label else "$label ${Fmt.dayShort(value)}") },
        trailingIcon = if (value != null) {
            {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Quitar filtro $label",
                    modifier = Modifier.clickable { onChange(null) }
                )
            }
        } else {
            null
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionCard(s: Session, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Fmt.dayLong(s.localDate), style = MaterialTheme.typography.titleMedium)
                Text(Fmt.time(s.startedAt, s.zone), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "${s.activity.label} · ${s.location?.name ?: "Sin ubicación"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            val parts = buildList {
                s.durationMinutes?.let { add(Fmt.duration(it)) }
                add(if (s.totalCatches == 1) "1 captura" else "${s.totalCatches} capturas")
                if (s.totalWeightKg > 0) add(Fmt.kg(s.totalWeightKg))
                if (s.photoPaths.isNotEmpty()) add("${s.photoPaths.size} foto(s)")
            }
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
