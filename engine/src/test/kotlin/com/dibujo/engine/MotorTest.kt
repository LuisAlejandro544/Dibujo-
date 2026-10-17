package com.dibujo.engine

import com.dibujo.core.Camara
import com.dibujo.core.Colores
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
}
