package com.dibujo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de los filtros de la imagen de calco: son operaciones de imagen puras
 * (no tocan el motor), así que viven en `:core` y se prueban acá.
 */
class FiltrosImagenTest {

    private fun rgb(rojo: Int, verde: Int, azul: Int, alfa: Int = 255): Int =
        (alfa shl 24) or (rojo shl 16) or (verde shl 8) or azul

    @Test
    fun sinFiltroDevuelveUnaCopiaIgual() {
        val origen = intArrayOf(rgb(10, 20, 30), rgb(200, 100, 50))
        val salida = FiltrosImagen.aplicar(origen, 2, 1, FiltroImagen.NINGUNO, 0)
        assertEquals(origen.toList(), salida.toList())
        salida[0] = 0
        assertEquals(rgb(10, 20, 30), origen[0], "el original no se toca")
    }

    @Test
    fun escalaDeGrisesConservaElAlfa() {
        val origen = intArrayOf(rgb(255, 0, 0, 128))
        val salida = FiltrosImagen.aplicar(origen, 1, 1, FiltroImagen.GRIS, 0)
        val y = FiltrosImagen.luminancia(rgb(255, 0, 0))
        assertEquals(128, (salida[0] ushr 24) and 0xFF, "el alfa se conserva")
        assertEquals(y, (salida[0] ushr 16) and 0xFF, "el rojo queda gris")
        assertEquals(y, salida[0] and 0xFF, "el azul queda gris")
    }

    @Test
    fun contrasteAumentaLaSeparacionConElGris() {
        val oscuro = rgb(100, 100, 100)
        val claro = rgb(200, 200, 200)
        val origen = intArrayOf(oscuro, claro)
        val salida = FiltrosImagen.aplicar(origen, 2, 1, FiltroImagen.CONTRASTE, 100)
        val oscuroNuevo = (salida[0] ushr 16) and 0xFF
        val claroNuevo = (salida[1] ushr 16) and 0xFF
        assertTrue(oscuroNuevo < 100, "el tono oscuro se oscurece (valor=$oscuroNuevo)")
        assertTrue(claroNuevo > 200, "el tono claro se aclara (valor=$claroNuevo)")
    }

    @Test
    fun contrasteNegativoAcercaLosTonosAlGris() {
        val origen = intArrayOf(rgb(20, 20, 20), rgb(240, 240, 240))
        val salida = FiltrosImagen.aplicar(origen, 2, 1, FiltroImagen.CONTRASTE, -100)
        assertEquals(128, (salida[0] ushr 16) and 0xFF)
        assertEquals(128, (salida[1] ushr 16) and 0xFF)
    }

    @Test
    fun umbralDejaBlancoYNegro() {
        val origen = intArrayOf(rgb(0, 0, 0), rgb(255, 255, 255), rgb(90, 90, 90))
        val salida = FiltrosImagen.aplicar(origen, 3, 1, FiltroImagen.UMBRAL, 128)
        assertEquals(0xFF000000.toInt(), salida[0], "lo oscuro queda negro")
        assertEquals(0xFFFFFFFF.toInt(), salida[1], "lo claro queda blanco")
        assertEquals(0xFF000000.toInt(), salida[2], "justo bajo el umbral queda negro")
    }

    @Test
    fun invertirCambiaCadaCanal() {
        val origen = intArrayOf(rgb(0, 10, 255))
        val salida = FiltrosImagen.aplicar(origen, 1, 1, FiltroImagen.INVERTIR, 0)
        assertEquals(255, (salida[0] ushr 16) and 0xFF)
        assertEquals(245, (salida[0] ushr 8) and 0xFF)
        assertEquals(0, salida[0] and 0xFF)
    }

    @Test
    fun bordesMarcaElContornoDeUnCuadrado() {
        // Cuadrado negro 4x4 sobre fondo blanco: el borde tiene gradiente.
        val ancho = 6
        val alto = 6
        val pixeles = IntArray(ancho * alto) { rgb(255, 255, 255) }
        for (y in 1..4) {
            for (x in 1..4) pixeles[y * ancho + x] = rgb(0, 0, 0)
        }
        val salida = FiltrosImagen.aplicar(pixeles, ancho, alto, FiltroImagen.BORDES, 8)
        // En el interior del cuadrado no hay borde: queda claro.
        assertTrue(
            (salida[3 * ancho + 3] and 0xFF) > 200,
            "el interior no tiene borde (valor=${salida[3 * ancho + 3] and 0xFF})",
        )
        // En el borde del cuadrado hay gradiente: queda oscuro.
        assertTrue(
            (salida[1 * ancho + 1] and 0xFF) < 200,
            "el borde queda marcado (valor=${salida[1 * ancho + 1] and 0xFF})",
        )
    }

    @Test
    fun sinFondoDejaTransparenteLoClaro() {
        val origen = intArrayOf(rgb(255, 255, 255), rgb(20, 20, 20))
        val salida = FiltrosImagen.aplicar(origen, 2, 1, FiltroImagen.SIN_FONDO, 235)
        assertEquals(0, (salida[0] ushr 24) and 0xFF, "el fondo queda transparente")
        val alfaTinta = (salida[1] ushr 24) and 0xFF
        assertTrue(alfaTinta > 200, "la tinta queda casi opaca (alfa=$alfaTinta)")
        assertEquals(20, salida[1] and 0xFF, "la tinta conserva su color")
    }

    @Test
    fun losRangosYTamanosPorDefectoSonCoherentes() {
        for (filtro in FiltroImagen.entries) {
            val rango = FiltrosImagen.rango(filtro)
            val defecto = FiltrosImagen.valorPorDefecto(filtro)
            assertTrue(
                defecto in rango,
                "${filtro.name}: el valor por defecto $defecto está fuera de $rango",
            )
        }
    }

    @Test
    fun unBufferInvalidoDevuelveVacio() {
        assertEquals(0, FiltrosImagen.aplicar(IntArray(4), 0, 4, FiltroImagen.GRIS, 0).size)
        assertEquals(0, FiltrosImagen.aplicar(IntArray(4), 4, 4, FiltroImagen.GRIS, 0).size)
    }
}
