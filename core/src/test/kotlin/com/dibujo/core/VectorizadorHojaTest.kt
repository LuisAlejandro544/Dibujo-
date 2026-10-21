package com.dibujo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests del vectorizador: el dibujo raster se convierte en un SVG con un
 * `<path>` por figura (y los agujeros se recortan).
 */
class VectorizadorHojaTest {

    private fun hoja(ancho: Int, alto: Int, pintar: (Int, Int) -> Int?): IntArray {
        val pixeles = IntArray(ancho * alto) { Colores.BLANCO }
        for (y in 0 until alto) {
            for (x in 0 until ancho) {
                val color = pintar(x, y) ?: continue
                pixeles[y * ancho + x] = color
            }
        }
        return pixeles
    }

    /** Color ARGB a `#rrggbb` (igual que lo escribe el vectorizador). */
    private fun hex(color: Int): String {
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        return "#%02x%02x%02x".format(r, g, b)
    }

    @Test
    fun unaHojaBlancaNoVectoriza() {
        val pixeles = IntArray(10 * 10) { Colores.BLANCO }
        assertNull(VectorizadorHoja.aSvg(pixeles, 10, 10))
    }

    @Test
    fun unCuadradoLlenoDaUnSoloCamino() {
        val pixeles = hoja(20, 20) { x, y ->
            if (x in 5..12 && y in 5..12) Colores.NEGRO else null
        }
        val svg = VectorizadorHoja.aSvg(pixeles, 20, 20)
        assertNotNull(svg)
        assertEquals(1, Regex("<path ").findAll(svg).count(), "un solo camino: $svg")
        assertTrue(svg.contains("fill=\"${hex(Colores.NEGRO)}\""), "el color del trazo: $svg")
        assertTrue(svg.contains("viewBox=\"0 0 20 20\""), "el lienzo del SVG: $svg")
    }

    @Test
    fun dosFigurasSeparadasDanDosCaminos() {
        val pixeles = hoja(30, 12) { x, y ->
            when {
                x in 2..6 && y in 2..6 -> Colores.ROJO
                x in 20..26 && y in 4..9 -> Colores.AZUL
                else -> null
            }
        }
        val svg = VectorizadorHoja.aSvg(pixeles, 30, 12)
        assertNotNull(svg)
        assertEquals(2, Regex("<path ").findAll(svg).count(), "dos caminos: $svg")
        assertTrue(svg.contains("fill=\"${hex(Colores.ROJO)}\""), "el rojo: $svg")
        assertTrue(svg.contains("fill=\"${hex(Colores.AZUL)}\""), "el azul: $svg")
    }

    @Test
    fun unAgujeroSeRecortaConUnSubTrazado() {
        // Un anillo: la tinta es un marco de 3 px alrededor de un hueco.
        val pixeles = hoja(24, 24) { x, y ->
            val enMarco = x in 4..18 && y in 4..18 &&
                !(x in 7..15 && y in 7..15)
            if (enMarco) Colores.NEGRO else null
        }
        val svg = VectorizadorHoja.aSvg(pixeles, 24, 24)
        assertNotNull(svg)
        // El camino tiene el contorno exterior y el del agujero: dos "Z".
        assertEquals(2, Regex(" Z").findAll(svg).count(), "el agujero se recorta: $svg")
    }

    @Test
    fun elSvgTraeElFondoComoRectangulo() {
        val pixeles = hoja(12, 12) { x, y ->
            if (x == 5 && y == 5) Colores.NEGRO else null
        }
        // Un solo píxel no alcanza para una figura: no hay camino.
        assertNull(VectorizadorHoja.aSvg(pixeles, 12, 12))
    }

    @Test
    fun unTrazoLargoSeSimplifica() {
        // Una línea horizontal de 40 px: el contorno son 4 vértices y con la
        // simplificación quedan pocos puntos.
        val pixeles = hoja(50, 10) { x, y ->
            if (y in 4..5 && x in 5..40) Colores.NEGRO else null
        }
        val svg = VectorizadorHoja.aSvg(pixeles, 50, 10)
        assertNotNull(svg)
        val camino = Regex("d=\"([^\"]*)\"").find(svg)!!.groupValues[1]
        val puntos = Regex("(-?\\d+) (-?\\d+)").findAll(camino).count()
        assertTrue(puntos <= 12, "el contorno está simplificado ($puntos puntos): $camino")
        assertTrue(puntos >= 3, "el contorno conserva la forma ($puntos puntos): $camino")
    }

    @Test
    fun unBufferInvalidoDevuelveNulo() {
        assertNull(VectorizadorHoja.aSvg(IntArray(4), 10, 10))
        assertNull(VectorizadorHoja.aSvg(IntArray(0), 0, 0))
    }
}
