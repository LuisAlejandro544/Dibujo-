package com.dibujo.android

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Exporta un [Bitmap] a la galería o lo comparte con cualquier app.
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

    /** Subcarpeta de caché para el PNG temporal que se comparte. */
    private const val CARPETA_COMPARTIR = "compartir"

    /** Imagen ya codificada como PNG, lista para guardar o compartir. */
    data class Imagen(val bytes: ByteArray, val nombre: String)

    /** Codifica el [bitmap] a PNG (calidad sin pérdida) y devuelve los bytes. */
    fun codificarPng(bitmap: Bitmap): ByteArray? = runCatching {
        ByteArrayOutputStream().use { salida ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, salida)) return null
            salida.toByteArray()
        }
    }.getOrNull()

    fun guardar(contexto: Context, bitmap: Bitmap, nombre: String): Boolean =
        codificarPng(bitmap)?.let { guardarBytes(contexto, it, nombre) } ?: false

    /** Guarda los bytes PNG en la galería (MediaStore o almacenamiento legacy). */
    fun guardarBytes(contexto: Context, bytes: ByteArray, nombre: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                guardarConMediaStore(contexto, bytes, nombre)
            } else {
                guardarLegacy(contexto, bytes, nombre)
            }
        }.getOrDefault(false)

    /**
     * Comparte los bytes PNG con cualquier app (mensajería, correo, redes).
     * Escribe un archivo temporal en la caché y lo expone por [FileProvider],
     * así no hace falta permiso de almacenamiento. Devuelve `false` si no hay
     * ninguna app que pueda recibir la imagen.
     */
    fun compartir(contexto: Context, bytes: ByteArray, nombre: String): Boolean =
        compartirArchivo(contexto, bytes, nombre, "png", "image/png")

    /**
     * Comparte cualquier archivo (PNG, SVG...) con las apps del equipo: lo
     * escribe en la caché y lo expone por [FileProvider]. La extensión y el
     * tipo MIME decides cómo lo ve la app que lo recibe.
     */
    fun compartirArchivo(
        contexto: Context,
        bytes: ByteArray,
        nombre: String,
        extension: String,
        tipoMime: String,
    ): Boolean = runCatching {
        val carpeta = File(contexto.cacheDir, CARPETA_COMPARTIR)
        if (!carpeta.exists() && !carpeta.mkdirs()) return false
        val archivo = File(carpeta, "$nombre.$extension")
        FileOutputStream(archivo).use { it.write(bytes) }
        val uri = FileProvider.getUriForFile(
            contexto,
            "${contexto.packageName}.fileprovider",
            archivo,
        )
        val enviar = Intent(Intent.ACTION_SEND).apply {
            type = tipoMime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val elegir = Intent.createChooser(enviar, null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        contexto.startActivity(elegir)
        true
    }.getOrDefault(false)

    /** Tamaño legible en KB/MB para avisar al usuario antes de guardar. */
    fun formatearTamano(bytes: Int): String {
        val kb = bytes / 1024.0
        return if (kb < 1024.0) {
            String.format(Locale.getDefault(), "%.0f KB", kb)
        } else {
            String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0)
        }
    }

    /** API 29+: MediaStore, sin permisos, con `RELATIVE_PATH` e `IS_PENDING`. */
    private fun guardarConMediaStore(contexto: Context, bytes: ByteArray, nombre: String): Boolean {
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
        val escrito = resolver.openOutputStream(uri)?.use { salida ->
            salida.write(bytes)
            true
        } ?: false
        if (!escrito) {
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
    private fun guardarLegacy(contexto: Context, bytes: ByteArray, nombre: String): Boolean {
        val carpeta = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            CARPETA,
        )
        if (!carpeta.exists() && !carpeta.mkdirs()) return false
        val archivo = File(carpeta, "$nombre.png")
        FileOutputStream(archivo).use { it.write(bytes) }
        MediaScannerConnection.scanFile(
            contexto,
            arrayOf(archivo.absolutePath),
            arrayOf("image/png"),
            null,
        )
        return true
    }
}
