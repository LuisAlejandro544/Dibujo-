package com.dibujo.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Guarda y recupera dibujos editables en el almacenamiento privado de la app.
 *
 * Cada dibujo es un PNG (ARGB, sin pérdida) dentro de `filesDir/dibujos`, así
 * que la hoja se puede reabrir tal cual quedó, no sólo exportarse a la galería.
 * Hay dos clases de archivos:
 *
 *  - **El borrador actual** (`actual.png`, ver [SLOT_ACTUAL]): el dibujo en el
 *    que se está trabajando. Se autoguarda al salir de la pantalla de dibujo y
 *    es el que reanuda "Continuar dibujando".
 *  - **Copias de la biblioteca**: cada "Guardar en la app" crea una copia con
 *    su propia marca de tiempo. Son las que lista "Cargar dibujo".
 *
 * Todo es síncrono y sin permisos (almacenamiento interno). La UI lo llama
 * fuera del hilo principal.
 */
object AlmacenDibujos {

    /** Identificador reservado del borrador en curso (no aparece en la lista). */
    const val SLOT_ACTUAL = "actual"

    private const val CARPETA = "dibujos"
    private const val SUFIJO = ".png"

    /** Metadatos de un dibujo guardado (sin los píxeles). */
    data class DibujoGuardado(
        val id: String,
        val ancho: Int,
        val alto: Int,
        val fecha: Long,
    )

    /** Contenido completo de un dibujo: dimensiones y píxeles ARGB. */
    class DibujoContenido(
        val ancho: Int,
        val alto: Int,
        val pixeles: IntArray,
    )

    /** Lista las copias de la biblioteca, de la más reciente a la más vieja. */
    fun listar(contexto: Context): List<DibujoGuardado> {
        val carpeta = carpeta(contexto) ?: return emptyList()
        val archivos = carpeta.listFiles { f -> f.isFile && f.name.endsWith(SUFIJO) }
            ?: return emptyList()
        return archivos
            .filter { it.nameWithoutExtension != SLOT_ACTUAL }
            .mapNotNull { describir(it) }
            .sortedByDescending { it.fecha }
    }

    /** ¿Hay un borrador en curso guardado? */
    fun hayActual(contexto: Context): Boolean = archivo(contexto, SLOT_ACTUAL)?.isFile == true

    /** Guarda los píxeles como el borrador actual (slot [SLOT_ACTUAL]). */
    fun guardarActual(contexto: Context, ancho: Int, alto: Int, pixeles: IntArray): Boolean =
        escribir(archivo(contexto, SLOT_ACTUAL), ancho, alto, pixeles)

    /** Crea una copia nueva en la biblioteca y devuelve sus metadatos. */
    fun guardarCopia(contexto: Context, ancho: Int, alto: Int, pixeles: IntArray): DibujoGuardado? {
        val id = "dibujo_${System.currentTimeMillis()}"
        val archivo = archivo(contexto, id) ?: return null
        if (!escribir(archivo, ancho, alto, pixeles)) return null
        return describir(archivo)
    }

    /** Carga el borrador actual, o null si no existe o no se pudo leer. */
    fun cargarActual(contexto: Context): DibujoContenido? = cargar(contexto, SLOT_ACTUAL)

    /**
     * Borra el borrador en curso. Lo usa "Nuevo dibujo" para empezar de cero:
     * así "Continuar dibujando" no reanuda el dibujo anterior.
     */
    fun borrarActual(contexto: Context): Boolean =
        runCatching { archivo(contexto, SLOT_ACTUAL)?.delete() == true }.getOrDefault(false)

    /** Carga una copia de la biblioteca por su [id]. */
    fun cargar(contexto: Context, id: String): DibujoContenido? {
        val archivo = archivo(contexto, id) ?: return null
        if (!archivo.isFile) return null
        return runCatching {
            val bitmap = BitmapFactory.decodeFile(archivo.absolutePath) ?: return null
            val ancho = bitmap.width
            val alto = bitmap.height
            val pixeles = IntArray(ancho * alto)
            bitmap.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
            bitmap.recycle()
            DibujoContenido(ancho, alto, pixeles)
        }.getOrNull()
    }

    /** Borra una copia de la biblioteca. */
    fun borrar(contexto: Context, id: String): Boolean =
        runCatching { archivo(contexto, id)?.delete() == true }.getOrDefault(false)

    /**
     * Miniatura reducida de un dibujo para la lista (potencias de dos con
     * `inSampleSize`), sin cargar los píxeles completos en memoria.
     */
    fun miniatura(contexto: Context, id: String, lado: Int): Bitmap? {
        val archivo = archivo(contexto, id) ?: return null
        if (!archivo.isFile) return null
        return runCatching {
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(archivo.absolutePath, limites)
            var escala = 1
            while (limites.outWidth / escala > lado * 2 || limites.outHeight / escala > lado * 2) {
                escala *= 2
            }
            val opciones = BitmapFactory.Options().apply { inSampleSize = escala }
            BitmapFactory.decodeFile(archivo.absolutePath, opciones)
        }.getOrNull()
    }

    private fun describir(archivo: File): DibujoGuardado? = runCatching {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.absolutePath, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        DibujoGuardado(
            id = archivo.nameWithoutExtension,
            ancho = limites.outWidth,
            alto = limites.outHeight,
            fecha = archivo.lastModified(),
        )
    }.getOrNull()

    private fun escribir(archivo: File?, ancho: Int, alto: Int, pixeles: IntArray): Boolean {
        if (archivo == null) return false
        if (ancho <= 0 || alto <= 0 || pixeles.size < ancho * alto) return false
        return runCatching {
            val bitmap = Bitmap.createBitmap(pixeles, ancho, alto, Bitmap.Config.ARGB_8888)
            try {
                FileOutputStream(archivo).use { salida ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, salida)
                }
            } finally {
                bitmap.recycle()
            }
            true
        }.getOrDefault(false)
    }

    private fun archivo(contexto: Context, id: String): File? {
        val carpeta = carpeta(contexto) ?: return null
        val limpio = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return File(carpeta, "$limpio$SUFIJO")
    }

    private fun carpeta(contexto: Context): File? {
        val carpeta = File(contexto.filesDir, CARPETA)
        if (!carpeta.exists() && !carpeta.mkdirs()) return null
        return carpeta
    }
}
