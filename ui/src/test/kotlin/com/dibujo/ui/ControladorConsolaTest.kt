package com.dibujo.ui

import com.dibujo.core.Colores
import com.dibujo.core.Herramienta
import com.dibujo.core.HerramientaDoble
import com.dibujo.core.LadoDoble
import com.dibujo.core.Modo
import com.dibujo.engine.MotorKotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControladorConsolaTest {

    private fun controlador(): ControladorConsola =
        ControladorConsola(MotorKotlin(12, 8), vw = 10, vh = 5, conColor = false)

    @Test
    fun colorDirectoPorNumero() {
        val c = controlador()
        val accion = c.procesar("3")
        assertEquals(ControladorConsola.Accion.REDIBUJAR, accion)
        assertEquals(Colores.ROJO, c.lapiz.color)
    }

    @Test
    fun numeroFueraDeRangoNoCambiaColor() {
        val c = controlador()
        c.procesar("99")
        assertEquals(Colores.NEGRO, c.lapiz.color)
    }

    @Test
    fun pintarPuntoEnCursor() {
        val c = controlador()
        c.procesar("3") // rojo
        c.procesar("d 4") // mueve cursor a (4,0)
        c.procesar("p")
        assertEquals(Colores.ROJO, (c.motor as MotorKotlin).pixelHoja(4, 0))
    }

    @Test
    fun plumaAbajoYMovimientoTrazanLinea() {
        val c = controlador()
        c.procesar("3")
        c.procesar(".") // pluma abajo en (0,0)
        c.procesar("4d") // cursor a (4,0)
        val accion = c.procesar(".") // soltar
        assertEquals(ControladorConsola.Accion.REDIBUJAR, accion)
        val m = c.motor as MotorKotlin
        for (x in 0..4) assertEquals(Colores.ROJO, m.pixelHoja(x, 0), "píxel ($x,0) debía estar pintado")
    }

    @Test
    fun modoMoverNoPinta() {
        val c = controlador()
        c.procesar("3")
        val accion = c.procesar("m")
        assertEquals(Modo.MOVER, c.modo)
        c.procesar("p") // en modo MOVER no debe pintar
        val m = c.motor as MotorKotlin
        assertEquals(Colores.BLANCO, m.pixelHoja(0, 0))
        assertTrue(accion == ControladorConsola.Accion.REDIBUJAR)
    }

    @Test
    fun modoMoverPaneaLaVista() {
        val c = controlador()
        c.procesar("m")
        c.procesar("d")
        assertTrue(c.motor.camara().panX > 0f)
    }

    @Test
    fun salirConQ() {
        val c = controlador()
        assertEquals(ControladorConsola.Accion.SALIR, c.procesar("q"))
    }

    @Test
    fun menuColoresElegirYCancelar() {
        val c = controlador()
        assertEquals(ControladorConsola.Accion.MENU_COLORES, c.procesar("c"))
        val sb = StringBuilder()
        val accion = c.menuColores(listOf("4").iterator(), sb)
        assertEquals(ControladorConsola.Accion.REDIBUJAR, accion)
        assertEquals(Colores.VERDE, c.lapiz.color)
        assertTrue(sb.toString().contains("— Elige un color —"))
    }

    @Test
    fun menuColoresCancelar() {
        val c = controlador()
        c.procesar("c")
        c.menuColores(listOf("0").iterator(), StringBuilder())
        assertEquals(Colores.NEGRO, c.lapiz.color)
    }

    @Test
    fun menuLapicesCambiaGrosor() {
        val c = controlador()
        assertEquals(ControladorConsola.Accion.MENU_LAPICES, c.procesar("l"))
        c.menuLapices(listOf("3").iterator(), StringBuilder())
        assertEquals(4, c.lapiz.grosor)
    }

    @Test
    fun menuLapicesConfiguraElDoble() {
        val c = controlador()
        c.procesar("l")
        // 9) con qué herramienta; 3) carbóncillo.
        c.menuLapices(listOf("9", "3").iterator(), StringBuilder())
        assertEquals(HerramientaDoble.CARBONCILLO, c.lapiz.herramientaDoble)
        // 10) de qué lado; 3) derecha.
        c.menuLapices(listOf("10", "3").iterator(), StringBuilder())
        assertEquals(LadoDoble.DERECHA, c.lapiz.ladoDoble)
        // 11) color de sombra; 3) rojo de la paleta.
        c.menuLapices(listOf("11", "3").iterator(), StringBuilder())
        assertEquals(Colores.ROJO, c.lapiz.colorDoble)
    }

    @Test
    fun menuLapicesEligeElDoble() {
        val c = controlador()
        c.procesar("l")
        c.menuLapices(listOf("8").iterator(), StringBuilder())
        assertEquals(Herramienta.DOBLE, c.lapiz.herramienta)
    }

    @Test
    fun alternarBorradorPintaDeBlanco() {
        val c = controlador()
        c.procesar("3")
        c.procesar("p") // punto rojo en (0,0)
        c.procesar("e") // borrador
        c.procesar("p") // borra el punto
        val m = c.motor as MotorKotlin
        assertEquals(Colores.BLANCO, m.pixelHoja(0, 0))
    }
}
