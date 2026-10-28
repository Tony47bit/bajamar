package com.pescasub.app.ui.conditions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pescasub.app.domain.ForecastBundle
import com.pescasub.app.domain.Spot
import com.pescasub.app.ui.EmptyView
import com.pescasub.app.ui.ErrorView
import com.pescasub.app.ui.LoadingView
import com.pescasub.app.ui.forecastErrorText
import com.pescasub.app.ui.locationFailureText
import com.pescasub.app.ui.rememberMyLocationAction

/**
 * Pasarela común de Inicio y Previsión: resuelve los estados "cargando", "sin ubicación", "error"
 * y solo llama a `content` cuando hay datos reales de la ubicación elegida.
 */
@Composable
fun ForecastGate(
    vm: ConditionsViewModel,
    onPickSpot: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (ConditionsUiState, Spot, ForecastBundle) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spot = state.spot
    val bundle = state.bundle
    val error = state.error
    Box(modifier) {
        when {
            !state.spotLoaded -> LoadingView()
            spot == null -> EmptyView(
                title = "Elige una ubicación",
                message = "Busca una localidad costera, usa tu ubicación o elige un punto en el mapa para ver " +
                    "las condiciones del mar.",
                actionLabel = "Elegir ubicación",
                onAction = onPickSpot
            )
            bundle != null -> content(state, spot, bundle)
            state.loading -> LoadingView("Cargando previsión de ${spot.name}…")
            error != null -> ErrorView(forecastErrorText(error), onRetry = vm::refresh)
            else -> LoadingView()
        }
    }
}

/** Acciones de la barra superior: actualizar y cambiar de ubicación. */
@Composable
fun SpotActions(state: ConditionsUiState, onRefresh: () -> Unit, onPick: () -> Unit) {
    if (state.spot != null) {
        if (state.loading) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Actualizar previsión")
            }
        }
    }
    IconButton(onClick = onPick) {
        Icon(Icons.Filled.Place, contentDescription = "Cambiar ubicación")
    }
}

/** Diálogo para elegir la ubicación activa: buscar localidad, mi ubicación o un punto guardado. */
@Composable
fun SpotPickerDialog(vm: ConditionsViewModel, onDismiss: () -> Unit) {
    val search by vm.search.collectAsStateWithLifecycle()
    val saved by vm.savedLocations.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var locationMessage by remember { mutableStateOf<String?>(null) }

    val locate = rememberMyLocationAction(
        onLocation = { c ->
            vm.selectSpot(Spot("Mi ubicación", c))
            onDismiss()
        },
        onFailure = { locationMessage = locationFailureText(it) }
    )
    DisposableEffect(Unit) { onDispose { vm.clearSearch() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir ubicación") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar localidad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                    trailingIcon = {
                        IconButton(onClick = { vm.search(query) }) {
                            Icon(Icons.Filled.Search, contentDescription = "Buscar")
                        }
                    }
                )
                if (search.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                search.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (search.searched && search.error == null && search.results.isEmpty()) {
                    Text("Sin resultados.", style = MaterialTheme.typography.bodySmall)
                }
                search.results.forEach { place ->
                    ListItem(
                        headlineContent = { Text(place.name) },
                        supportingContent = place.region?.let { { Text(it) } },
                        modifier = Modifier.clickable {
                            vm.selectSpot(Spot(place.name, place.coordinates))
                            onDismiss()
                        }
                    )
                }
                Button(onClick = locate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Usar mi ubicación")
                }
                locationMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (saved.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Mis puntos", style = MaterialTheme.typography.titleSmall)
                    saved.forEach { loc ->
                        ListItem(
                            headlineContent = { Text(loc.name) },
                            supportingContent = if (loc.description.isNotBlank()) {
                                { Text(loc.description, maxLines = 1) }
                            } else {
                                null
                            },
                            modifier = Modifier.clickable {
                                vm.selectSaved(loc)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
