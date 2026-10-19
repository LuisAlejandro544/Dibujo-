package com.dibujo.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.roundToInt

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
 * Inserta texto en la hoja sin tocar el motor de rasterizado: dibuja las letras
 * en un bitmap chico con [Canvas]/[Paint] y mezcla el resultado sobre el buffer
 * ARGB de la hoja. La hoja se aplica después con `reemplazarHoja`, que ya tiene
 * paridad C++/Kotlin y deja el paso en el historial (se puede deshacer).
 *
 * Así las tipografías del sistema quedan sólo del lado Android y el núcleo
 * (`:core` y `:engine`) sigue sin depender de Android.
 */
object TextoHoja {

    /**
     * Dibuja [texto] (admite saltos de línea) centrado en
     * ([centroX], [centroY]) de hoja, con la [tipografia] y el tamaño
     * [tamano] px, usando [color] ARGB. Devuelve `true` si pintó algún píxel.
     */
    fun dibujar(
        pixeles: IntArray,
        ancho: Int,
        alto: Int,
        texto: String,
        tipografia: Tipografia,
        tamano: Float,
        centroX: Float,
        centroY: Float,
        color: Int,
    ): Boolean {
        if (texto.isBlank() || ancho <= 0 || alto <= 0) return false
        val lineas = texto.split('\n')
        val tamanoLetra = tamano.coerceIn(8f, 512f)
        val lapiz = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = tamanoLetra
            typeface = Typeface.create(tipografia.familia, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        val metricas = lapiz.fontMetrics
        val altoLinea = metricas.descent - metricas.ascent
        val anchoTexto = lineas.maxOf { lapiz.measureText(it) }
        if (anchoTexto <= 0f || altoLinea <= 0f) return false
        val margen = (tamanoLetra * 0.12f).coerceAtLeast(2f)
        val anchoBmp = ceil(anchoTexto + margen * 2f).toInt().coerceAtLeast(1)
        val altoBmp = ceil(altoLinea * lineas.size + margen * 2f).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(anchoBmp, altoBmp, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        var y = margen - metricas.ascent
        for (linea in lineas) {
            canvas.drawText(linea, margen, y, lapiz)
            y += altoLinea
        }
        val origen = IntArray(anchoBmp * altoBmp)
        bitmap.getPixels(origen, 0, anchoBmp, 0, 0, anchoBmp, altoBmp)
        bitmap.recycle()

        val x0 = (centroX - anchoBmp / 2f).roundToInt()
        val y0 = (centroY - altoBmp / 2f).roundToInt()
        var pinto = false
        for (fy in 0 until altoBmp) {
            val dy = y0 + fy
            if (dy !in 0 until alto) continue
            for (fx in 0 until anchoBmp) {
                val dx = x0 + fx
                if (dx !in 0 until ancho) continue
                val src = origen[fy * anchoBmp + fx]
                val alfa = (src ushr 24) and 0xFF
                if (alfa == 0) continue
                val i = dy * ancho + dx
                pixeles[i] = if (alfa >= 255) src else mezclar(src, pixeles[i], alfa)
                pinto = true
            }
        }
        return pinto
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
