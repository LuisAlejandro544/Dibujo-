package com.dibujo.engine

import com.dibujo.core.Camara
import com.dibujo.core.Colores
import com.dibujo.core.Herramienta
import com.dibujo.core.HerramientaDoble
import com.dibujo.core.LadoDoble
import com.dibujo.core.Lapiz
import com.dibujo.core.Simetria
import com.dibujo.core.Textura
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotorKotlinTest {
    @Test
    fun puntoPintaPixel() {
        val m = MotorKotlin(10, 10)
        m.configurarLapiz(Lapiz(color = Colores.ROJO, grosor = 1))
        m.iniciarTrazo(3f, 4f)
        m.continuarTrazo(3f, 4f)
        assertTrue(m.terminarTrazo())
        assertEquals(Colores.ROJO, m.pixelHoja(3, 4))
    }

    @Test
    fun trazoContinuoPintaIntermedios() {
        val m = MotorKotlin(20, 10)
        m.configurarLapiz(Lapiz(color = Colores.NEGRO))
        m.iniciarTrazo(0f, 0f)
        m.continuarTrazo(10f, 0f)
        assertTrue(m.terminarTrazo())
        for (x in 0..10) assertEquals(Colores.NEGRO, m.pixelHoja(x, 0))
    }

    @Test
    fun deshacerRehacer() {
        val m = MotorKotlin(10, 10)
        m.configurarLapiz(Lapiz(color = Colores.AZUL))
        m.iniciarTrazo(1f, 1f)
        m.terminarTrazo()
        assertTrue(m.puedeDeshacer())
        assertTrue(m.deshacer())
        assertTrue(!m.puedeDeshacer())
        assertTrue(m.deshacerRehacerOK())
    }

    private fun MotorKotlin.deshacerRehacerOK(): Boolean {
        val ok = rehacer()
        return ok
    }

    @Test
    fun pintarRellenaRegionDelimitada() {
        val m = MotorKotlin(9, 9)
        // Borde formado por un lazo (pluma continua).
        m.configurarLapiz(Lapiz(color = Colores.NEGRO, grosor = 1))
        m.iniciarTrazo(1f, 1f)
        m.continuarTrazo(7f, 1f)
        m.continuarTrazo(7f, 7f)
        m.continuarTrazo(1f, 7f)
        m.continuarTrazo(1f, 1f)
        assertTrue(m.terminarTrazo())

        m.configurarLapiz(Lapiz(color = Colores.AMARILLO, grosor = 1, herramienta = Herramienta.PINTAR))
        m.iniciarTrazo(4f, 4f)
        assertTrue(m.terminarTrazo())
        assertEquals(Colores.AMARILLO, m.pixelHoja(4, 4))
        // El borde no se pinta con el balde (mismo color exacto del borde).
        assertEquals(Colores.NEGRO, m.pixelHoja(1, 1))
    }

    @Test
    fun expandirConservaElDibujo() {
        val m = MotorKotlin(8, 8)
        m.configurarLapiz(Lapiz(color = Colores.ROJO, grosor = 1))
        m.iniciarTrazo(2f, 3f)
        m.terminarTrazo()
        m.expandir(0, 0, 4, 2)
        assertEquals(12, m.ancho)
        assertEquals(10, m.alto)
        assertEquals(Colores.ROJO, m.pixelHoja(2, 3))
        assertEquals(Colores.BLANCO, m.pixelHoja(10, 8))
        assertTrue(!m.puedeDeshacer())
    }

    @Test
    fun voltearDosVecesVuelveAlOriginal() {
        val m = MotorKotlin(8, 6)
        m.configurarLapiz(Lapiz(color = Colores.ROJO, grosor = 1))
        m.iniciarTrazo(1f, 1f)
        m.continuarTrazo(6f, 4f)
        assertTrue(m.terminarTrazo())
        val antes = IntArray(8 * 6)
        assertTrue(m.copiarHoja(antes))
        assertTrue(m.transformarHoja(true, false, 0f, 0f, 1f))
        val espejo = IntArray(8 * 6)
        assertTrue(m.copiarHoja(espejo))
        assertEquals(Colores.ROJO, espejo[1 * 8 + 6], "el espejo mueve el trazo")
        assertTrue(m.transformarHoja(true, false, 0f, 0f, 1f))
        val despues = IntArray(8 * 6)
        assertTrue(m.copiarHoja(despues))
        for (i in antes.indices) assertEquals(antes[i], despues[i], "espejo doble: píxel $i")
        // No-op: no hay nada que transformar y no debe consumir historial.
        val deshacible = m.puedeDeshacer()
        assertTrue(!m.transformarHoja(false, false, 0f, 0f, 1f), "no-op")
        assertEquals(deshacible, m.puedeDeshacer(), "el no-op no cambia el historial")
    }

    @Test
    fun asistenciaEnderezaElTrazo() {
        val motores = mutableListOf<com.dibujo.core.MotorDibujo>(MotorKotlin(24, 24))
        if (NativeEngine.disponible()) motores.add(MotorNativo(24, 24))
        for (m in motores) {
            m.asistenciaRecta(true)
            m.configurarLapiz(Lapiz(color = Colores.NEGRO, grosor = 1))
            // Trazo tembloroso de (2,2) a (20,20): debe quedar la recta.
            m.iniciarTrazo(2f, 2f)
            m.continuarTrazo(4f, 12f)
            m.continuarTrazo(12f, 6f)
            m.continuarTrazo(20f, 20f)
            assertTrue(m.terminarTrazo(), "${m.nombre}: trazo")
            assertEquals(Colores.NEGRO, m.pixelHoja(11, 11), "${m.nombre}: la recta pasa por (11,11)")
            assertEquals(Colores.BLANCO, m.pixelHoja(11, 4), "${m.nombre}: fuera de la recta queda el fondo")
            m.close()
        }
    }
}

