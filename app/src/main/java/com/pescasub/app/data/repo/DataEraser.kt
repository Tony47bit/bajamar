package com.pescasub.app.data.repo

import android.content.Context
import com.pescasub.app.data.local.AppDatabase
import com.pescasub.app.domain.SpotStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Borra TODOS los datos personales del dispositivo: base de datos (ubicaciones, jornadas, capturas,
 * caché de previsión), fotos, ubicación elegida y archivos temporales (cámara, teselas del mapa).
 */
class DataEraser(
    private val context: Context,
    private val database: AppDatabase,
    private val spotStore: SpotStore
) {
    suspend fun eraseAll() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            File(context.filesDir, "photos").deleteRecursively()
            File(context.cacheDir, "camera").deleteRecursively()
            File(context.cacheDir, "osmdroid").deleteRecursively()
        }
        spotStore.clear()
    }
}
