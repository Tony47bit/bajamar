package com.pescasub.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        LocationEntity::class,
        SessionEntity::class,
        CatchEntity::class,
        PhotoEntity::class,
        ForecastCacheEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun sessionDao(): SessionDao
    abstract fun forecastCacheDao(): ForecastCacheDao

    companion object {
        const val NAME = "bajamar.db"

        /**
         * Migraciones futuras. NUNCA se usa fallbackToDestructiveMigration: son datos personales del
         * usuario. Al subir `version`, añade aquí la migración y su prueba (ver androidTest).
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
