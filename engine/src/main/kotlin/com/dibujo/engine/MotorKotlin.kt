package com.dibujo.engine

import com.dibujo.core.Camara
import com.dibujo.core.InstantaneaHoja
import com.dibujo.core.Lapiz
import com.dibujo.core.Lienzo
import com.dibujo.core.MotorDibujo

/**
 * Respaldo puro Kotlin del motor: misma lógica que el núcleo C++.
 * Soporta hasta [MotorDibujo.MAXIMO_CAPAS] capas raster (por defecto 1).
 */
class MotorKotlin(
    anchoInicial: Int,
    altoInicial: Int,
) : MotorDibujo {

    private data class Capa(
        val lienzo: Lienzo,
        var visible: Boolean = true,
        var opacidad: Float = 1f,
    )

    private val capas = mutableListOf(Capa(Lienzo(anchoInicial, altoInicial)))
    private var indiceCapaActiva = 0

    private val hoja: Lienzo get() = capas[indiceCapaActiva].lienzo

    // Historial por capa activa: cada paso recuerda qué capa se modificó.
    private data class PasoCapa(val indice: Int, val instantanea: InstantaneaHoja)
    private val pasado = mutableListOf<PasoCapa>()
    private val futuro = mutableListOf<PasoCapa>()
    private var maxHistorial = 30

    private var lapiz = Lapiz()
    private var asistencia = false
    private var simetria = com.dibujo.core.Simetria.NINGUNA

    override val ancho: Int get() = capas[0].lienzo.ancho
    override val alto: Int get() = capas[0].lienzo.alto

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

    override fun simetria(modo: com.dibujo.core.Simetria) {
        simetria = modo
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
        guardarPaso()
        if (asistencia && lapiz.herramienta != com.dibujo.core.Herramienta.PINTAR && puntos >= 4) {
            val x1 = trazo[(puntos - 1) * 2]
            val y1 = trazo[(puntos - 1) * 2 + 1]
            trazo[2] = x1
            trazo[3] = y1
            puntos = 2
        }
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
        val textura = if (lapiz.herramienta == com.dibujo.core.Herramienta.BORRADOR) {
            com.dibujo.core.Textura.LISA
        } else {
            lapiz.textura
        }
        val cantidad = puntos * 2
        val redonda = lapiz.punta == com.dibujo.core.Punta.REDONDA
        val separacion = com.dibujo.core.TrazoDoble.separacion(grosor)
        val grosorDoble = lapiz.grosorEfectivo(lapiz.herramientaDoble.herramienta)
        val colorDoble = lapiz.colorDoble
        val dibujarDoble = { ps: FloatArray, n: Int ->
            if (lapiz.herramientaDoble != com.dibujo.core.HerramientaDoble.PLUMA) {
                val desplazado = com.dibujo.core.TrazoDoble.acompanante(
                    ps, n, separacion, lapiz.ladoDoble,
                )
                hoja.polilineaGruesa(desplazado, n, grosorDoble, redonda, colorDoble, textura)
            }
        }
        val dibujarPrincipal = { ps: FloatArray, n: Int ->
            hoja.polilineaGruesa(ps, n, grosor, redonda, color, textura)
        }
        val dibujar = { ps: FloatArray, n: Int ->
            if (lapiz.herramienta == com.dibujo.core.Herramienta.DOBLE) {
                dibujarDoble(ps, n)
                dibujarPrincipal(ps, n)
            } else {
                dibujarPrincipal(ps, n)
            }
        }
        dibujar(trazo, cantidad)
        if (simetria != com.dibujo.core.Simetria.NINGUNA) {
            val vertical = simetria == com.dibujo.core.Simetria.VERTICAL ||
                simetria == com.dibujo.core.Simetria.CUADRADA
            val horizontal = simetria == com.dibujo.core.Simetria.HORIZONTAL ||
                simetria == com.dibujo.core.Simetria.CUADRADA
            if (vertical) {
                val reflejo = reflejar(trazo, cantidad, hoja.ancho, hoja.alto, true, false)
                dibujar(reflejo, cantidad)
            }
            if (horizontal) {
                val reflejo = reflejar(trazo, cantidad, hoja.ancho, hoja.alto, false, true)
                dibujar(reflejo, cantidad)
            }
            if (vertical && horizontal) {
                val reflejo = reflejar(trazo, cantidad, hoja.ancho, hoja.alto, true, true)
                dibujar(reflejo, cantidad)
            }
        }
        puntos = 0
        return true
    }

    private fun reflejar(
        puntos: FloatArray,
        cantidad: Int,
        ancho: Int,
        alto: Int,
        ejeX: Boolean,
        ejeY: Boolean,
    ): FloatArray {
        val salida = FloatArray(cantidad)
        var i = 0
        while (i + 1 < cantidad) {
            var x = puntos[i]
            var y = puntos[i + 1]
            if (ejeX) x = (ancho - 1).toFloat() - x
            if (ejeY) y = (alto - 1).toFloat() - y
            salida[i] = x
            salida[i + 1] = y
            i += 2
        }
        return salida
    }

    override fun cancelarTrazo() {
        puntos = 0
    }

    override fun deshacer(): Boolean {
        if (pasado.isEmpty()) return false
        val paso = pasado.removeAt(pasado.lastIndex)
        futuro.add(PasoCapa(paso.indice, InstantaneaHoja.capturar(capas[paso.indice].lienzo.pixeles)))
        paso.instantanea.restaurar(capas[paso.indice].lienzo.pixeles)
        return true
    }

    override fun rehacer(): Boolean {
        if (futuro.isEmpty()) return false
        val paso = futuro.removeAt(futuro.lastIndex)
        pasado.add(PasoCapa(paso.indice, InstantaneaHoja.capturar(capas[paso.indice].lienzo.pixeles)))
        paso.instantanea.restaurar(capas[paso.indice].lienzo.pixeles)
        return true
    }

    override fun puedeDeshacer(): Boolean = pasado.isNotEmpty()
    override fun puedeRehacer(): Boolean = futuro.isNotEmpty()

    override fun limiteHistorial(pasos: Int) {
        maxHistorial = pasos.coerceIn(1, 1000)
        while (pasado.size > maxHistorial) pasado.removeAt(0)
    }

    private fun guardarPaso() {
        pasado.add(PasoCapa(indiceCapaActiva, InstantaneaHoja.capturar(hoja.pixeles)))
        if (pasado.size > maxHistorial) pasado.removeAt(0)
        futuro.clear()
    }

    override fun limpiarHoja() {
        guardarPaso()
        hoja.limpiar()
    }

    override fun limpiarTrazos(): Boolean {
        if (capas.all { c -> c.lienzo.pixeles.all { it == c.lienzo.fondo } }) {
            // solo verificar capa activa para el no-op histórico
        }
        if (hoja.pixeles.all { it == hoja.fondo }) return false
        guardarPaso()
        hoja.limpiarTrazos()
        return true
    }

    override fun expandir(izquierda: Int, arriba: Int, derecha: Int, abajo: Int) {
        pasado.clear()
        futuro.clear()
        for (c in capas) c.lienzo.expandir(izquierda, arriba, derecha, abajo)
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

    // ----- composición de capas -----

    private fun componerHoja(destino: IntArray) {
        // fondo base
        val n = ancho * alto
        for (i in 0 until n) destino[i] = capas[0].lienzo.fondo
        // source-over de abajo a arriba
        for (c in capas) {
            if (!c.visible) continue
            val op = c.opacidad.coerceIn(0f, 1f)
            val px = c.lienzo.pixeles
            for (i in 0 until n) {
                val src = px[i]
                val sa = ((src ushr 24) and 0xFF) * op
                val saI = sa.toInt().coerceIn(0, 255)
                if (saI == 0) continue
                val dst = destino[i]
                val da = (dst ushr 24) and 0xFF
                val inv = 255 - saI
                val na = saI + (da * inv) / 255
                if (na == 0) { destino[i] = 0; continue }
                // compute properly:
                val sr = (src ushr 16) and 0xFF
                val sg = (src ushr 8) and 0xFF
                val sb = src and 0xFF
                val dr = (dst ushr 16) and 0xFF
                val dg = (dst ushr 8) and 0xFF
                val db = dst and 0xFF
                val outA = na
                val outR = (sr * saI + dr * da * inv / 255) / outA.coerceAtLeast(1)
                val outG = (sg * saI + dg * da * inv / 255) / outA.coerceAtLeast(1)
                val outB = (sb * saI + db * da * inv / 255) / outA.coerceAtLeast(1)
                destino[i] = (outA shl 24) or (outR.coerceIn(0,255) shl 16) or (outG.coerceIn(0,255) shl 8) or outB.coerceIn(0,255)
            }
        }
    }

    override fun renderizar(destino: IntArray, vw: Int, vh: Int) {
        // componer hoja completa en buffer temporal
        val compuesto = IntArray(ancho * alto)
        componerHoja(compuesto)
        val c = kotlin.math.cos(rotacionF.toDouble()).toFloat()
        val s = kotlin.math.sin(rotacionF.toDouble()).toFloat()
        for (y in 0 until vh) {
            for (x in 0 until vw) {
                val ex = x + panX
                val ey = y + panY
                val hx = (c * ex + s * ey) / zoomF
                val hy = (-s * ex + c * ey) / zoomF
                destino[y * vw + x] = muestrearBilineal(compuesto, hx, hy)
            }
        }
    }

    private fun muestrearBilineal(buf: IntArray, hx: Float, hy: Float): Int {
        val ix = kotlin.math.floor(hx.toDouble()).toInt()
        val iy = kotlin.math.floor(hy.toDouble()).toInt()
        if (ix < 0 || iy < 0 || ix >= ancho || iy >= alto) return FUERA
        val fx = hx.toDouble() - ix
        val fy = hy.toDouble() - iy
        val ix1 = if (ix + 1 < ancho) ix + 1 else ix
        val iy1 = if (iy + 1 < alto) iy + 1 else iy
        val fila0 = iy * ancho
        val fila1 = iy1 * ancho
        val c00 = buf[fila0 + ix]
        val c10 = buf[fila0 + ix1]
        val c01 = buf[fila1 + ix]
        val c11 = buf[fila1 + ix1]
        val a = canalBilineal((c00 ushr 24) and 0xFF, (c10 ushr 24) and 0xFF,
            (c01 ushr 24) and 0xFF, (c11 ushr 24) and 0xFF, fx, fy)
        val r = canalBilineal((c00 ushr 16) and 0xFF, (c10 ushr 16) and 0xFF,
            (c01 ushr 16) and 0xFF, (c11 ushr 16) and 0xFF, fx, fy)
        val g = canalBilineal((c00 ushr 8) and 0xFF, (c10 ushr 8) and 0xFF,
            (c01 ushr 8) and 0xFF, (c11 ushr 8) and 0xFF, fx, fy)
        val b = canalBilineal(c00 and 0xFF, c10 and 0xFF, c01 and 0xFF, c11 and 0xFF, fx, fy)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

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
        val compuesto = IntArray(ancho * alto)
        componerHoja(compuesto)
        if (destino.size < compuesto.size) return false
        System.arraycopy(compuesto, 0, destino, 0, compuesto.size)
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
        guardarPaso()
        return hoja.transformar(voltearHorizontal, voltearVertical, desplazamientoX, desplazamientoY, escala)
    }

    override fun reemplazarHoja(pixeles: IntArray): Boolean {
        if (pixeles.size != hoja.ancho * hoja.alto) return false
        guardarPaso()
        hoja.reemplazar(pixeles)
        return true
    }

    override fun pixelHoja(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= ancho || y >= alto) return FUERA
        // componer en ese punto
        val i = y * ancho + x
        var dst = capas[0].lienzo.fondo
        for (c in capas) {
            if (!c.visible) continue
            val op = c.opacidad.coerceIn(0f, 1f)
            val src = c.lienzo.pixeles[i]
            val sa = ((src ushr 24) and 0xFF) * op / 1f
            val saI = sa.toInt().coerceIn(0, 255)
            if (saI == 0) continue
            val da = (dst ushr 24) and 0xFF
            val inv = 255 - saI
            val na = saI + (da * inv) / 255
            if (na == 0) { dst = 0; continue }
            val sr = (src ushr 16) and 0xFF
            val sg = (src ushr 8) and 0xFF
            val sb = src and 0xFF
            val dr = (dst ushr 16) and 0xFF
            val dg = (dst ushr 8) and 0xFF
            val db = dst and 0xFF
            val outR = (sr * saI + dr * da * inv / 255) / na.coerceAtLeast(1)
            val outG = (sg * saI + dg * da * inv / 255) / na.coerceAtLeast(1)
            val outB = (sb * saI + db * da * inv / 255) / na.coerceAtLeast(1)
            dst = (na.coerceIn(0,255) shl 24) or (outR.coerceIn(0,255) shl 16) or (outG.coerceIn(0,255) shl 8) or outB.coerceIn(0,255)
        }
        return dst
    }

    override fun close() {}

    // ----- API de capas -----

    override val maximoCapas: Int get() = MotorDibujo.MAXIMO_CAPAS
    override val numeroCapas: Int get() = capas.size
    override val capaActiva: Int get() = indiceCapaActiva

    override fun agregarCapa(): Boolean {
        if (capas.size >= maximoCapas) return false
        capas.add(Capa(Lienzo(ancho, alto, capas[0].lienzo.fondo)))
        return true
    }

    override fun eliminarCapa(indice: Int): Boolean {
        if (capas.size <= 1) return false
        if (indice < 0 || indice >= capas.size) return false
        capas.removeAt(indice)
        if (indiceCapaActiva >= capas.size) indiceCapaActiva = capas.size - 1
        if (indiceCapaActiva < 0) indiceCapaActiva = 0
        return true
    }

    override fun seleccionarCapa(indice: Int): Boolean {
        if (indice < 0 || indice >= capas.size) return false
        indiceCapaActiva = indice
        return true
    }

    override fun visibilidadCapa(indice: Int, visible: Boolean) {
        if (indice in capas.indices) capas[indice].visible = visible
    }

    override fun opacidadCapa(indice: Int, alfa: Float) {
        if (indice in capas.indices) capas[indice].opacidad = alfa.coerceIn(0f, 1f)
    }

    override fun esCapaVisible(indice: Int): Boolean =
        if (indice in capas.indices) capas[indice].visible else false

    override fun opacidadDeCapa(indice: Int): Float =
        if (indice in capas.indices) capas[indice].opacidad else 0f

    private fun grosorEfectivo(): Int = lapiz.grosorEfectivo()

    companion object {
        const val MAX_PUNTOS = 4096
        const val FUERA = 0xFFECECEC.toInt()
        const val ZOOM_MIN = 0.05f
        const val ZOOM_MAX = 64.0f
        private const val PI_F = 3.141592653589793f
        private const val DOS_PI_F = 6.283185307179586f
    }
}
