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
    private var asistencia = false

    override val ancho: Int get() = hoja.ancho
    override val alto: Int get() = hoja.alto

    // Trazo en curso (coordenadas de hoja).
    private val trazo = FloatArray(MAX_PUNTOS * 2)
    private var puntos = 0

    // Cámara.
    private var panX = 0f
    private var panY = 0f
    private var zoomF = 1.0f
    private var rotacionF = 0f // radianes

    override val nombre: String = "Kotlin (respaldo)"

    override fun version(): String = nombre

    override fun configurarLapiz(lapiz: Lapiz) {
        this.lapiz = lapiz
    }

    override fun asistenciaRecta(activa: Boolean) {
        asistencia = activa
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
        // Trazo asistido: se descarta el recorrido tembloroso y se dibuja una
        // recta entre el primer y el último punto (coincide con el C++).
        if (asistencia && lapiz.herramienta != com.dibujo.core.Herramienta.PINTAR && puntos >= 4) {
            val x1 = trazo[(puntos - 1) * 2]
            val y1 = trazo[(puntos - 1) * 2 + 1]
            trazo[2] = x1
            trazo[3] = y1
            puntos = 2
        }
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
        // El borrador no lleva textura (debe borrar uniforme); el resto usa la
        // textura elegida. El C++ replica esta misma decisión (paridad).
        val textura = if (lapiz.herramienta == com.dibujo.core.Herramienta.BORRADOR) {
            com.dibujo.core.Textura.LISA
        } else {
            lapiz.textura
        }
        if (lapiz.herramienta == com.dibujo.core.Herramienta.DOBLE) {
            // Doble lápiz: dos líneas paralelas con la normal suavizada por
            // vértice (polilínea completa), para que no tiemblen en los
            // empalmes. Misma aritmética que el C++.
            hoja.polilineaDoble(
                trazo, puntos * 2, grosor,
                lapiz.punta == com.dibujo.core.Punta.REDONDA, color,
                separacionDoble(grosor), textura,
            )
            puntos = 0
            return true
        }
        // Se rasteriza toda la polilínea con cobertura máxima por píxel y una
        // sola mezcla: sin discos superpuestos ni bordes acumulados.
        hoja.polilineaGruesa(
            trazo, puntos * 2, grosor,
            lapiz.punta == com.dibujo.core.Punta.REDONDA, color, textura,
        )
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

    override fun limiteHistorial(pasos: Int) {
        historial.fijarMaximo(pasos)
    }

    override fun limpiarHoja() {
        historial.guardar(hoja)
        hoja.limpiar()
    }

    override fun limpiarTrazos(): Boolean {
        // Si no hay tinta no hay nada que limpiar (y no ensuciamos el historial).
        if (hoja.pixeles.all { it == hoja.fondo }) return false
        historial.guardar(hoja)
        hoja.limpiarTrazos()
        return true
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
        zoomF = (zoomF * factor).coerceIn(ZOOM_MIN, ZOOM_MAX)
    }

    override fun rotar(delta: Float) {
        rotacionF += delta
        // Normaliza a (-pi, pi] para no acumular vueltas (coincide con `rotar`
        // del C++).
        while (rotacionF > PI_F) rotacionF -= DOS_PI_F
        while (rotacionF <= -PI_F) rotacionF += DOS_PI_F
    }

    override fun centrarVista() {
        panX = 0f
        panY = 0f
        zoomF = 1.0f
        rotacionF = 0f
    }

    override fun camara(): Camara = Camara(panX, panY, zoomF, rotacionF)

    override fun renderizar(destino: IntArray, vw: Int, vh: Int) {
        val c = kotlin.math.cos(rotacionF.toDouble()).toFloat()
        val s = kotlin.math.sin(rotacionF.toDouble()).toFloat()
        for (y in 0 until vh) {
            for (x in 0 until vw) {
                // e = pantalla + pan; R(−rot) y des-escalado por el zoom.
                val ex = x + panX
                val ey = y + panY
                val hx = (c * ex + s * ey) / zoomF
                val hy = (-s * ex + c * ey) / zoomF
                destino[y * vw + x] = muestrearBilineal(hx, hy)
            }
        }
    }

    /**
     * Muestreo bilineal de la hoja en coordenadas continuas (centro del texel
     * en el entero). Al acercar ya no se ven bloques duros: el color se
     * interpola entre los 4 texeles vecinos. Idéntico al C++ (paridad).
     * Fuera de la hoja devuelve [FUERA].
     */
    private fun muestrearBilineal(hx: Float, hy: Float): Int {
        val ix = kotlin.math.floor(hx.toDouble()).toInt()
        val iy = kotlin.math.floor(hy.toDouble()).toInt()
        if (ix < 0 || iy < 0 || ix >= ancho || iy >= alto) return FUERA
        val fx = hx.toDouble() - ix
        val fy = hy.toDouble() - iy
        val ix1 = if (ix + 1 < ancho) ix + 1 else ix
        val iy1 = if (iy + 1 < alto) iy + 1 else iy
        val fila0 = iy * ancho
        val fila1 = iy1 * ancho
        val c00 = hoja.pixeles[fila0 + ix]
        val c10 = hoja.pixeles[fila0 + ix1]
        val c01 = hoja.pixeles[fila1 + ix]
        val c11 = hoja.pixeles[fila1 + ix1]
        val a = canalBilineal((c00 ushr 24) and 0xFF, (c10 ushr 24) and 0xFF,
            (c01 ushr 24) and 0xFF, (c11 ushr 24) and 0xFF, fx, fy)
        val r = canalBilineal((c00 ushr 16) and 0xFF, (c10 ushr 16) and 0xFF,
            (c01 ushr 16) and 0xFF, (c11 ushr 16) and 0xFF, fx, fy)
        val g = canalBilineal((c00 ushr 8) and 0xFF, (c10 ushr 8) and 0xFF,
            (c01 ushr 8) and 0xFF, (c11 ushr 8) and 0xFF, fx, fy)
        val b = canalBilineal(c00 and 0xFF, c10 and 0xFF, c01 and 0xFF, c11 and 0xFF, fx, fy)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Interpola un canal entre los 4 vecinos y redondea a entero (paridad C++). */
    private fun canalBilineal(
        c00: Int, c10: Int, c01: Int, c11: Int,
        fx: Double, fy: Double,
    ): Int {
        val arriba = c00 + (c10 - c00) * fx
        val abajo = c01 + (c11 - c01) * fx
        val v = arriba + (abajo - arriba) * fy
        return kotlin.math.floor(v + 0.5).toInt()
    }

    override fun copiarHoja(destino: IntArray): Boolean {
        val origen = hoja.pixeles
        if (destino.size < origen.size) return false
        System.arraycopy(origen, 0, destino, 0, origen.size)
        return true
    }

    override fun transformarHoja(
        voltearHorizontal: Boolean,
        voltearVertical: Boolean,
        desplazamientoX: Float,
        desplazamientoY: Float,
        escala: Float,
    ): Boolean {
        val cambia = voltearHorizontal || voltearVertical ||
            desplazamientoX != 0f || desplazamientoY != 0f || escala != 1f
        if (!cambia || escala <= 0f) return false
        historial.guardar(hoja)
        return hoja.transformar(voltearHorizontal, voltearVertical, desplazamientoX, desplazamientoY, escala)
    }

    override fun reemplazarHoja(pixeles: IntArray): Boolean {
        if (pixeles.size != hoja.ancho * hoja.alto) return false
        historial.guardar(hoja)
        hoja.reemplazar(pixeles)
        return true
    }

    override fun pixelHoja(x: Int, y: Int): Int = hoja.pixel(x, y)

    override fun close() {
        // Sin recursos nativos: nada que liberar.
    }

    private fun grosorEfectivo(): Int = lapiz.grosorEfectivo()

    /** Separación entre las dos líneas de la herramienta DOBLE (paridad C++). */
    private fun separacionDoble(grosor: Int): Float = (grosor + 2) * 0.5f

    companion object {
        const val MAX_PUNTOS = 4096
        const val FUERA = 0xFFECECEC.toInt() // gris claro fuera de la hoja
        const val ZOOM_MIN = 0.05f
        const val ZOOM_MAX = 64.0f
        private const val PI_F = 3.141592653589793f
        private const val DOS_PI_F = 6.283185307179586f
    }
}
