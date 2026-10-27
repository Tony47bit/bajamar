package com.pescasub.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.pescasub.app.domain.Coordinates
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Obtiene la posición actual. Si no hay permiso o falla devuelve null (nunca lanza). */
class DeviceLocationProvider(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    // El permiso lo comprueba quien llama (rememberMyLocationAction); una SecurityException se captura abajo.
    @SuppressLint("MissingPermission")
    suspend fun current(): Coordinates? = suspendCancellableCoroutine { cont ->
        val cancellation = CancellationTokenSource()
        cont.invokeOnCancellation { cancellation.cancel() }
        try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                .addOnSuccessListener { location ->
                    val result = location?.let {
                        try {
                            Coordinates(it.latitude, it.longitude)
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    }
                    if (cont.isActive) cont.resume(result)
                }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resume(null)
        }
    }
}

/**
 * Devuelve la acción "usar mi ubicación". El permiso se pide de forma contextual, solo al invocarla.
 * Si se deniega, la app sigue siendo plenamente usable eligiendo el lugar a mano.
 */
@Composable
fun rememberMyLocationAction(
    onLocation: (Coordinates) -> Unit,
    onFailure: (LocationFailure) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember { DeviceLocationProvider(context.applicationContext) }
    val currentOnLocation by rememberUpdatedState(onLocation)
    val currentOnFailure by rememberUpdatedState(onFailure)

    fun fetch() {
        scope.launch {
            val coordinates = provider.current()
            if (coordinates != null) currentOnLocation(coordinates) else currentOnFailure(LocationFailure.UNAVAILABLE)
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) fetch() else currentOnFailure(LocationFailure.DENIED)
    }

    return remember {
        {
            val granted = listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                fetch()
            } else {
                launcher.launch(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                )
            }
        }
    }
}
