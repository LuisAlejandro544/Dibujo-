package com.dibujo.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.ceil

/**
 * Tipografías incluidas en Android (no se descarga ni empaqueta ninguna
 * fuente): las cinco familias clásicas del sistema. El nombre es el de la
 * familia que entiende [Typeface.create].
 */
enum class Tipografia(val familia: String) {
    SANS("sans-serif"),
    SERIF("serif"),
    MONO("monospace"),
    CURSIVA("cursive"),
    CASUAL("casual"),
}

/**
 * Texto vivo de la hoja: se dibuja como una capa Compose encima del lienzo (no
 * se hornea en los píxeles), así se puede mover, escalar y volver a editar en
 * cualquier momento, igual que el calco. El texto se hornea recién al exportar
 * o al aplicar una operación que rehace la hoja (recortar/redimensionar).
 *
 * Las coordenadas ([centroX]/[centroY]) y el tamaño están en px de hoja. La
 * [escala] la ajusta el usuario con el gesto de dos dedos.
 */
data class TextoEnHoja(
    val id: Long,
    val texto: String,
    val tipografia: Tipografia,
    val tamano: Float,
    val color: Int,
    val centroX: Float,
    val centroY: Float,
    val escala: Float = 1f,
    val voltearH: Boolean = false,
    val voltearV: Boolean = false,
)

/**
 * Compone texto con las fuentes del sistema sobre un bitmap ARGB. Se usa para
 * la capa viva (Compose) y para hornear el texto en la hoja al exportar. El
 * núcleo (`:core` y `:engine`) sigue sin depender de Android.
 */
object TextoHoja {

    /** Bitmap con [texto] (admite saltos de línea) ya compuesto y centrado. */
    fun crearBitmap(
        texto: String,
        tipografia: Tipografia,
        tamano: Float,
        color: Int,
        voltearH: Boolean = false,
        voltearV: Boolean = false,
    ): Bitmap? {
        if (texto.isBlank()) return null
        val (lapiz, lineas, metricas) = preparar(texto, tipografia, tamano, color)
        val altoLinea = metricas.descent - metricas.ascent
        val anchoTexto = lineas.maxOf { lapiz.measureText(it) }
        if (anchoTexto <= 0f || altoLinea <= 0f) return null
        val tamanoLetra = lapiz.textSize
        val margen = (tamanoLetra * 0.12f).coerceAtLeast(2f)
        val anchoBmp = ceil(anchoTexto + margen * 2f).toInt().coerceAtLeast(1)
        val altoBmp = ceil(altoLinea * lineas.size + margen * 2f).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(anchoBmp, altoBmp, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (voltearH || voltearV) {
            canvas.scale(
                if (voltearH) -1f else 1f,
                if (voltearV) -1f else 1f,
                anchoBmp / 2f,
                altoBmp / 2f,
            )
        }
        var y = margen - metricas.ascent
        for (linea in lineas) {
            canvas.drawText(linea, margen, y, lapiz)
            y += altoLinea
        }
        return bitmap
    }

    /** Tamaño (ancho × alto en px) que ocuparía el texto compuesto. */
    fun medidas(texto: String, tipografia: Tipografia, tamano: Float): Pair<Int, Int> {
        if (texto.isBlank()) return 0 to 0
        val (lapiz, lineas, metricas) = preparar(texto, tipografia, tamano, 0xFF000000.toInt())
        val altoLinea = metricas.descent - metricas.ascent
        val anchoTexto = lineas.maxOf { lapiz.measureText(it) }
        if (anchoTexto <= 0f || altoLinea <= 0f) return 0 to 0
        val margen = (lapiz.textSize * 0.12f).coerceAtLeast(2f)
        val anchoBmp = ceil(anchoTexto + margen * 2f).toInt().coerceAtLeast(1)
        val altoBmp = ceil(altoLinea * lineas.size + margen * 2f).toInt().coerceAtLeast(1)
        return anchoBmp to altoBmp
    }

    /**
     * Dibuja [texto] escalado por [escala] alrededor de su centro, recortando
     * lo que cae fuera de la hoja. Se usa para hornear los textos vivos en una
     * copia (exportar/recortar): el bitmap se compone al tamaño base y se
     * escala igual que la capa viva, así la imagen final coincide con lo que ve
     * el usuario sin reservar un bitmap gigante.
     */
    fun dibujarEscalado(
        pixeles: IntArray,
        ancho: Int,
        alto: Int,
        texto: String,
        tipografia: Tipografia,
        tamanoBase: Float,
        escala: Float,
        centroX: Float,
        centroY: Float,
        color: Int,
        voltearH: Boolean = false,
        voltearV: Boolean = false,
    ): Boolean {
        if (escala <= 0f || ancho <= 0 || alto <= 0) return false
        val base = crearBitmap(texto, tipografia, tamanoBase, color, voltearH, voltearV)
            ?: return false
        val anchoDestino = base.width * escala
        val altoDestino = base.height * escala
        val izquierda = centroX - anchoDestino / 2f
        val arriba = centroY - altoDestino / 2f
        val x0 = kotlin.math.floor(izquierda.toDouble()).toInt().coerceAtLeast(0)
        val y0 = kotlin.math.floor(arriba.toDouble()).toInt().coerceAtLeast(0)
        val x1 = ceil((izquierda + anchoDestino).toDouble()).toInt().coerceAtMost(ancho)
        val y1 = ceil((arriba + altoDestino).toDouble()).toInt().coerceAtMost(alto)
        if (x1 <= x0 || y1 <= y0) {
            base.recycle()
            return false
        }
        val dw = x1 - x0
        val dh = y1 - y0
        val escalado = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(escalado)
        val pincel = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(
            base,
            null,
            android.graphics.RectF(
                izquierda - x0,
                arriba - y0,
                izquierda - x0 + anchoDestino,
                arriba - y0 + altoDestino,
            ),
            pincel,
        )
        base.recycle()
        val origen = IntArray(dw * dh)
        escalado.getPixels(origen, 0, dw, 0, 0, dw, dh)
        escalado.recycle()
        var pinto = false
        for (fy in 0 until dh) {
            for (fx in 0 until dw) {
                val src = origen[fy * dw + fx]
                val alfa = (src ushr 24) and 0xFF
                if (alfa == 0) continue
                val i = (y0 + fy) * ancho + (x0 + fx)
                pixeles[i] = if (alfa >= 255) src else mezclar(src, pixeles[i], alfa)
                pinto = true
            }
        }
        return pinto
    }

    private fun preparar(
        texto: String,
        tipografia: Tipografia,
        tamano: Float,
        color: Int,
    ): Triple<Paint, List<String>, Paint.FontMetrics> {
        val lapiz = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = tamano.coerceIn(8f, 512f)
            typeface = Typeface.create(tipografia.familia, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        return Triple(lapiz, texto.split('\n'), lapiz.fontMetrics)
    }

    /** Mezcla [origen] sobre [destino] con cobertura [alfa] (0..255). */
    private fun mezclar(origen: Int, destino: Int, alfa: Int): Int {
        val inv = 255 - alfa
        val r = (((origen ushr 16) and 0xFF) * alfa + ((destino ushr 16) and 0xFF) * inv + 127) / 255
        val g = (((origen ushr 8) and 0xFF) * alfa + ((destino ushr 8) and 0xFF) * inv + 127) / 255
        val b = ((origen and 0xFF) * alfa + (destino and 0xFF) * inv + 127) / 255
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
