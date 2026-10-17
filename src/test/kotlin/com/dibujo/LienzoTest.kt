package com.dibujo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LienzoTest {
    @Test
    fun pintarDentroDeLimites() {
        val l = Lienzo(10, 10)
        l.pintar(3, 4, 'R')
        assertEquals('R', l.celdas[4][3])
    }

    @Test
    fun pintarFueraDeLimitesNoFalla() {
        val l = Lienzo(5, 5)
        l.pintar(-1, 0, 'R')
        l.pintar(0, 99, 'R')
        assertEquals('.', l.celdas[0][0])
    }

    @Test
    fun lineaHorizontal() {
        val l = Lienzo(10, 5)
        l.linea(0, 0, 4, 0, 'B')
        assertEquals("BBBBB.....", l.celdas[0].joinToString(""))
    }

    @Test
    fun rectanguloRelleno() {
        val l = Lienzo(6, 6)
        l.rectangulo(1, 1, 3, 3, 'G', relleno = true)
        assertEquals('G', l.celdas[2][2])
        assertEquals('G', l.celdas[1][1])
    }

    @Test
    fun floodFillRellenaRegion() {
        val l = Lienzo(3, 3)
        l.linea(0, 1, 2, 1, 'B')
        l.rellenar(0, 0, 'Y')
        assertEquals("""YYY
BBB
...""".replace("\n", ""), l.celdas.joinToString("") { it.joinToString("") })
    }

    @Test
    fun floodFillNoCruzaBorde() {
        val l = Lienzo(3, 3)
        l.linea(0, 1, 2, 1, 'B')
        l.rellenar(0, 2, 'Y')
        assertEquals('Y', l.celdas[2][0])
        assertEquals('B', l.celdas[1][0])
        assertEquals('Y', l.celdas[2][2])
    }

    @Test
    fun deshacerRestaura() {
        val l = Lienzo(5, 5)
        val ed = Editor(l)
        ed.guardarEstado()
        l.pintar(0, 0, 'R')
        assertTrue(ed.deshacer())
        assertEquals('.', l.celdas[0][0])
    }
}
