package com.dibujo.engine

import com.dibujo.core.Camara
import com.dibujo.core.Colores
import com.dibujo.core.Herramienta
import com.dibujo.core.Lapiz
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
}
