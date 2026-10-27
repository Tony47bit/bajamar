package com.pescasub.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.FormValidation
import com.pescasub.app.domain.SavedLocation
import com.pescasub.app.domain.Spot
import com.pescasub.app.domain.Validated
import com.pescasub.app.ui.Fmt
import com.pescasub.app.ui.conditions.ConditionsViewModel
import com.pescasub.app.ui.locationFailureText
import com.pescasub.app.ui.rememberMyLocationAction
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/** Normaliza la longitud al rango -180..180 (el mapa permite desplazarse más allá). */
private fun GeoPoint.toCoordinatesOrNull(): Coordinates? {
    val lon = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
    return try {
        Coordinates(latitude, lon)
    } catch (e: IllegalArgumentException) {
        null
    }
}

private data class Focus(val coordinates: Coordinates, val nonce: Int)

@Composable
fun MapScreen(
    mapVm: MapViewModel,
    conditionsVm: ConditionsViewModel,
    onSpotChosen: () -> Unit
) {
    val saved by mapVm.saved.collectAsStateWithLifecycle()
    val selection by mapVm.selection.collectAsStateWithLifecycle()
    val message by mapVm.message.collectAsStateWithLifecycle()
    val search by conditionsVm.search.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var focus by remember { mutableStateOf<Focus?>(null) }
    var focusCounter by remember { mutableIntStateOf(0) }
    var showSave by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    var showCoordinates by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun moveTo(c: Coordinates) {
        focusCounter++
        focus = Focus(c, focusCounter)
    }

    val locate = rememberMyLocationAction(
        onLocation = { c ->
            mapVm.selectTemp(c, "Mi ubicación")
            moveTo(c)
        },
        onFailure = { f -> scope.launch { snackbar.showSnackbar(locationFailureText(f)) } }
    )

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            mapVm.messageShown()
        }
    }

    // Lo seleccionado, resuelto a algo mostrable.
    val selectedSaved: SavedLocation? = (selection as? MapSelection.Saved)?.let { s -> saved.firstOrNull { it.id == s.id } }
    val selectedTemp = selection as? MapSelection.Temp
    val selectedCoordinates: Coordinates? = selectedSaved?.coordinates ?: selectedTemp?.coordinates
    val selectedName: String = selectedSaved?.name ?: selectedTemp?.label ?: "Punto seleccionado"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            OsmMap(
                saved = saved,
                temp = selectedTemp,
                focus = focus,
                onTap = { c -> mapVm.selectTemp(c, null) },
                onSavedTap = { id -> mapVm.selectSaved(id) },
                modifier = Modifier.fillMaxSize()
            )

            // Búsqueda
            Column(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Buscar localidad costera") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { conditionsVm.search(query) }),
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotEmpty() || search.results.isNotEmpty()) {
                                    IconButton(onClick = {
                                        query = ""
                                        conditionsVm.clearSearch()
                                    }) { Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda") }
                                }
                            }
                        )
                        if (search.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        search.error?.let {
                            Text(
                                it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        if (search.searched && search.error == null && search.results.isEmpty()) {
                            Text("Sin resultados.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                        }
                        search.results.forEach { place ->
                            ListItem(
                                headlineContent = { Text(place.name) },
                                supportingContent = place.region?.let { { Text(it) } },
                                modifier = Modifier.clickable {
                                    mapVm.selectTemp(place.coordinates, place.name)
                                    moveTo(place.coordinates)
                                    query = ""
                                    conditionsVm.clearSearch()
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showCoordinates = true },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) { Text("Coordenadas") }
                    OutlinedButton(
                        onClick = { showList = true },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) { Text("Mis puntos (${saved.size})") }
                }
            }

            // Mi ubicación
            SmallFloatingActionButton(
                onClick = locate,
                modifier = Modifier.align(Alignment.BottomEnd).padding(
                    end = 16.dp,
                    bottom = if (selectedCoordinates != null) 200.dp else 16.dp
                )
            ) { Icon(Icons.Filled.MyLocation, contentDescription = "Mi ubicación") }

            // Tarjeta del punto seleccionado
            if (selectedCoordinates != null) {
                Card(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(selectedName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { mapVm.clearSelection() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                            }
                        }
                        Text(
                            Fmt.coordinates(selectedCoordinates.latitude, selectedCoordinates.longitude),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (selectedSaved != null && selectedSaved.description.isNotBlank()) {
                            Text(selectedSaved.description, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                conditionsVm.selectSpot(Spot(selectedName, selectedCoordinates, selectedSaved?.id))
                                onSpotChosen()
                            }) { Text("Ver condiciones") }
                            if (selectedSaved == null) {
                                OutlinedButton(onClick = { showSave = true }) { Text("Guardar") }
                            } else {
                                OutlinedButton(onClick = { showSave = true }) { Text("Editar") }
                                TextButton(onClick = { showDelete = true }) { Text("Eliminar") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSave && selectedCoordinates != null) {
        SaveLocationDialog(
            existing = selectedSaved,
            coordinates = selectedCoordinates,
            suggestedName = selectedTemp?.label.orEmpty(),
            onDismiss = { showSave = false },
            onSave = { name, description ->
                mapVm.save(selectedSaved, name, description, selectedCoordinates)
                showSave = false
            }
        )
    }
    if (showDelete && selectedSaved != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Eliminar punto") },
            text = {
                Text(
                    "Se eliminará «${selectedSaved.name}» de este dispositivo. Las jornadas de la bitácora " +
                        "asociadas se conservan, pero perderán la ubicación."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    mapVm.delete(selectedSaved.id)
                    showDelete = false
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar") } }
        )
    }
    if (showList) {
        AlertDialog(
            onDismissRequest = { showList = false },
            title = { Text("Mis puntos") },
            text = {
                if (saved.isEmpty()) {
                    Text(
                        "Aún no has guardado ningún punto. Toca el mapa para seleccionar una posición y pulsa " +
                            "«Guardar». Tus puntos son privados y solo se almacenan en este dispositivo."
                    )
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        saved.forEach { loc ->
                            ListItem(
                                headlineContent = { Text(loc.name) },
                                supportingContent = {
                                    Text(Fmt.coordinates(loc.coordinates.latitude, loc.coordinates.longitude))
                                },
                                leadingContent = { Icon(Icons.Filled.Place, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    mapVm.selectSaved(loc.id)
                                    moveTo(loc.coordinates)
                                    showList = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showList = false }) { Text("Cerrar") } }
        )
    }
    if (showCoordinates) {
        CoordinatesDialog(
            onDismiss = { showCoordinates = false },
            onConfirm = { c ->
                mapVm.selectTemp(c, null)
                moveTo(c)
                showCoordinates = false
            }
        )
    }
}

@Composable
private fun SaveLocationDialog(
    existing: SavedLocation?,
    coordinates: Coordinates,
    suggestedName: String,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: suggestedName) }
    var description by rememberSaveable { mutableStateOf(existing?.description.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Guardar punto" else "Editar punto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = null },
                    label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it; error = null },
                    label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                Text(
                    Fmt.coordinates(coordinates.latitude, coordinates.longitude),
                    style = MaterialTheme.typography.bodySmall
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "Privado: este punto solo se guarda en tu dispositivo y no se comparte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = FormValidation.locationName(name)
                val d = FormValidation.description(description)
                when {
                    n is Validated.Invalid -> error = n.message
                    d is Validated.Invalid -> error = d.message
                    n is Validated.Valid && d is Validated.Valid -> onSave(n.value, d.value)
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun CoordinatesDialog(onDismiss: () -> Unit, onConfirm: (Coordinates) -> Unit) {
    var lat by rememberSaveable { mutableStateOf("") }
    var lon by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ir a unas coordenadas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lat, onValueChange = { lat = it; error = null },
                    label = { Text("Latitud (ej. 36,7213)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lon, onValueChange = { lon = it; error = null },
                    label = { Text("Longitud (ej. -4,4214)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val r = FormValidation.coordinates(lat, lon)) {
                    is Validated.Valid -> onConfirm(r.value)
                    is Validated.Invalid -> error = r.message
                }
            }) { Text("Ir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Mapa de OpenStreetMap (osmdroid). Las teselas se piden a tile.openstreetmap.org: el servidor puede
 * deducir la zona que miras, pero nunca recibe tus puntos guardados.
 */
@Composable
private fun OsmMap(
    saved: List<SavedLocation>,
    temp: MapSelection.Temp?,
    focus: Focus?,
    onTap: (Coordinates) -> Unit,
    onSavedTap: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnSavedTap by rememberUpdatedState(onSavedTap)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(40.0, -3.7))
            overlays.add(CopyrightOverlay(context)) // atribución obligatoria de OpenStreetMap
            overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        p?.toCoordinatesOrNull()?.let { currentOnTap(it) }
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                })
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(focus) {
        focus?.let {
            mapView.controller.setZoom(13.0)
            mapView.controller.animateTo(GeoPoint(it.coordinates.latitude, it.coordinates.longitude))
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.removeAll { it is Marker }
            saved.forEach { loc ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(loc.coordinates.latitude, loc.coordinates.longitude)
                        title = loc.name
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            currentOnSavedTap(loc.id)
                            true
                        }
                    }
                )
            }
            temp?.let { t ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(t.coordinates.latitude, t.coordinates.longitude)
                        title = t.label ?: "Punto seleccionado"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ -> true }
                    }
                )
            }
            map.invalidate()
        }
    )
}
