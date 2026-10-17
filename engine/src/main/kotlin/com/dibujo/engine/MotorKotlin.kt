package com.dibujo.engine

import com.dibujo.core.Camara
import com.dibujo.core.Lapiz
import com.dibujo.core.Lienzo
import com.dibujo.core.MotorDibujo

/**
 * Respaldo puro Kotlin del motor: misma lógica que el núcleo C++.
 * Se usa cuando la librería nativa no está disponible (p. ej. sin cmake/g++).
 */
class MotorKotlin(
    anchoInicial: Int,
    altoInicial: Int,
) : MotorDibujo {

    private val hoja = Lienzo(anchoInicial, altoInicial)
    private val historial = com.dibujo.core.Historial()
    private var lapiz = Lapiz()

    override val ancho: Int get() = hoja.ancho
    override val alto: Int get() = hoja.alto

    // Trazo en curso (coordenadas de hoja).
    private val trazo = FloatArray(MAX_PUNTOS * 2)
    private var puntos = 0

    // Cámara.
    private var panX = 0f
    private var panY = 0f
    private var zoomF = 1.0f

    override val nombre: String = "Kotlin (respaldo)"

    override fun version(): String = nombre

    override fun configurarLapiz(lapiz: Lapiz) {
        this.lapiz = lapiz
    }

    override fun iniciarTrazo(x: Float, y: Float) {
        cancelarTrazo()
        trazo[0] = x
        trazo[1] = y
        puntos = 1
    }

    override fun continuarTrazo(x: Float, y: Float) {
        if (puntos == 0) return
        val lastI = (puntos - 1) * 2
        val dx = x - trazo[lastI]
        val dy = y - trazo[lastI + 1]
        if (dx * dx + dy * dy < 1.0f) return
        if (puntos >= MAX_PUNTOS) return
        trazo[puntos * 2] = x
        trazo[puntos * 2 + 1] = y
        puntos++
    }

    override fun terminarTrazo(): Boolean {
        if (puntos == 0) return false
        historial.guardar(hoja)
        // La herramienta PINTAR ignora el trazo: rellena la región donde se
        // tocó con el color del lápiz (balde de pintura).
        if (lapiz.herramienta == com.dibujo.core.Herramienta.PINTAR) {
            hoja.rellenar(
                kotlin.math.round(trazo[0]).toInt(),
                kotlin.math.round(trazo[1]).toInt(),
                lapiz.color,
            )
            puntos = 0
            return true
        }
        val grosor = grosorEfectivo()
        val color = if (lapiz.herramienta == com.dibujo.core.Herramienta.BORRADOR) hoja.fondo else lapiz.color
        val radio = grosor / 2.0f
        if (puntos == 1) {
            if (lapiz.punta == com.dibujo.core.Punta.REDONDA) hoja.disco(trazo[0], trazo[1], radio, color)
            else hoja.bloque(trazo[0], trazo[1], radio, color)
        } else {
            for (i in 0 until puntos - 1) {
                hoja.lineaGruesa(
                    trazo[i * 2], trazo[i * 2 + 1],
                    trazo[(i + 1) * 2], trazo[(i + 1) * 2 + 1],
                    grosor, lapiz.punta == com.dibujo.core.Punta.REDONDA, color,
                )
            }
        }
        puntos = 0
        return true
    }

    override fun cancelarTrazo() {
        puntos = 0
    }

    override fun deshacer(): Boolean = historial.deshacer(hoja)
    override fun rehacer(): Boolean = historial.rehacer(hoja)
    override fun puedeDeshacer(): Boolean = historial.puedeDeshacer()
    override fun puedeRehacer(): Boolean = historial.puedeRehacer()

    override fun limpiarHoja() {
        historial.guardar(hoja)
        hoja.limpiar()
    }

    override fun expandir(izquierda: Int, arriba: Int, derecha: Int, abajo: Int) {
        historial.limpiar()
        hoja.expandir(izquierda, arriba, derecha, abajo)
    }

    override fun pan(dx: Float, dy: Float) {
        val maxX = ancho * zoomF
        val maxY = alto * zoomF
        panX = (panX + dx).coerceIn(-maxX, maxX)
        panY = (panY + dy).coerceIn(-maxY, maxY)
    }

    override fun zoom(factor: Float) {
        if (factor <= 0f) return
        zoomF = (zoomF * factor).coerceIn(0.25f, 8.0f)
    }

    override fun centrarVista() {
        panX = 0f
        panY = 0f
        zoomF = 1.0f
    }

    override fun camara(): Camara = Camara(panX, panY, zoomF)

    override fun renderizar(destino: IntArray, vw: Int, vh: Int) {
        for (y in 0 until vh) {
            for (x in 0 until vw) {
                val hx = ((x + panX) / zoomF).toInt()
                val hy = ((y + panY) / zoomF).toInt()
                destino[y * vw + x] = if (hx in 0 until ancho && hy in 0 until alto) {
                    hoja.pixeles[hy * ancho + hx]
                } else {
                    FUERA
                }
            }
        }
    }

    override fun copiarHoja(destino: IntArray): Boolean {
        val origen = hoja.pixeles
        if (destino.size < origen.size) return false
        System.arraycopy(origen, 0, destino, 0, origen.size)
        return true
    }

    /** Color de un píxel de la hoja (para tests). */
    fun pixelHoja(x: Int, y: Int): Int = hoja.pixel(x, y)

    override fun close() {
        // Sin recursos nativos: nada que liberar.
    }

    private fun grosorEfectivo(): Int = lapiz.grosorEfectivo()

    companion object {
        const val MAX_PUNTOS = 4096
        const val FUERA = 0xFFECECEC.toInt() // gris claro fuera de la hoja
    }
}
