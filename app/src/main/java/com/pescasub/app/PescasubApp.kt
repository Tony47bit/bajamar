package com.pescasub.app

import android.app.Application
import java.io.File
import org.osmdroid.config.Configuration

class PescasubApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // osmdroid: identifícate ante el servidor de teselas y guarda la caché en almacenamiento privado
        // (sin pedir permisos de almacenamiento).
        Configuration.getInstance().apply {
            userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        container = AppContainer(this)
    }
}
