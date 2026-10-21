package com.dibujo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de la instantánea comprimida del historial.
 *
 * El historial guardaba una copia completa de la hoja por paso (megabytes en
 * una hoja grande). Ahora la instantánea se guarda con RLE: tiene que
 * devolver **exactamente** la misma hoja (sin pérdida) y ocupar mucho menos.
 */
class InstantaneaHojaTest {

    @Test
    fun restauraLaHojaExactamente() {
        val pixeles = IntArray(100) { if (it % 3 == 0) Colores.ROJO else Colores.BLANCO }
        val instantanea = InstantaneaHoja.capturar(pixeles)
        val destino = IntArray(100) { Colores.AZUL }
        instantanea.restaurar(destino)
        assertEquals(pixeles.toList(), destino.toList())
    }

    @Test
    fun unaHojaConMuchoFondoOcupaMuchoMenos() {
        // 1000×1000 en su mayoría blanco: el RLE lo deja en unas decenas de KB.
        val pixeles = IntArray(1000 * 1000) { Colores.BLANCO }
        for (x in 100..900) pixeles[500 * 1000 + x] = Colores.NEGRO
        val instantanea = InstantaneaHoja.capturar(pixeles)
        assertEquals(1000 * 1000, instantanea.pixeles)
        assertTrue(
            instantanea.bytes < 1000 * 1000 * 4 / 10,
            "la instantánea comprimida ocupa ${instantanea.bytes} bytes (esperado < 10% del crudo)",
        )
        val destino = IntArray(pixeles.size)
        instantanea.restaurar(destino)
        assertEquals(pixeles.toList(), destino.toList(), "la restauración es exacta")
    }

    @Test
    fun unaImagenRuidosaNoCrece() {
        // Cada píxel distinto: el RLE no conviene y se guarda la copia cruda.
        val pixeles = IntArray(64) { 0xFF000000.toInt() or (it * 2654435761u.toInt()) }
        val instantanea = InstantaneaHoja.capturar(pixeles)
        assertTrue(
            instantanea.bytes <= pixeles.size * 4,
            "la instantánea no ocupa más que la copia cruda (${instantanea.bytes})",
        )
        val destino = IntArray(pixeles.size)
        instantanea.restaurar(destino)
        assertEquals(pixeles.toList(), destino.toList())
    }

    @Test
    fun elHistorialDeshaceYRehaceIgual() {
        val lienzo = Lienzo(20, 10)
        val historial = Historial()
        historial.guardar(lienzo)
        lienzo.pintar(5, 5, Colores.ROJO)
        historial.guardar(lienzo)
        lienzo.pintar(6, 5, Colores.AZUL)

        assertTrue(historial.puedeDeshacer())
        assertTrue(historial.deshacer(lienzo))
        assertEquals(Colores.ROJO, lienzo.pixel(5, 5))
        assertEquals(Colores.BLANCO, lienzo.pixel(6, 5))
        assertTrue(historial.puedeRehacer())
        assertTrue(historial.rehacer(lienzo))
        assertEquals(Colores.ROJO, lienzo.pixel(5, 5))
        assertEquals(Colores.AZUL, lienzo.pixel(6, 5))
    }
}
