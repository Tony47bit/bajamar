package com.pescasub.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.pescasub.app.domain.PhotoStore
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Guarda las fotos de las jornadas en el almacenamiento PRIVADO de la app (filesDir/photos).
 *
 * Al importar se vuelve a codificar la imagen, lo que descarta los metadatos EXIF (incluida la
 * posición GPS de la foto: privacidad de los puntos de pesca) conservando la orientación, y se
 * limita el tamaño a MAX_SIDE píxeles para no llenar el almacenamiento.
 */
class PhotoStorage(private val context: Context) : PhotoStore {
    private val dir: File get() = File(context.filesDir, "photos").also { it.mkdirs() }

    override suspend fun import(uri: String): String? = withContext(Dispatchers.IO) {
        try {
            val source = Uri.parse(uri)
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext null

            val rotation = try {
                resolver.openInputStream(source)?.use { ExifInterface(it).rotationDegrees } ?: 0
            } catch (e: Exception) {
                0
            }

            val longest = maxOf(decoded.width, decoded.height)
            val scale = if (longest > MAX_SIDE) MAX_SIDE.toFloat() / longest else 1f
            val matrix = Matrix().apply {
                if (scale != 1f) postScale(scale, scale)
                if (rotation != 0) postRotate(rotation.toFloat())
            }
            val output = if (matrix.isIdentity) {
                decoded
            } else {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            }

            val file = File(dir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (output !== decoded) decoded.recycle()
            output.recycle()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    override fun delete(path: String) {
        try {
            val file = File(path)
            // Solo se borran archivos que están dentro de la carpeta de fotos de la app.
            if (file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) file.delete()
        } catch (e: Exception) {
            // Nada que hacer: el archivo ya no existe o no es accesible.
        }
    }

    private companion object {
        const val MAX_SIDE = 2048
    }
}
