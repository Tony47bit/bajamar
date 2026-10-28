package com.pescasub.app.data.local

import androidx.room.withTransaction
import com.pescasub.app.domain.LocationRepository
import com.pescasub.app.domain.LogbookRepository
import com.pescasub.app.domain.PhotoStore
import com.pescasub.app.domain.SavedLocation
import com.pescasub.app.domain.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLocationRepository(private val dao: LocationDao) : LocationRepository {
    override fun observeAll(): Flow<List<SavedLocation>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: Long): SavedLocation? = dao.get(id)?.toDomain()

    override suspend fun save(location: SavedLocation): Long =
        if (location.id == 0L) {
            dao.insert(location.toEntity())
        } else {
            dao.update(location.toEntity())
            location.id
        }

    override suspend fun delete(id: Long) = dao.delete(id)
}

class RoomLogbookRepository(
    private val db: AppDatabase,
    private val photoStore: PhotoStore
) : LogbookRepository {
    private val dao = db.sessionDao()

    override fun observeAll(): Flow<List<Session>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observe(id: Long): Flow<Session?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun get(id: Long): Session? = dao.get(id)?.toDomain()

    override suspend fun save(session: Session): Long {
        var removedPhotos: List<String> = emptyList()
        val id = db.withTransaction {
            val sessionId: Long
            if (session.id == 0L) {
                sessionId = dao.insert(session.toEntity())
            } else {
                val previous = dao.get(session.id)
                    ?: throw IllegalStateException("La jornada ${session.id} ya no existe")
                removedPhotos = previous.photos.map { it.filePath } - session.photoPaths.toSet()
                dao.update(session.toEntity())
                dao.deleteCatches(session.id)
                dao.deletePhotos(session.id)
                sessionId = session.id
            }
            dao.insertCatches(session.catches.map { it.toEntity(sessionId) })
            dao.insertPhotos(session.photoPaths.map { PhotoEntity(sessionId = sessionId, filePath = it) })
            sessionId
        }
        // Los archivos solo se borran cuando la transacción ya se ha confirmado.
        removedPhotos.forEach(photoStore::delete)
        return id
    }

    override suspend fun delete(id: Long) {
        val paths = dao.get(id)?.photos?.map { it.filePath }.orEmpty()
        dao.delete(id) // capturas y fotos: borrado en cascada
        paths.forEach(photoStore::delete)
    }
}
