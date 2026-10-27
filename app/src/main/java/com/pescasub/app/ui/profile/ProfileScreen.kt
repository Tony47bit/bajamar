package com.pescasub.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pescasub.app.BuildConfig
import com.pescasub.app.ui.AppTopBar
import com.pescasub.app.ui.SectionCard
import com.pescasub.app.ui.TextRow

@Composable
fun ProfileScreen(vm: ProfileViewModel, attribution: String) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmErase by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    Scaffold(
        topBar = { AppTopBar("Perfil y privacidad") },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard("Tus datos") {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextRow("Jornadas guardadas", state.sessionCount.toString())
                    TextRow("Puntos guardados", state.locationCount.toString())
                    Text(
                        "Todo se guarda solo en este dispositivo. No hay cuenta, no se sube nada a ningún servidor " +
                            "y no se envía analítica.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(
                        onClick = { confirmErase = true },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Eliminar todos mis datos", color = MaterialTheme.colorScheme.error) }
                }
            }
            item {
                SectionCard("Qué sale del dispositivo") {
                    Text(
                        "• Previsión: se envían a Open-Meteo las coordenadas de la ubicación elegida redondeadas " +
                            "a ~1 km, nunca el punto exacto ni su nombre.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• Visibilidad submarina: se consultan a NOAA CoastWatch (ERDDAP) los datos de satélite del " +
                            "punto elegido, con coordenadas redondeadas a ~1 km.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• Búsqueda de localidades: el texto que escribes se envía a Open-Meteo.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• Mapa: las teselas se descargan de OpenStreetMap; su servidor puede deducir la zona que " +
                            "estás viendo, pero no recibe tus puntos guardados.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• Tu ubicación del GPS solo se lee si pulsas «Mi ubicación» y no se guarda salvo que tú " +
                            "guardes el punto.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• Las fotos se guardan en la carpeta privada de la app, sin su ubicación GPS.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item {
                SectionCard("Fuentes y licencias") {
                    Text(attribution, style = MaterialTheme.typography.bodyMedium)
                    Text("Mapa: © OpenStreetMap contributors (ODbL).", style = MaterialTheme.typography.bodyMedium)
                    Text("Transparencia del agua: NOAA CoastWatch, VIIRS Kd490 (dominio público de EE. UU.).", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                SectionCard("Acerca de") {
                    TextRow("Aplicación", "Bajamar")
                    TextRow("Versión", BuildConfig.VERSION_NAME)
                    Text(
                        "App independiente, inspirada en el tipo de funciones de las apps de pronóstico para pesca " +
                            "submarina. Todas las funciones son gratuitas; no hay suscripción ni compras.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Las previsiones y valoraciones son orientativas y no garantizan la seguridad. Bucea " +
                            "siempre acompañado y comprueba las condiciones reales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Eliminar todos los datos") },
            text = {
                Text(
                    "Se borrarán de este dispositivo todas tus jornadas, capturas, fotos, puntos guardados y la " +
                        "ubicación elegida. No se puede deshacer."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmErase = false
                    vm.eraseAll()
                }) { Text("Eliminar todo") }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancelar") } }
        )
    }
}
