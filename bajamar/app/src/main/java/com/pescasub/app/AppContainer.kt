package com.pescasub.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.pescasub.app.data.local.AppDatabase
import com.pescasub.app.data.local.RoomLocationRepository
import com.pescasub.app.data.local.RoomLogbookRepository
import com.pescasub.app.data.remote.ErddapClarityRepository
import com.pescasub.app.data.remote.OpenMeteoForecastApi
import com.pescasub.app.data.remote.OpenMeteoGeocodingApi
import com.pescasub.app.data.remote.OpenMeteoMarineApi
import com.pescasub.app.data.remote.OpenMeteoPlaceSearch
import com.pescasub.app.data.remote.OpenMeteoProvider
import com.pescasub.app.data.repo.DataEraser
import com.pescasub.app.data.repo.DataStoreSpotStore
import com.pescasub.app.data.repo.DefaultWeatherRepository
import com.pescasub.app.data.repo.PhotoStorage
import com.pescasub.app.domain.LocationRepository
import com.pescasub.app.domain.LogbookRepository
import com.pescasub.app.domain.PhotoStore
import com.pescasub.app.domain.PlaceSearch
import com.pescasub.app.domain.SpotStore
import com.pescasub.app.domain.WeatherProvider
import com.pescasub.app.domain.WeatherRepository
import com.pescasub.app.domain.visibility.WaterClarityRepository
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private val Context.spotDataStore by preferencesDataStore(name = "spot")

/**
 * Contenedor de dependencias manual. Se eligió frente a Hilt para reducir piezas de build en
 * una primera versión; migrar a Hilt es mecánico si el proyecto crece.
 *
 * Nota de privacidad: no hay interceptores de log de red ni analítica; las coordenadas nunca se escriben en logs.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val database: AppDatabase = AppDatabase.build(appContext)

    val weatherProvider: WeatherProvider = OpenMeteoProvider(
        forecastApi = retrofit("https://api.open-meteo.com/").create(OpenMeteoForecastApi::class.java),
        marineApi = retrofit("https://marine-api.open-meteo.com/").create(OpenMeteoMarineApi::class.java),
        json = json
    )

    val weatherRepository: WeatherRepository =
        DefaultWeatherRepository(weatherProvider, database.forecastCacheDao(), Clock.systemUTC())

    /** Transparencia del agua (satélite NOAA, sin clave): base del cálculo de visibilidad submarina. */
    val waterClarityRepository: WaterClarityRepository = ErddapClarityRepository(httpClient, Clock.systemUTC())

    val placeSearch: PlaceSearch = OpenMeteoPlaceSearch(
        retrofit("https://geocoding-api.open-meteo.com/").create(OpenMeteoGeocodingApi::class.java)
    )

    val locationRepository: LocationRepository = RoomLocationRepository(database.locationDao())

    val photoStore: PhotoStore = PhotoStorage(appContext)

    val logbookRepository: LogbookRepository = RoomLogbookRepository(database, photoStore)

    val spotStore: SpotStore = DataStoreSpotStore(appContext.spotDataStore, applicationScope)

    val dataEraser = DataEraser(appContext, database, spotStore)
}
