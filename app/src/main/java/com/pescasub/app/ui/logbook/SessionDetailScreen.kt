package com.pescasub.app.ui.logbook

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pescasub.app.domain.Session
import com.pescasub.app.ui.AppTopBar
import com.pescasub.app.ui.EmptyView
import com.pescasub.app.ui.Fmt
import com.pescasub.app.ui.LoadingView
import com.pescasub.app.ui.SectionCard
import com.pescasub.app.ui.TextRow
import java.io.File

@Composable
fun SessionDetailScreen(
    vm: SessionDetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.errorShown()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar("Jornada", onBack = onBack, actions = {
                val s = state
                if (s is DetailUiState.Content) {
                    IconButton(onClick = { onEdit(s.session.id) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar jornada")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar jornada")
                    }
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                DetailUiState.Loading -> LoadingView()
                DetailUiState.NotFound -> EmptyView(
                    "Jornada no encontrada", "Es posible que se haya eliminado.", "Volver", onBack
                )
                is DetailUiState.Content -> DetailContent(s.session)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar jornada") },
            text = { Text("Se eliminarán la jornada, sus capturas y sus fotos de este dispositivo. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onDone = onBack)
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun DetailContent(s: Session) {
    var bigPhoto by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(Fmt.dayLong(s.localDate)) {
                TextRow("Hora", Fmt.time(s.startedAt, s.zone))
                TextRow("Actividad", s.activity.label)
                TextRow("Duración", Fmt.duration(s.durationMinutes))
                TextRow("Ubicación", s.location?.name ?: "Sin ubicación")
                s.location?.let {
                    TextRow("Coordenadas", Fmt.coordinates(it.coordinates.latitude, it.coordinates.longitude))
                }
            }
        }
        s.conditions?.let { c ->
            item {
                SectionCard("Condiciones") {
                    TextRow("Aire", Fmt.temp(c.airTempC))
                    TextRow("Agua", Fmt.temp(c.seaTempC))
                    TextRow("Viento", Fmt.speed(c.windKmh))
                    TextRow("Oleaje", Fmt.height(c.waveM))
                    TextRow("Periodo", Fmt.period(c.wavePeriodS))
                    Text(
                        "Datos de previsión de ${c.source} adjuntados a la jornada; no son mediciones tuyas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        s.observedVisibilityM?.let { v ->
            item {
                SectionCard("Visibilidad que viste") {
                    TextRow("Visibilidad horizontal", "$v m")
                }
            }
        }
        s.apnea?.let { a ->
            item {
                SectionCard("Apnea") {
                    TextRow("Profundidad máxima", Fmt.number(a.maxDepthM, 1, "m"))
                    TextRow("Tiempo máximo", Fmt.seconds(a.maxTimeSec))
                }
            }
        }
        item {
            SectionCard("Capturas") {
                if (s.catches.isEmpty()) {
                    Text("Sin capturas registradas.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    s.catches.forEach { c ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${c.count} × ${c.species}")
                            Text(if (c.weightKg == null) "Sin peso" else Fmt.kg(c.weightKg))
                        }
                    }
                    TextRow("Total", "${s.totalCatches} pieza(s) · ${Fmt.kg(s.totalWeightKg)}")
                }
            }
        }
        if (s.notes.isNotBlank()) {
            item { SectionCard("Notas") { Text(s.notes, style = MaterialTheme.typography.bodyMedium) } }
        }
        if (s.photoPaths.isNotEmpty()) {
            item {
                SectionCard("Fotos") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.photoPaths) { path ->
                            AsyncImage(
                                model = File(path),
                                contentDescription = "Foto de la jornada",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { bigPhoto = path }
                            )
                        }
                    }
                }
            }
        }
    }

    bigPhoto?.let { path ->
        Dialog(onDismissRequest = { bigPhoto = null }) {
            AsyncImage(
                model = File(path),
                contentDescription = "Foto ampliada",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clickable { bigPhoto = null }
            )
        }
    }
}
