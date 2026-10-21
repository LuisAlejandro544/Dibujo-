package com.dibujo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests del estabilizador de mano: suaviza el temblor de los dedos sin
 * deformar el trazo (el punto sigue yendo hacia donde va el dedo).
 */
class EstabilizadorTrazoTest {

    @Test
    fun sinSuavizadoLosPuntosPasanSinCambio() {
        val e = EstabilizadorTrazo(0f)
        e.estabilizar(10f, 20f)
        e.estabilizar(30f, 40f)
        assertEquals(30f, e.x)
        assertEquals(40f, e.y)
    }

    @Test
    fun elPrimerPuntoNoSeMueve() {
        val e = EstabilizadorTrazo(0.9f)
        e.estabilizar(5f, 7f)
        assertEquals(5f, e.x)
        assertEquals(7f, e.y)
    }

    @Test
    fun suavizarAcortaElTemblor() {
        val e = EstabilizadorTrazo(0.8f)
        e.estabilizar(0f, 0f)
        // El dedo tiembla: va y vuelve cerca del mismo punto.
        e.estabilizar(10f, 0f)
        e.estabilizar(0f, 0f)
        val x = e.x
        assertTrue(x in 0f..2f, "el temblor queda suavizado (x=$x)")
    }

    @Test
    fun elTrazoSigueAvanzandoHaciaElDedo() {
        val e = EstabilizadorTrazo(0.5f)
        e.estabilizar(0f, 0f)
        var anterior = 0f
        repeat(20) {
            e.estabilizar(anterior + 10f, 0f)
            assertTrue(e.x > anterior, "el trazo avanza (x=${e.x})")
            anterior = e.x
        }
    }

    @Test
    fun reiniciarEmpiezaUnTrazoNuevo() {
        val e = EstabilizadorTrazo(0.9f)
        e.estabilizar(0f, 0f)
        e.estabilizar(100f, 0f)
        e.reiniciar()
        e.estabilizar(7f, 7f)
        assertEquals(7f, e.x, "el primer punto del trazo nuevo pasa sin suavizar")
        assertEquals(7f, e.y)
    }

    @Test
    fun elNivelSeRecortaAlRangoValido() {
        val e = EstabilizadorTrazo(5f)
        assertEquals(EstabilizadorTrazo.MAX_SUAVIZADO, e.suavizado)
        e.suavizado = -1f
        assertEquals(0f, e.suavizado)
    }
}
