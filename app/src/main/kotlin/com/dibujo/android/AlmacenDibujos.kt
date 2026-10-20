package com.dibujo.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Guarda y recupera dibujos editables en el almacenamiento privado de la app.
 *
 * Cada dibujo es un PNG (ARGB, sin pérdida) dentro de `filesDir/dibujos`, así
 * que la hoja se puede reabrir tal cual quedó, no sólo exportarse a la galería.
 * Los textos vivos (ver [TextoEnHoja]) NO se hornean en el PNG: viajan en un
 * archivo aparte `<id>.textos.json`, así al reabrir el dibujo siguen siendo
 * movibles, escalables y editables. El JSON también guarda el tamaño de la hoja
 * para poder dibujar los textos en la miniatura.
 *
 * Hay dos clases de archivos:
 *
 *  - **El borrador actual** (`actual.png` + `actual.textos.json`, ver
 *    [SLOT_ACTUAL]): el dibujo en el que se está trabajando. Se autoguarda al
 *    salir de la pantalla de dibujo y es el que reanuda "Continuar dibujando".
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
    private const val SUFIJO_TEXTOS = ".textos.json"

    /** Metadatos de un dibujo guardado (sin los píxeles). */
    data class DibujoGuardado(
        val id: String,
        val ancho: Int,
        val alto: Int,
        val fecha: Long,
    )

    /** Contenido completo de un dibujo: dimensiones, píxeles ARGB y textos vivos. */
    class DibujoContenido(
        val ancho: Int,
        val alto: Int,
        val pixeles: IntArray,
        val textos: List<TextoEnHoja> = emptyList(),
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
    fun guardarActual(
        contexto: Context,
        ancho: Int,
        alto: Int,
        pixeles: IntArray,
        textos: List<TextoEnHoja> = emptyList(),
    ): Boolean = escribir(contexto, SLOT_ACTUAL, ancho, alto, pixeles, textos)

    /** Crea una copia nueva en la biblioteca y devuelve sus metadatos. */
    fun guardarCopia(
        contexto: Context,
        ancho: Int,
        alto: Int,
        pixeles: IntArray,
        textos: List<TextoEnHoja> = emptyList(),
    ): DibujoGuardado? {
        val id = "dibujo_${System.currentTimeMillis()}"
        if (!escribir(contexto, id, ancho, alto, pixeles, textos)) return null
        val archivo = archivo(contexto, id) ?: return null
        return describir(archivo)
    }

    /** Carga el borrador actual, o null si no existe o no se pudo leer. */
    fun cargarActual(contexto: Context): DibujoContenido? = cargar(contexto, SLOT_ACTUAL)

    /**
     * Borra el borrador en curso. Lo usa "Nuevo dibujo" para empezar de cero:
     * así "Continuar dibujando" no reanuda el dibujo anterior.
     */
    fun borrarActual(contexto: Context): Boolean =
        runCatching { borrarArchivos(contexto, SLOT_ACTUAL) }.getOrDefault(false)

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
            DibujoContenido(ancho, alto, pixeles, leerTextos(contexto, id, ancho, alto))
        }.getOrNull()
    }

    /** Borra una copia de la biblioteca. */
    fun borrar(contexto: Context, id: String): Boolean =
        runCatching { borrarArchivos(contexto, id) }.getOrDefault(false)

    /**
     * Miniatura reducida de un dibujo para la lista (potencias de dos con
     * `inSampleSize`), sin cargar los píxeles completos en memoria. Compone
     * encima los textos vivos para que la lista muestre el dibujo completo.
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
            val bitmap = BitmapFactory.decodeFile(archivo.absolutePath, opciones) ?: return null
            val textos = leerTextos(contexto, id, limites.outWidth, limites.outHeight)
            if (textos.isEmpty()) return bitmap
            val reducida = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return bitmap
            bitmap.recycle()
            val ancho = reducida.width
            val alto = reducida.height
            val factor = if (limites.outWidth > 0) ancho.toFloat() / limites.outWidth else 1f
            val pixeles = IntArray(ancho * alto)
            reducida.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
            textos.forEach { texto ->
                TextoHoja.dibujarEscalado(
                    pixeles, ancho, alto, texto.texto, texto.tipografia,
                    texto.tamano, texto.escala * factor,
                    texto.centroX * factor, texto.centroY * factor, texto.color,
                    texto.voltearH, texto.voltearV,
                )
            }
            reducida.setPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
            reducida
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

    private fun escribir(
        contexto: Context,
        id: String,
        ancho: Int,
        alto: Int,
        pixeles: IntArray,
        textos: List<TextoEnHoja>,
    ): Boolean {
        val archivo = archivo(contexto, id) ?: return false
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
            escribirTextos(contexto, id, ancho, alto, textos)
            true
        }.getOrDefault(false)
    }

    private fun escribirTextos(
        contexto: Context,
        id: String,
        ancho: Int,
        alto: Int,
        textos: List<TextoEnHoja>,
    ) {
        val archivo = archivoTextos(contexto, id) ?: return
        if (textos.isEmpty()) {
            archivo.delete()
            return
        }
        val raiz = JSONObject()
        raiz.put("ancho", ancho)
        raiz.put("alto", alto)
        val lista = JSONArray()
        textos.forEach { texto ->
            lista.put(
                JSONObject()
                    .put("id", texto.id)
                    .put("texto", texto.texto)
                    .put("tipografia", texto.tipografia.name)
                    .put("tamano", texto.tamano.toDouble())
                    .put("color", texto.color)
                    .put("centroX", texto.centroX.toDouble())
                    .put("centroY", texto.centroY.toDouble())
                    .put("escala", texto.escala.toDouble())
                    .put("voltearH", texto.voltearH)
                    .put("voltearV", texto.voltearV),
            )
        }
        raiz.put("textos", lista)
        archivo.writeText(raiz.toString())
    }

    private fun leerTextos(
        contexto: Context,
        id: String,
        anchoHoja: Int,
        altoHoja: Int,
    ): List<TextoEnHoja> {
        val archivo = archivoTextos(contexto, id) ?: return emptyList()
        if (!archivo.isFile) return emptyList()
        return runCatching {
            val raiz = JSONObject(archivo.readText())
            // Si la hoja cambió de tamaño se descartan: las coordenadas viejas
            // ya no representan el dibujo guardado.
            if (raiz.optInt("ancho", anchoHoja) != anchoHoja ||
                raiz.optInt("alto", altoHoja) != altoHoja
            ) {
                return emptyList()
            }
            val lista = raiz.optJSONArray("textos") ?: return emptyList()
            buildList {
                for (i in 0 until lista.length()) {
                    val t = lista.optJSONObject(i) ?: continue
                    val tipografia = runCatching {
                        Tipografia.valueOf(t.getString("tipografia"))
                    }.getOrNull() ?: continue
                    val contenido = t.optString("texto")
                    if (contenido.isBlank()) continue
                    val centroX = t.optDouble("centroX", 0.0).toFloat()
                    val centroY = t.optDouble("centroY", 0.0).toFloat()
                    if (!centroX.isFinite() || !centroY.isFinite()) continue
                    val tamano = t.optDouble("tamano", 64.0).toFloat()
                        .takeIf { it.isFinite() }
                        ?.coerceIn(8f, 512f) ?: 64f
                    val escala = t.optDouble("escala", 1.0).toFloat()
                        .takeIf { it.isFinite() && it > 0f }
                        ?.coerceIn(0.05f, 100f) ?: 1f
                    add(
                        TextoEnHoja(
                            id = t.optLong("id", i.toLong() + 1),
                            texto = contenido,
                            tipografia = tipografia,
                            tamano = tamano,
                            color = t.optInt("color", 0xFF000000.toInt()),
                            centroX = centroX,
                            centroY = centroY,
                            escala = escala,
                            voltearH = t.optBoolean("voltearH", false),
                            voltearV = t.optBoolean("voltearV", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun borrarArchivos(contexto: Context, id: String): Boolean {
        val png = archivo(contexto, id)?.delete() == true
        archivoTextos(contexto, id)?.delete()
        return png
    }

    private fun archivo(contexto: Context, id: String): File? {
        val carpeta = carpeta(contexto) ?: return null
        return File(carpeta, "${limpio(id)}$SUFIJO")
    }

    private fun archivoTextos(contexto: Context, id: String): File? {
        val carpeta = carpeta(contexto) ?: return null
        return File(carpeta, "${limpio(id)}$SUFIJO_TEXTOS")
    }

    private fun limpio(id: String): String = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun carpeta(contexto: Context): File? {
        val carpeta = File(contexto.filesDir, CARPETA)
        if (!carpeta.exists() && !carpeta.mkdirs()) return null
        return carpeta
    }
}