/**
 * Paridad entre el motor C++ y el respaldo Kotlin.
 * Solo corre si la librería nativa está disponible.
 */
class MotorNativoTest {
    @Test
    fun historialParidad() {
        if (!NativeEngine.disponible()) return

        for (m in listOf(MotorNativo(20, 10), MotorKotlin(20, 10))) {
            m.configurarLapiz(Lapiz(color = Colores.ROJO, grosor = 3))
            m.iniciarTrazo(1f, 5f)
            m.continuarTrazo(15f, 5f)
            assertTrue(m.terminarTrazo())
            m.configurarLapiz(Lapiz(color = Colores.NEGRO, grosor = 3))
            m.iniciarTrazo(1f, 9f)
            m.continuarTrazo(15f, 9f)
            assertTrue(m.terminarTrazo())
            assertTrue(m.puedeDeshacer(), "${m.nombre}: hay paso que deshacer")
            assertTrue(!m.puedeRehacer(), "${m.nombre}: no hay paso que rehacer")

            // Deshacer borra el último trazo y habilita rehacer.
            assertTrue(m.deshacer(), "${m.nombre}: deshacer 1")
            assertTrue(m.puedeRehacer(), "${m.nombre}: rehacer habilitado")
            val b1 = IntArray(20 * 10)
            m.renderizar(b1, 20, 10)
            assertEquals(Colores.BLANCO, b1[9 * 20 + 1], "${m.nombre}: trazo 2 deshecho")
            assertEquals(Colores.ROJO, b1[5 * 20 + 1], "${m.nombre}: trazo 1 queda")

            // Segunda ronda completa: deshacer todo, rehacer todo.
            assertTrue(m.deshacer(), "${m.nombre}: deshacer 2")
            assertTrue(!m.puedeDeshacer(), "${m.nombre}: historial vacío")
            val b2 = IntArray(20 * 10)
            m.renderizar(b2, 20, 10)
            assertEquals(Colores.BLANCO, b2[5 * 20 + 1], "${m.nombre}: hoja limpia")
            assertTrue(m.rehacer(), "${m.nombre}: rehacer 1")
            assertTrue(m.rehacer(), "${m.nombre}: rehacer 2")
            val b3 = IntArray(20 * 10)
            m.renderizar(b3, 20, 10)
            assertEquals(Colores.NEGRO, b3[9 * 20 + 1], "${m.nombre}: trazo 2 rehecho")
            assertEquals(Colores.ROJO, b3[5 * 20 + 1], "${m.nombre}: trazo 1 rehecho")
            assertTrue(!m.puedeRehacer(), "${m.nombre}: futuro vacío")
            m.close()
        }
    }

    /**
     * El historial guarda instantáneas **comprimidas** (RLE) de la hoja: con
     * una hoja grande cada paso ocupaba megabytes y la app se quedaba sin
     * memoria. La compresión es sin pérdida, así que deshacer y rehacer tienen
     * que devolver exactamente la misma hoja en C++ y en Kotlin.
     */
    @Test
    fun historialComprimidoParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return

        val trazos = listOf(
            listOf(2f to 2f, 20f to 6f),
            listOf(3f to 12f, 18f to 10f),
            listOf(5f to 5f, 6f to 6f, 7f to 5f),
        )
        val nativo = MotorNativo(24, 16)
        val kotlin = MotorKotlin(24, 16)
        for (m in listOf(nativo, kotlin)) {
            m.configurarLapiz(Lapiz(color = Colores.AZUL, grosor = 2))
            for (trazo in trazos) {
                m.iniciarTrazo(trazo[0].first, trazo[0].second)
                for (p in trazo.drop(1)) m.continuarTrazo(p.first, p.second)
                assertTrue(m.terminarTrazo(), "${m.nombre}: trazo terminado")
            }
        }
        assertEquals(hojaDe(kotlin), hojaDe(nativo), "las hojas difieren tras dibujar")

        // Deshacer y rehacer paso a paso: las hojas deben coincidir siempre.
        repeat(trazos.size) {
            assertTrue(nativo.deshacer(), "nativo: deshacer ${it + 1}")
            assertTrue(kotlin.deshacer(), "kotlin: deshacer ${it + 1}")
            assertEquals(hojaDe(kotlin), hojaDe(nativo), "discrepancia al deshacer ${it + 1}")
        }
        assertTrue(!nativo.puedeDeshacer(), "nativo: historial vacío")
        assertTrue(!kotlin.puedeDeshacer(), "kotlin: historial vacío")

