package com.pescasub.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LocationEntity::class,
        SessionEntity::class,
        CatchEntity::class,
        PhotoEntity::class,
        ForecastCacheEntity::class
    ],
    version = 2,
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
        /**
         * 1 → 2: añade dos columnas NULABLES a `sessions` (visibilidad que vio el usuario y la estimada por el
         * modelo al guardar). No se toca ni se borra ningún dato existente.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `observedVisibilityM` INTEGER")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `estimatedVisibilityM` INTEGER")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
