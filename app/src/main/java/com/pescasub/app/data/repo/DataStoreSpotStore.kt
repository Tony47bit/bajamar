package com.pescasub.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.SelectedSpotState
import com.pescasub.app.domain.Spot
import com.pescasub.app.domain.SpotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Recuerda la última ubicación elegida (solo en el almacenamiento privado del dispositivo). */
class DataStoreSpotStore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope
) : SpotStore {

    override val state: StateFlow<SelectedSpotState> = dataStore.data
        .map { prefs -> SelectedSpotState(loaded = true, spot = prefs.toSpot()) }
        .catch { emit(SelectedSpotState(loaded = true, spot = null)) }
        .stateIn(scope, SharingStarted.Eagerly, SelectedSpotState())

    override suspend fun select(spot: Spot) {
        dataStore.edit { prefs ->
            prefs[NAME] = spot.name
            prefs[LAT] = spot.coordinates.latitude
            prefs[LON] = spot.coordinates.longitude
            if (spot.locationId != null) prefs[LOCATION_ID] = spot.locationId else prefs.remove(LOCATION_ID)
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toSpot(): Spot? {
        val lat = this[LAT] ?: return null
        val lon = this[LON] ?: return null
        val coordinates = try {
            Coordinates(lat, lon)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return Spot(name = this[NAME] ?: "Ubicación", coordinates = coordinates, locationId = this[LOCATION_ID])
    }

    private companion object {
        val NAME = stringPreferencesKey("spot_name")
        val LAT = doublePreferencesKey("spot_lat")
        val LON = doublePreferencesKey("spot_lon")
        val LOCATION_ID = longPreferencesKey("spot_location_id")
    }
}
