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
