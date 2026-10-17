package com.dibujo.android

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Guarda un [Bitmap] en la galería del dispositivo.
 *
 * Desde Android 10 (API 29) se usa `MediaStore` con `IS_PENDING`, que no
 * requiere permisos de almacenamiento. En equipos anteriores (26–28) se escribe
 * en `Pictures/Dibujo` y se avisa al escáner de medios; ahí hace falta
 * `WRITE_EXTERNAL_STORAGE`, que la UI pide antes de llamar aquí.
 *
 * Todo se captura con `runCatching`: ante cualquier fallo devuelve `false` y la
 * UI avisa al usuario, sin tumbar la app.
 */
object ExportadorGaleria {

    /** Subcarpeta dentro de Pictures donde se agrupan los dibujos exportados. */
    private const val CARPETA = "Dibujo"

    fun guardar(contexto: Context, bitmap: Bitmap, nombre: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                guardarConMediaStore(contexto, bitmap, nombre)
            } else {
                guardarLegacy(contexto, bitmap, nombre)
            }
        }.getOrDefault(false)

    /** API 29+: MediaStore, sin permisos, con `RELATIVE_PATH` e `IS_PENDING`. */
    private fun guardarConMediaStore(contexto: Context, bitmap: Bitmap, nombre: String): Boolean {
        val resolver = contexto.contentResolver
        val valores = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$nombre.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$CARPETA",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores)
            ?: return false
        val comprimido = resolver.openOutputStream(uri)?.use { salida ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, salida)
        } ?: false
        if (!comprimido) {
            resolver.delete(uri, null, null)
            return false
        }
        // Termina la escritura: recién ahora la imagen es visible en la galería.
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
        return true
    }

    /** API 26–28: escritura directa en Pictures/Dibujo + escaneo de medios. */
    private fun guardarLegacy(contexto: Context, bitmap: Bitmap, nombre: String): Boolean {
        val carpeta = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            CARPETA,
        )
        if (!carpeta.exists() && !carpeta.mkdirs()) return false
        val archivo = File(carpeta, "$nombre.png")
        FileOutputStream(archivo).use { salida ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, salida)) return false
        }
        MediaScannerConnection.scanFile(
            contexto,
            arrayOf(archivo.absolutePath),
            arrayOf("image/png"),
            null,
        )
        return true
    }
}