        repeat(trazos.size) {
            assertTrue(nativo.rehacer(), "nativo: rehacer ${it + 1}")
            assertTrue(kotlin.rehacer(), "kotlin: rehacer ${it + 1}")
            assertEquals(hojaDe(kotlin), hojaDe(nativo), "discrepancia al rehacer ${it + 1}")
        }
        nativo.close()
        kotlin.close()
    }

    /** Copia la hoja de un motor como lista de píxeles (para comparar). */
    private fun hojaDe(m: com.dibujo.core.MotorDibujo): List<Int> {
        val buffer = IntArray(m.ancho * m.alto)
        assertTrue(m.copiarHoja(buffer), "${m.nombre}: copiarHoja")
        return buffer.toList()
    }

    /**
     * La simetría dibuja el trazo y sus reflejos respecto del centro de la
     * hoja: el motor tiene que hacerlo igual en C++ y en Kotlin (el reflejo es
     * una resta, así que no hay diferencias de coma flotante).
     */
    @Test
    fun simetriaParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return

        for (modo in Simetria.entries) {
            val nativo = MotorNativo(24, 16)
            val kotlin = MotorKotlin(24, 16)
            for (m in listOf(nativo, kotlin)) {
                m.simetria(modo)
                m.configurarLapiz(Lapiz(color = Colores.ROJO, grosor = 2))
                m.iniciarTrazo(3f, 2f)
                m.continuarTrazo(8f, 6f)
                m.continuarTrazo(5f, 12f)
                assertTrue(m.terminarTrazo(), "${m.nombre}: trazo terminado")
            }
            assertEquals(
                hojaDe(kotlin),
                hojaDe(nativo),
                "discrepancia con simetría $modo",
            )
            nativo.close()
            kotlin.close()
        }
    }

    /** Sin simetría no hay reflejos; con simetría vertical, sí. */
    @Test
    fun simetriaDibujaElReflejoEnKotlin() {
        val trazo = listOf(3f to 2f, 8f to 6f)
        val normal = MotorKotlin(24, 16)
        normal.configurarLapiz(Lapiz(color = Colores.ROJO, grosor = 2))
        normal.iniciarTrazo(trazo[0].first, trazo[0].second)
        normal.continuarTrazo(trazo[1].first, trazo[1].second)
        assertTrue(normal.terminarTrazo())
        assertEquals(Colores.BLANCO, normal.pixelHoja(20, 2), "sin simetría no hay reflejo")

        val simetrico = MotorKotlin(24, 16)
        simetrico.simetria(Simetria.VERTICAL)
        simetrico.configurarLapiz(Lapiz(color = Colores.ROJO, grosor = 2))
        simetrico.iniciarTrazo(trazo[0].first, trazo[0].second)
        simetrico.continuarTrazo(trazo[1].first, trazo[1].second)
        assertTrue(simetrico.terminarTrazo())
        assertEquals(Colores.ROJO, simetrico.pixelHoja(20, 2), "el reflejo vertical se dibuja")
        assertEquals(Colores.ROJO, simetrico.pixelHoja(3, 2), "el trazo original se dibuja")
        normal.close()
        simetrico.close()
    }

    @Test
    fun nativoDibujaLineaIgualQueKotlin() {
        if (!NativeEngine.disponible()) return

        val lapiz = Lapiz(color = Colores.ROJO, grosor = 3)
        val nativo = MotorNativo(20, 10)
        val kotlin = MotorKotlin(20, 10)
        for (m in listOf(nativo, kotlin)) {
            m.configurarLapiz(lapiz)
            m.iniciarTrazo(1f, 5f)
            m.continuarTrazo(15f, 5f)
            assertTrue(m.terminarTrazo())
        }

        val a = IntArray(20 * 10)
        val b = IntArray(20 * 10)
        nativo.renderizar(a, 20, 10)
        kotlin.renderizar(b, 20, 10)
        for (i in a.indices) {
            assertEquals(b[i], a[i], "discrepanza en píxel $i (nativo=${a[i]}, kotlin=${b[i]})")
        }
        nativo.close()
        kotlin.close()
    }

    @Test
    fun limiteHistorialParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        for (m in listOf(MotorNativo(20, 10), MotorKotlin(20, 10))) {
            m.limiteHistorial(2)
            m.configurarLapiz(Lapiz(color = Colores.ROJO, grosor = 1))
            for (y in 1..3) {
                m.iniciarTrazo(2f, y.toFloat())
                m.continuarTrazo(18f, y.toFloat())
                assertTrue(m.terminarTrazo(), "${m.nombre}: trazo $y")
            }
            // Con tope 2, el primer snapshot ya se descartó: quedan dos.
            assertTrue(m.deshacer(), "${m.nombre}: deshacer 1")
            assertTrue(m.deshacer(), "${m.nombre}: deshacer 2")
            assertTrue(!m.deshacer(), "${m.nombre}: el tope 2 no guarda más")
            m.close()
        }
    }

    @Test
    fun texturaParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        for (textura in Textura.entries) {
            val ancho = 26
            val alto = 18
            val nativo = MotorNativo(ancho, alto)
            val kotlin = MotorKotlin(ancho, alto)
            for (m in listOf(nativo, kotlin)) {
                m.configurarLapiz(
                    Lapiz(color = Colores.AZUL, grosor = 7, textura = textura),
                )
                m.iniciarTrazo(4f, 9f)
                m.continuarTrazo(9f, 4f)
                m.continuarTrazo(14f, 13f)
                m.continuarTrazo(21f, 6f)
                assertTrue(m.terminarTrazo(), "${m.nombre}: trazo con $textura")
            }
            val a = IntArray(ancho * alto)
            val b = IntArray(ancho * alto)
            assertTrue(nativo.copiarHoja(a), "nativo: copiarHoja $textura")
            assertTrue(kotlin.copiarHoja(b), "kotlin: copiarHoja $textura")
            for (i in a.indices) {
                assertEquals(b[i], a[i], "textura $textura: discrepancia en píxel $i")
            }
            nativo.close()
            kotlin.close()
        }
    }

    @Test
    fun nativoCamaraYRender() {
        if (!NativeEngine.disponible()) return
        val m = MotorNativo(40, 20)
        assertEquals(Camara(0f, 0f, 1f), m.camara())
        m.pan(10f, 5f)
        m.zoom(2.0f)
        assertEquals(Camara(10f, 5f, 2f), m.camara())
        val buf = IntArray(8 * 8)
        m.renderizar(buf, 8, 8)
        m.centrarVista()
        assertEquals(Camara(0f, 0f, 1f), m.camara())
        m.close()
    }

    @Test
    fun nativoVersionExponeString() {
        if (!NativeEngine.disponible()) return
        assertTrue(MotorNativo(4, 4).version().isNotEmpty())
    }

    @Test
    fun pintarParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        val nativo = MotorNativo(14, 14)
        val kotlin = MotorKotlin(14, 14)
        for (m in listOf(nativo, kotlin)) {
            m.configurarLapiz(Lapiz(color = Colores.AZUL, grosor = 3))
            m.iniciarTrazo(3f, 3f)
            m.continuarTrazo(11f, 3f)
            m.continuarTrazo(11f, 11f)
            m.continuarTrazo(3f, 11f)
            m.continuarTrazo(3f, 3f)
            assertTrue(m.terminarTrazo())
            m.configurarLapiz(Lapiz(color = Colores.AMARILLO, grosor = 1, herramienta = Herramienta.PINTAR))
            m.iniciarTrazo(7f, 7f)
            assertTrue(m.terminarTrazo())
        }
        val a = IntArray(14 * 14)
        val b = IntArray(14 * 14)
        nativo.renderizar(a, 14, 14)
        kotlin.renderizar(b, 14, 14)
        for (i in a.indices) {
            assertEquals(b[i], a[i], "relleno: discrepancia en píxel $i")
        }
        nativo.close()
        kotlin.close()
    }

    @Test
    fun rellenoParidadConBarreras() {
        if (!NativeEngine.disponible()) return
        val ancho = 32
        val alto = 32
        val azar = java.util.Random(20261017L)
        // Mismas barreras para los dos motores: se generan una vez y se aplican
        // idénticas (si no, el azar consumiría números distintos por motor).
        val barreras = List(24) {
            Pair(azar.nextInt(ancho).toFloat(), (azar.nextInt(alto - 2) + 1).toFloat())
        }
        val nativo = MotorNativo(ancho, alto)
        val kotlin = MotorKotlin(ancho, alto)
        for (m in listOf(nativo, kotlin)) {
            // Líneas que parten la hoja en varias regiones cerradas.
            m.configurarLapiz(Lapiz(color = Colores.NEGRO, grosor = 1))
            for ((x, y) in barreras) {
                m.iniciarTrazo(x, 0f)
                m.continuarTrazo(x, y)
                m.terminarTrazo()
            }
        }
        val preA = IntArray(ancho * alto)
        val preB = IntArray(ancho * alto)
        assertTrue(nativo.copiarHoja(preA), "nativo: copiarHoja pre")
        assertTrue(kotlin.copiarHoja(preB), "kotlin: copiarHoja pre")
        for (i in preA.indices) {
            assertEquals(preB[i], preA[i], "barreras: discrepancia en píxel $i")
        }
        for (m in listOf(nativo, kotlin)) {
            m.configurarLapiz(
                Lapiz(color = Colores.VERDE, grosor = 1, herramienta = Herramienta.PINTAR),
            )
            // Coordenada sin .5: `kotlin.math.round` empata al par y `std::lround`
            // empata afuera, así que un .5 elegiría un píxel distinto por motor.
            m.iniciarTrazo(15.25f, 15.25f)
            assertTrue(m.terminarTrazo(), "${m.nombre}: balde")
        }
        val a = IntArray(ancho * alto)
        val b = IntArray(ancho * alto)
        assertTrue(nativo.copiarHoja(a), "nativo: copiarHoja")
        assertTrue(kotlin.copiarHoja(b), "kotlin: copiarHoja")
        for (i in a.indices) {
            assertEquals(b[i], a[i], "relleno con barreras: discrepancia en píxel $i")
        }
        nativo.close()
        kotlin.close()
    }

    @Test
    fun rellenoCierraHuecoParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        val ancho = 27
        val alto = 27
        val nativo = MotorNativo(ancho, alto)
        val kotlin = MotorKotlin(ancho, alto)
        for (m in listOf(nativo, kotlin)) {
            m.configurarLapiz(Lapiz(color = Colores.NEGRO, grosor = 1))
            // Cuadrado (3,3)-(23,23) con un hueco de 2 px en el borde de arriba
            // (dos trazos: el hueco queda entre (11,3) y (14,3)).
            m.iniciarTrazo(3f, 3f)
            m.continuarTrazo(11f, 3f)
            assertTrue(m.terminarTrazo())
            m.iniciarTrazo(14f, 3f)
            m.continuarTrazo(23f, 3f)
            m.continuarTrazo(23f, 23f)
            m.continuarTrazo(3f, 23f)
            m.continuarTrazo(3f, 3f)
            assertTrue(m.terminarTrazo())
            // El balde cierra el hueco y rellena: mismo resultado en los dos.
            m.configurarLapiz(Lapiz(color = Colores.AMARILLO, grosor = 1, herramienta = Herramienta.PINTAR))
            m.iniciarTrazo(13f, 13f)
            assertTrue(m.terminarTrazo())
        }
        val a = IntArray(ancho * alto)
        val b = IntArray(ancho * alto)
        assertTrue(nativo.copiarHoja(a), "nativo: copiarHoja")
        assertTrue(kotlin.copiarHoja(b), "kotlin: copiarHoja")
        for (i in a.indices) {
            assertEquals(b[i], a[i], "cierre de hueco: discrepancia en píxel $i")
        }
        assertEquals(Colores.AMARILLO, b[13 * ancho + 13])
        nativo.close()
        kotlin.close()
    }

    @Test
    fun expandirParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        val nativo = MotorNativo(8, 6)
        val kotlin = MotorKotlin(8, 6)
        for (m in listOf(nativo, kotlin)) {
            m.configurarLapiz(Lapiz(color = Colores.ROJO, grosor = 3))
            m.iniciarTrazo(1f, 1f)
            m.continuarTrazo(6f, 4f)
            assertTrue(m.terminarTrazo())
            m.expandir(0, 0, 4, 3)
            assertEquals(12, m.ancho, "${m.nombre}: ancho")
            assertEquals(9, m.alto, "${m.nombre}: alto")
        }
        val a = IntArray(12 * 9)
        val b = IntArray(12 * 9)
        nativo.renderizar(a, 12, 9)
        kotlin.renderizar(b, 12, 9)
        for (i in a.indices) {
            assertEquals(b[i], a[i], "expandir: discrepancia en píxel $i")
        }
        nativo.close()
        kotlin.close()
    }

    @Test
    fun fueraDeHojaParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        val nativo = MotorNativo(4, 4)
        val kotlin = MotorKotlin(4, 4)
        // Un viewport más grande que la hoja revela el color exterior.
        val a = IntArray(8 * 8)
        val b = IntArray(8 * 8)
        nativo.renderizar(a, 8, 8)
        kotlin.renderizar(b, 8, 8)
        for (i in a.indices) {
            assertEquals(b[i], a[i], "exterior: discrepancia en píxel $i")
        }
        nativo.close()
        kotlin.close()
    }

    @Test
    fun copiarHojaParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        val nativo = MotorNativo(20, 10)
        val kotlin = MotorKotlin(20, 10)
        for (m in listOf(nativo, kotlin)) {
            m.configurarLapiz(Lapiz(color = Colores.VERDE, grosor = 3))
            m.iniciarTrazo(2f, 5f)
            m.continuarTrazo(17f, 5f)
            assertTrue(m.terminarTrazo())
            m.configurarLapiz(Lapiz(color = Colores.AZUL, grosor = 2, herramienta = Herramienta.PINTAR))
            m.iniciarTrazo(9f, 9f)
            assertTrue(m.terminarTrazo())
        }
        val a = IntArray(20 * 10)
        val b = IntArray(20 * 10)
        assertTrue(nativo.copiarHoja(a), "nativo: copiarHoja")
        assertTrue(kotlin.copiarHoja(b), "kotlin: copiarHoja")
        for (i in a.indices) {
            assertEquals(b[i], a[i], "copiarHoja: discrepancia en píxel $i")
        }
        // Un destino más chico que la hoja se rechaza sin copiar.
        assertTrue(!nativo.copiarHoja(IntArray(3)), "nativo: destino corto rechazado")
        assertTrue(!kotlin.copiarHoja(IntArray(3)), "kotlin: destino corto rechazado")
        nativo.close()
        kotlin.close()
    }

    @Test
    fun reemplazarHojaParidadNativoKotlin() {
        val motores = mutableListOf<com.dibujo.core.MotorDibujo>(MotorKotlin(6, 4))
        if (NativeEngine.disponible()) motores.add(MotorNativo(6, 4))
        val nueva = IntArray(6 * 4) { i -> if (i % 3 == 0) Colores.ROJO else Colores.BLANCO }
        for (m in motores) {
            m.configurarLapiz(Lapiz(color = Colores.AZUL, grosor = 2))
            m.iniciarTrazo(0f, 0f)
            m.continuarTrazo(5f, 3f)
            assertTrue(m.terminarTrazo(), "${m.nombre}: trazo")
            val antes = IntArray(6 * 4)
            assertTrue(m.copiarHoja(antes))

            assertTrue(m.reemplazarHoja(nueva), "${m.nombre}: reemplazarHoja")
            val tras = IntArray(6 * 4)
            assertTrue(m.copiarHoja(tras))
            for (i in tras.indices) assertEquals(nueva[i], tras[i], "${m.nombre}: píxel $i")

            // Un tamaño distinto se rechaza sin tocar la hoja.
            assertTrue(!m.reemplazarHoja(IntArray(3)), "${m.nombre}: tamaño corto rechazado")
            val sinCambios = IntArray(6 * 4)
            assertTrue(m.copiarHoja(sinCambios))
            for (i in sinCambios.indices) {
                assertEquals(nueva[i], sinCambios[i], "${m.nombre}: rechazo no debe tocar")
            }

            // Queda en el historial: deshacer vuelve al estado previo.
            assertTrue(m.deshacer(), "${m.nombre}: deshacer")
            val vuelta = IntArray(6 * 4)
            assertTrue(m.copiarHoja(vuelta))
            for (i in vuelta.indices) assertEquals(antes[i], vuelta[i], "${m.nombre}: deshacer $i")
            m.close()
        }
    }

    @Test
    fun zoomPermiteAcercarMucho() {
        // Antes el máximo era 8x; las apps profesionales permiten mucho más zoom.
        val motores = mutableListOf<com.dibujo.core.MotorDibujo>(MotorKotlin(20, 20))
        if (NativeEngine.disponible()) motores.add(MotorNativo(20, 20))
        for (m in motores) {
            m.zoom(100f)
            assertTrue(m.camara().zoom > 8f, "${m.nombre}: zoom=${m.camara().zoom}")
            m.centrarVista()
            assertEquals(1f, m.camara().zoom, "${m.nombre}: centrar resetea el zoom")
            m.close()
        }
    }

    @Test
    fun camaraIncluyeRotacionYCentrarLaResetea() {
        val motores = mutableListOf<com.dibujo.core.MotorDibujo>(MotorKotlin(40, 20))
        if (NativeEngine.disponible()) motores.add(MotorNativo(40, 20))
        for (m in motores) {
            m.rotar(0.5f)
            assertEquals(0.5f, m.camara().rotacion, "${m.nombre}: rotación")
            m.centrarVista()
            assertEquals(0f, m.camara().rotacion, "${m.nombre}: centrar resetea la rotación")
            assertEquals(Camara(0f, 0f, 1f), m.camara(), "${m.nombre}: cámara centrada")
            m.close()
        }
    }

    @Test
    fun rotacionParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        // Ángulos con cos/sin idénticos en ambos lados (múltiplos de 90°): así
        // la paridad píxel a píxel no depende de la última ulp de la libm.
        for (angulo in listOf(0f, 1.5707964f, 3.1415927f)) {
            val nativo = MotorNativo(20, 20)
            val kotlin = MotorKotlin(20, 20)
            for (m in listOf(nativo, kotlin)) {
                m.configurarLapiz(Lapiz(color = Colores.ROJO, grosor = 3))
                m.iniciarTrazo(4f, 4f)
                m.continuarTrazo(15f, 12f)
                assertTrue(m.terminarTrazo())
                m.rotar(angulo)
                m.pan(3f, -2f)
            }
            val a = IntArray(20 * 20)
            val b = IntArray(20 * 20)
            nativo.renderizar(a, 20, 20)
            kotlin.renderizar(b, 20, 20)
            for (i in a.indices) {
                assertEquals(b[i], a[i], "rotación $angulo: discrepancia en píxel $i")
            }
            nativo.close()
            kotlin.close()
        }
    }

    @Test
    fun dobleParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        val nativo = MotorNativo(24, 24)
        val kotlin = MotorKotlin(24, 24)
        for (m in listOf(nativo, kotlin)) {
            m.configurarLapiz(Lapiz(color = Colores.AZUL, grosor = 3, herramienta = Herramienta.DOBLE))
            m.iniciarTrazo(4f, 6f)
            m.continuarTrazo(18f, 6f)
            m.continuarTrazo(18f, 18f)
            assertTrue(m.terminarTrazo(), "${m.nombre}: trazo doble")
        }
        val a = IntArray(24 * 24)
        val b = IntArray(24 * 24)
        nativo.renderizar(a, 24, 24)
        kotlin.renderizar(b, 24, 24)
        for (i in a.indices) {
            assertEquals(b[i], a[i], "doble: discrepancia en píxel $i")
        }
        nativo.close()
        kotlin.close()
    }

    @Test
    fun dobleConfigurableParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        val nativo = MotorNativo(28, 28)
        val kotlin = MotorKotlin(28, 28)
        for (m in listOf(nativo, kotlin)) {
            m.configurarLapiz(
                Lapiz(
                    color = Colores.AZUL,
                    grosor = 3,
                    herramienta = Herramienta.DOBLE,
                    herramientaPrincipal = Herramienta.PORTAMINA,
                    herramientaDoble = HerramientaDoble.CARBONCILLO,
                    ladoDoble = LadoDoble.DERECHA,
                    colorDoble = Colores.ROJO,
                ),
            )
            m.iniciarTrazo(4f, 6f)
            m.continuarTrazo(18f, 6f)
            m.continuarTrazo(18f, 20f)
            assertTrue(m.terminarTrazo(), "${m.nombre}: trazo doble configurable")
        }
        val a = IntArray(28 * 28)
        val b = IntArray(28 * 28)
        assertTrue(nativo.copiarHoja(a), "nativo: copiarHoja")
        assertTrue(kotlin.copiarHoja(b), "kotlin: copiarHoja")
        for (i in a.indices) {
            assertEquals(b[i], a[i], "doble configurable: discrepancia en píxel $i")
        }
        nativo.close()
        kotlin.close()
    }

    @Test
    fun dobleMismoLadoParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        val nativo = MotorNativo(20, 20)
        val kotlin = MotorKotlin(20, 20)
        for (m in listOf(nativo, kotlin)) {
            m.configurarLapiz(
                Lapiz(
                    color = Colores.VERDE,
                    grosor = 2,
                    herramienta = Herramienta.DOBLE,
                    herramientaDoble = HerramientaDoble.CARBONCILLO,
                    ladoDoble = LadoDoble.MISMO,
                    colorDoble = Colores.ROJO,
                ),
            )
            m.iniciarTrazo(5f, 10f)
            m.continuarTrazo(15f, 10f)
            assertTrue(m.terminarTrazo(), "${m.nombre}: doble sobre el trazo")
        }
        val a = IntArray(20 * 20)
        val b = IntArray(20 * 20)
        assertTrue(nativo.copiarHoja(a), "nativo: copiarHoja")
        assertTrue(kotlin.copiarHoja(b), "kotlin: copiarHoja")
        for (i in a.indices) {
            assertEquals(b[i], a[i], "doble mismo lado: discrepancia en píxel $i")
        }
        nativo.close()
        kotlin.close()
    }

    @Test
    fun dobleDibujaElAcompananteConSuColor() {
        // Trazo horizontal hacia la derecha: la izquierda del trazo es arriba y
        // la derecha es abajo.
        val izquierda = MotorKotlin(24, 24)
        izquierda.configurarLapiz(
            Lapiz(
                color = Colores.AZUL,
                grosor = 2,
                herramienta = Herramienta.DOBLE,
                herramientaDoble = HerramientaDoble.PORTAMINA,
                ladoDoble = LadoDoble.IZQUIERDA,
                colorDoble = Colores.ROJO,
            ),
        )
        izquierda.iniciarTrazo(4f, 12f)
        izquierda.continuarTrazo(20f, 12f)
        assertTrue(izquierda.terminarTrazo())
        assertEquals(Colores.AZUL, izquierda.pixelHoja(12, 12), "trazo principal")
        assertEquals(Colores.ROJO, izquierda.pixelHoja(12, 10), "doble a la izquierda")
        assertEquals(Colores.BLANCO, izquierda.pixelHoja(12, 16), "lejos del doble")

        val derecha = MotorKotlin(24, 24)
        derecha.configurarLapiz(
            Lapiz(
                color = Colores.AZUL,
                grosor = 2,
                herramienta = Herramienta.DOBLE,
                herramientaDoble = HerramientaDoble.PORTAMINA,
                ladoDoble = LadoDoble.DERECHA,
                colorDoble = Colores.ROJO,
            ),
        )
        derecha.iniciarTrazo(4f, 12f)
        derecha.continuarTrazo(20f, 12f)
        assertTrue(derecha.terminarTrazo())
        assertEquals(Colores.AZUL, derecha.pixelHoja(12, 12), "trazo principal")
        assertEquals(Colores.ROJO, derecha.pixelHoja(12, 14), "doble a la derecha")
        assertEquals(Colores.BLANCO, derecha.pixelHoja(12, 8), "lejos del doble")
    }

    @Test
    fun dobleConPincelVectorialNoLoDibujaElMotor() {
        // El pincel vectorial lo dibuja la capa de vectores de la app: el
        // motor sólo pinta el trazo principal.
        val m = MotorKotlin(24, 24)
        m.configurarLapiz(
            Lapiz(
                color = Colores.AZUL,
                grosor = 2,
                herramienta = Herramienta.DOBLE,
                herramientaDoble = HerramientaDoble.PLUMA,
                ladoDoble = LadoDoble.IZQUIERDA,
                colorDoble = Colores.ROJO,
            ),
        )
        m.iniciarTrazo(4f, 12f)
        m.continuarTrazo(20f, 12f)
        assertTrue(m.terminarTrazo())
        assertEquals(Colores.AZUL, m.pixelHoja(12, 12), "trazo principal")
        assertEquals(Colores.BLANCO, m.pixelHoja(12, 10), "sin doble vectorial")
    }

    @Test
    fun limpiarTrazosParidadNativoKotlin() {        if (!NativeEngine.disponible()) return
        val ancho = 28
        val alto = 20
        val nativo = MotorNativo(ancho, alto)
        val kotlin = MotorKotlin(ancho, alto)
        for (m in listOf(nativo, kotlin)) {
            m.configurarLapiz(Lapiz(color = Colores.NEGRO, grosor = 5))
            // El mismo trazo repasado tres veces queda grueso.
            repeat(3) {
                m.iniciarTrazo(4f, 6f)
                m.continuarTrazo(23f, 8f)
                m.continuarTrazo(10f, 15f)
                assertTrue(m.terminarTrazo(), "${m.nombre}: trazo")
            }
            assertTrue(m.limpiarTrazos(), "${m.nombre}: limpiar trazos")
        }
        val a = IntArray(ancho * alto)
        val b = IntArray(ancho * alto)
        assertTrue(nativo.copiarHoja(a), "nativo: copiarHoja")
        assertTrue(kotlin.copiarHoja(b), "kotlin: copiarHoja")
        for (i in a.indices) {
            assertEquals(b[i], a[i], "limpiar: discrepancia en píxel $i")
        }
        nativo.close()
        kotlin.close()
    }

    @Test
    fun limpiarTrazosHojaVacia() {
        val motores = mutableListOf<com.dibujo.core.MotorDibujo>(MotorKotlin(10, 10))
        if (NativeEngine.disponible()) motores.add(MotorNativo(10, 10))
        for (m in motores) {
            assertTrue(!m.limpiarTrazos(), "${m.nombre}: hoja vacía no cambia")
            m.close()
        }
    }

    @Test
    fun transformarParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        val ancho = 16
        val alto = 12
        val nativo = MotorNativo(ancho, alto)
        val kotlin = MotorKotlin(ancho, alto)
        for (m in listOf(nativo, kotlin)) {
            m.configurarLapiz(Lapiz(color = Colores.ROJO, grosor = 3))
            m.iniciarTrazo(2f, 2f)
            m.continuarTrazo(8f, 5f)
            assertTrue(m.terminarTrazo(), "${m.nombre}: trazo")
            m.configurarLapiz(Lapiz(color = Colores.AZUL, grosor = 2))
            m.iniciarTrazo(3f, 9f)
            assertTrue(m.terminarTrazo(), "${m.nombre}: punto")
            assertTrue(
                m.transformarHoja(true, true, 3f, -2f, 1.5f),
                "${m.nombre}: transformar",
            )
        }
        val a = IntArray(ancho * alto)
        val b = IntArray(ancho * alto)
        assertTrue(nativo.copiarHoja(a), "nativo: copiarHoja")
        assertTrue(kotlin.copiarHoja(b), "kotlin: copiarHoja")
        for (i in a.indices) {
            assertEquals(b[i], a[i], "transformar: discrepancia en píxel $i")
        }
        nativo.close()
        kotlin.close()
    }

    @Test
    fun asistenciaRectaParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        val nativo = MotorNativo(24, 24)
        val kotlin = MotorKotlin(24, 24)
        for (m in listOf(nativo, kotlin)) {
            m.asistenciaRecta(true)
            m.configurarLapiz(Lapiz(color = Colores.AZUL, grosor = 2))
            m.iniciarTrazo(3f, 3f)
            m.continuarTrazo(5f, 10f)
            m.continuarTrazo(8f, 6f)
            m.continuarTrazo(20f, 19f)
            assertTrue(m.terminarTrazo(), "${m.nombre}: trazo asistido")
        }
        val a = IntArray(24 * 24)
        val b = IntArray(24 * 24)
        assertTrue(nativo.copiarHoja(a), "nativo: copiarHoja")
        assertTrue(kotlin.copiarHoja(b), "kotlin: copiarHoja")
        for (i in a.indices) {
            assertEquals(b[i], a[i], "asistencia: discrepancia en píxel $i")
        }
        nativo.close()
        kotlin.close()
    }

    @Test
    fun renderBilinealParidadNativoKotlin() {
        if (!NativeEngine.disponible()) return
        val nativo = MotorNativo(40, 30)
        val kotlin = MotorKotlin(40, 30)
        for (m in listOf(nativo, kotlin)) {
            m.configurarLapiz(Lapiz(color = Colores.NEGRO, grosor = 3))
            m.iniciarTrazo(4f, 4f)
            m.continuarTrazo(31f, 17f)
            assertTrue(m.terminarTrazo(), "${m.nombre}: trazo")
            // Zoom y pan fraccionarios: el muestreo bilineal interpola texeles.
            m.zoom(2.5f)
            m.pan(3.25f, -1.75f)
        }
        val ancho = 48
        val alto = 40
        val a = IntArray(ancho * alto)
        val b = IntArray(ancho * alto)
        nativo.renderizar(a, ancho, alto)
        kotlin.renderizar(b, ancho, alto)
        for (i in a.indices) {
            assertEquals(b[i], a[i], "bilineal: discrepancia en píxel $i")
        }
        nativo.close()
        kotlin.close()
    }
}
