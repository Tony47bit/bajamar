package com.pescasub.app.ui.logbook

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pescasub.app.domain.Activity
import com.pescasub.app.ui.AppTopBar
import com.pescasub.app.ui.EmptyView
import com.pescasub.app.ui.Fmt
import com.pescasub.app.ui.LoadingView
import com.pescasub.app.ui.SectionCard
import com.pescasub.app.ui.showDatePicker
import com.pescasub.app.ui.showTimePicker
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun EditSessionScreen(vm: EditSessionViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val locations by vm.locationOptions.collectAsStateWithLifecycle()
    val suggestions by vm.speciesSuggestions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    LaunchedEffect(state.saveError) {
        state.saveError?.let {
            snackbar.showSnackbar(it)
            vm.saveErrorShown()
        }
    }
    LaunchedEffect(state.photoMessage) {
        state.photoMessage?.let {
            snackbar.showSnackbar(it)
            vm.photoMessageShown()
        }
    }

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(EditSessionViewModel.MAX_PHOTOS)
    ) { uris: List<Uri> -> vm.addPhotos(uris.map { it.toString() }) }

    // La cámara escribe en un archivo temporal de la caché privada; la app lo recodifica (sin EXIF) al importarlo.
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = cameraPath
        if (ok && path != null) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
            vm.addPhotos(listOf(uri.toString()))
        }
    }
    fun launchCamera() {
        try {
            val dir = File(context.cacheDir, "camera").apply {
                deleteRecursively()
                mkdirs()
            }
            val file = File(dir, "captura_${System.currentTimeMillis()}.jpg")
            cameraPath = file.absolutePath
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("No se encontró ninguna app de cámara.") }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (vm.isNew) "Nueva jornada" else "Editar jornada",
                onBack = onClose,
                actions = {
                    TextButton(onClick = vm::save, enabled = !state.saving && !state.loading) { Text("Guardar") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            state.loading -> LoadingView(modifier = Modifier.padding(padding))
            state.notFound -> EmptyView(
                "Jornada no encontrada", "Es posible que se haya eliminado.", "Volver", onClose,
                Modifier.padding(padding)
            )
            else -> Column(
                Modifier.padding(padding).fillMaxSize().imePadding()
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())

                SectionCard("Cuándo y qué") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showDatePicker(context, state.startedAt.toLocalDate(), vm::setDate) },
                            modifier = Modifier.weight(1f)
                        ) { Text(Fmt.dayShort(state.startedAt.toLocalDate()) + " " + state.startedAt.year) }
                        OutlinedButton(
                            onClick = { showTimePicker(context, state.startedAt.toLocalTime(), vm::setTime) },
                            modifier = Modifier.weight(1f)
                        ) { Text(Fmt.time(state.startedAt.toInstant(), state.startedAt.zone)) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Activity.entries.forEach { a ->
                            FilterChip(
                                selected = state.activity == a,
                                onClick = { vm.setActivity(a) },
                                label = { Text(a.label) }
                            )
                        }
                    }
                    NumberField(
                        value = state.durationText, onChange = vm::setDuration,
                        label = "Duración (minutos)", error = state.errors["duration"]
                    )
                }

                SectionCard("Ubicación") {
                    var open by remember { mutableStateOf(false) }
                    val selected = locations.firstOrNull { it.id == state.locationId }
                    Box {
                        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selected?.name ?: "Sin ubicación")
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(text = { Text("Sin ubicación") }, onClick = {
                                open = false
                                vm.setLocation(null)
                            })
                            locations.forEach { loc ->
                                DropdownMenuItem(text = { Text(loc.name) }, onClick = {
                                    open = false
                                    vm.setLocation(loc.id)
                                })
                            }
                        }
                    }
                    if (locations.isEmpty()) {
                        Text(
                            "Guarda puntos en la pestaña Mapa para asociarlos a tus jornadas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SectionCard("Condiciones del mar") {
                    val c = state.conditions
                    if (c != null) {
                        Text(
                            "Aire ${Fmt.temp(c.airTempC)} · viento ${Fmt.speed(c.windKmh)} · " +
                                "oleaje ${Fmt.height(c.waveM)} · agua ${Fmt.temp(c.seaTempC)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Previsión de ${c.source} adjuntada a la jornada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = vm::clearConditions) { Text("Quitar condiciones") }
                    } else {
                        OutlinedButton(
                            onClick = vm::attachConditions,
                            enabled = !state.conditionsLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (state.conditionsLoading) "Consultando…" else "Adjuntar previsión de la ubicación") }
                    }
                    state.conditionsMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                if (state.activity.hasApneaData) {
                    SectionCard("Apnea") {
                        NumberField(
                            state.apneaDepthText, vm::setApneaDepth, "Profundidad máxima (m)",
                            state.errors["apneaDepth"], decimal = true
                        )
                        NumberField(
                            state.apneaTimeText, vm::setApneaTime, "Tiempo máximo (segundos)",
                            state.errors["apneaTime"]
                        )
                    }
                }

                SectionCard("Visibilidad que viste") {
                    NumberField(
                        state.visibilityText, vm::setVisibility, "Visibilidad horizontal (m, opcional)",
                        state.errors["visibility"]
                    )
                    Text(
                        "Cuántos metros crees que se veían bajo el agua. Con 3 o más anotaciones de un mismo punto " +
                            "guardado, la app ajusta su estimación a lo que sueles ver allí. Anótala el mismo día para " +
                            "que cuente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SectionCard("Capturas") {
                    state.catches.forEach { draft ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = draft.species,
                                    onValueChange = { vm.updateCatch(draft.key, species = it) },
                                    label = { Text("Especie") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { vm.removeCatch(draft.key) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Quitar captura")
                                }
                            }
                            val typed = draft.species.trim()
                            val matches = if (typed.isEmpty()) {
                                emptyList()
                            } else {
                                suggestions.filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, true) }.take(4)
                            }
                            if (matches.isNotEmpty()) {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    matches.forEach { m ->
                                        AssistChip(onClick = { vm.updateCatch(draft.key, species = m) }, label = { Text(m) })
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = draft.weight,
                                    onValueChange = { vm.updateCatch(draft.key, weight = it) },
                                    label = { Text("Peso total (kg)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = draft.count,
                                    onValueChange = { vm.updateCatch(draft.key, count = it) },
                                    label = { Text("Piezas") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(0.7f)
                                )
                            }
                            draft.error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    OutlinedButton(onClick = { vm.addCatch() }) { Text("Añadir captura") }
                }

                SectionCard("Fotos") {
                    if (state.photos.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.photos) { path ->
                                Box {
                                    AsyncImage(
                                        model = File(path),
                                        contentDescription = "Foto de la jornada",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
                                    )
                                    IconButton(
                                        onClick = { vm.removePhoto(path) },
                                        modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Quitar foto",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(Color(0x99000000))
                                                .padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Galería")
                        }
                        OutlinedButton(onClick = ::launchCamera) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Cámara")
                        }
                    }
                    Text(
                        "Las fotos se copian al almacenamiento privado de la app sin su ubicación GPS (máx. " +
                            "${EditSessionViewModel.MAX_PHOTOS}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SectionCard("Notas") {
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = vm::setNotes,
                        label = { Text("Notas de la jornada") },
                        minLines = 3,
                        isError = state.errors["notes"] != null,
                        supportingText = state.errors["notes"]?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(onClick = vm::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.saving) "Guardando…" else "Guardar jornada")
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String?,
    decimal: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}
