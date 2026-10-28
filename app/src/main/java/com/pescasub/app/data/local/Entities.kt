package com.pescasub.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Ubicación guardada. Las "marcas GPS" personales son ubicaciones con isFavorite = false o true:
 * todas son privadas y no salen del dispositivo.
 */
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val isFavorite: Boolean,
    /** Instante UTC en milisegundos. */
    val createdAt: Long
)

/**
 * Jornada. Si se borra la ubicación asociada la jornada se conserva (locationId pasa a NULL).
 * Las fechas se guardan en UTC y se conserva la zona horaria en la que se registraron.
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("locationId"), Index("startedAt")]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Instante UTC en milisegundos. */
    val startedAt: Long,
    /** Zona horaria (ID de java.time) vigente al registrar la jornada. */
    val zoneId: String,
    /** Nombre de domain.Activity. */
    val activity: String,
    val durationMinutes: Int?,
    val locationId: Long?,
    val notes: String,
    // Condiciones obtenidas de una API (nulas si el usuario no las adjuntó). snapSource != null => hay instantánea.
    val snapAirTempC: Double?,
    val snapWindKmh: Double?,
    val snapWaveM: Double?,
    val snapWavePeriodS: Double?,
    val snapSeaTempC: Double?,
    val snapSource: String?,
    // Datos de apnea introducidos por el usuario.
    val apneaMaxDepthM: Double?,
    val apneaMaxTimeSec: Int?,
    /** Visibilidad horizontal observada por el usuario, en metros. (Añadido en la versión 2.) */
    val observedVisibilityM: Int? = null,
    /** Cifra central estimada por el modelo al guardar la jornada, sin ajustar. (Añadido en la versión 2.) */
    val estimatedVisibilityM: Int? = null
)

/** Fila de captura; se elimina en cascada con su jornada. `weightKg` es el peso total de la fila. */
@Entity(
    tableName = "catches",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("species")]
)
data class CatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val species: String,
    val weightKg: Double?,
    val count: Int
)

/** Foto copiada al almacenamiento privado de la app; se elimina en cascada con su jornada. */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val filePath: String
)

/** Caché de la última respuesta del proveedor meteorológico (carga útil opaca). */
@Entity(tableName = "forecast_cache")
data class ForecastCacheEntity(
    @PrimaryKey val cacheKey: String,
    val payload: String,
    /** Instante UTC en milisegundos en que se obtuvo. */
    val fetchedAt: Long
)

data class SessionWithDetails(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val catches: List<CatchEntity>,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val photos: List<PhotoEntity>,
    @Relation(parentColumn = "locationId", entityColumn = "id")
    val location: LocationEntity?
)
