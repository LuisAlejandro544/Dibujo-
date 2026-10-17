package com.dibujo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LienzoTest {
    @Test
    fun pintarDentroDeLimites() {
        val l = Lienzo(10, 10)
        l.pintar(3, 4, Colores.ROJO)
        assertEquals(Colores.ROJO, l.pixel(3, 4))
    }

    @Test
    fun pintarFueraNoFalla() {
        val l = Lienzo(5, 5)
        l.pintar(-1, 0, Colores.ROJO)
        l.pintar(0, 99, Colores.ROJO)
        assertEquals(Colores.BLANCO, l.pixel(0, 0))
    }

    @Test
    fun discoRadioCeroPintaUnPixel() {
        val l = Lienzo(5, 5)
        l.disco(2f, 2f, 0.5f, Colores.AZUL)
        assertEquals(Colores.AZUL, l.pixel(2, 2))
        assertEquals(Colores.BLANCO, l.pixel(0, 0))
    }

    @Test
    fun lineaGruesaHorizontal() {
        val l = Lienzo(10, 5)
        l.lineaGruesa(0f, 0f, 4f, 0f, 1, redonda = true, color = Colores.VERDE)
        for (x in 0 until 5) assertEquals(Colores.VERDE, l.pixel(x, 0))
        assertEquals(Colores.BLANCO, l.pixel(5, 0))
    }

    @Test
    fun rellenarInundaRegion() {
        val l = Lienzo(3, 3)
        l.lineaGruesa(0f, 1f, 2f, 1f, 1, redonda = true, color = Colores.NEGRO)
        l.rellenar(0, 0, Colores.AMARILLO)
        assertTrue((0 until 3).all { l.pixel(it, 0) == Colores.AMARILLO })
        assertTrue((0 until 3).all { l.pixel(it, 1) == Colores.NEGRO })
    }

    @Test
    fun rellenarNoCruzaBorde() {
        val l = Lienzo(3, 3)
        l.lineaGruesa(0f, 1f, 2f, 1f, 1, redonda = true, color = Colores.NEGRO)
        l.rellenar(0, 2, Colores.AMARILLO)
        assertEquals(Colores.AMARILLO, l.pixel(0, 2))
        assertEquals(Colores.NEGRO, l.pixel(0, 1))
    }

    @Test
    fun clonarCopiaPixeles() {
        val l = Lienzo(4, 4)
        l.pintar(1, 1, Colores.ROJO)
        val copia = l.clonar()
        assertEquals(Colores.ROJO, copia.pixel(1, 1))
        copia.pintar(1, 1, Colores.AZUL)
        assertEquals(Colores.ROJO, l.pixel(1, 1))
    }

    @Test
    fun discoBordeSuavizadoNoEsEscalonado() {
        // Un disco de radio 1 sobre blanco deja el centro opaco y el borde
        // con cobertura parcial (valor intermedio entre negro y blanco).
        val l = Lienzo(9, 9)
        l.disco(4f, 4f, 1f, Colores.NEGRO)
        assertEquals(Colores.NEGRO, l.pixel(4, 4))
        val borde = l.pixel(5, 4)
        assertTrue(borde != Colores.NEGRO && borde != Colores.BLANCO, "borde=$borde")
    }

    @Test
    fun pintarMezclaCoberturaTotalReemplaza() {
        val l = Lienzo(3, 3)
        l.pintarMezcla(1, 1, Colores.ROJO, 255)
        assertEquals(Colores.ROJO, l.pixel(1, 1))
    }

    @Test
    fun pintarMezclaCoberturaCeroNoPinta() {
        val l = Lienzo(3, 3)
        l.pintarMezcla(1, 1, Colores.ROJO, 0)
        assertEquals(Colores.BLANCO, l.pixel(1, 1))
    }

    @Test
    fun expandirConservaElContenidoYAgregaFondo() {
        val l = Lienzo(4, 3)
        l.pintar(0, 0, Colores.ROJO)
        l.pintar(3, 2, Colores.AZUL)
        l.expandir(0, 0, 2, 1)
        assertEquals(6, l.ancho)
        assertEquals(4, l.alto)
        // El contenido conserva sus coordenadas.
        assertEquals(Colores.ROJO, l.pixel(0, 0))
        assertEquals(Colores.AZUL, l.pixel(3, 2))
        // La franja nueva es fondo.
        assertEquals(Colores.BLANCO, l.pixel(5, 3))
        assertEquals(Colores.BLANCO, l.pixel(0, 3))
    }

    @Test
    fun expandirPorIzquierdaYArribaDesplazaElContenido() {
        val l = Lienzo(3, 3)
        l.pintar(0, 0, Colores.ROJO)
        l.expandir(2, 1, 0, 0)
        assertEquals(5, l.ancho)
        assertEquals(4, l.alto)
        // El dibujo se desplaza con la hoja para no moverse de lugar.
        assertEquals(Colores.ROJO, l.pixel(2, 1))
        assertEquals(Colores.BLANCO, l.pixel(0, 0))
    }
}

class LimpiarTrazosTest {
    @Test
    fun hojaVaciaNoCambia() {
        val l = Lienzo(8, 8)
        assertTrue(!l.limpiarTrazos(), "una hoja vacía no tiene nada que limpiar")
    }

    @Test
    fun adelgazaTrazoRepetido() {
        val l = Lienzo(24, 24)
        // El mismo trazo repasado tres veces: la línea queda gruesa e irregular.
        repeat(3) {
            l.lineaGruesa(3f, 12f, 21f, 12f, 7, redonda = true, color = Colores.NEGRO)
        }
        val grueso = contarTinta(l)
        val altoAntes = (0 until 24).count { l.pixel(12, it) != Colores.BLANCO }
        assertEquals(7, altoAntes, "el trazo grueso ocupa ~7 px de alto")
        assertTrue(l.limpiarTrazos(), "debe limpiar")
        val fino = contarTinta(l)
        assertTrue(fino < grueso * 7 / 10, "grueso=$grueso fino=$fino")
        // La línea sigue existiendo por el centro, ya fina (1 px + antialias).
        assertTrue(l.pixel(12, 12) != Colores.BLANCO, "el centro conserva la tinta")
        val altoDespues = (0 until 24).count { l.pixel(12, it) != Colores.BLANCO }
        assertTrue(altoDespues <= 4, "ya no es gruesa: alto=$altoDespues")
    }

    private fun contarTinta(l: Lienzo): Int {
        var n = 0
        for (y in 0 until l.alto) {
            for (x in 0 until l.ancho) if (l.pixel(x, y) != Colores.BLANCO) n++
        }
        return n
    }
}

class CuantizadorTest {
    @Test
    fun extraeColoresDeGrupos() {
        val muestras = IntArray(60) { i ->
            when {
                i < 20 -> Colores.ROJO
                i < 40 -> Colores.VERDE
                else -> Colores.AZUL
            }
        }
        val p = Cuantizador.paleta(muestras, 3)
        assertEquals(3, p.size)
        assertTrue(cerca(p, Colores.ROJO), "rojo en $p")
        assertTrue(cerca(p, Colores.VERDE), "verde en $p")
        assertTrue(cerca(p, Colores.AZUL), "azul en $p")
    }

    @Test
    fun sinMuestrasDevuelveVacio() {
        assertEquals(emptyList(), Cuantizador.paleta(IntArray(0), 3))
        assertEquals(emptyList(), Cuantizador.paleta(IntArray(5) { 0x00FFFFFF }, 3))
    }

    @Test
    fun esDeterminista() {
        val muestras = IntArray(50) { it * 0x010305 }
        assertEquals(Cuantizador.paleta(muestras, 4), Cuantizador.paleta(muestras, 4))
    }

    private fun cerca(paleta: List<Int>, color: Int): Boolean = paleta.any { c ->
        val dr = ((c ushr 16) and 0xFF) - ((color ushr 16) and 0xFF)
        val dg = ((c ushr 8) and 0xFF) - ((color ushr 8) and 0xFF)
        val db = (c and 0xFF) - (color and 0xFF)
        dr * dr + dg * dg + db * db <= 9
    }
}

class BackendRenderTest {
    @Test
    fun automaticoUsaVulkanSiHaySoporte() {
        assertEquals(BackendRender.VULKAN, BackendRender.AUTOMATICO.resolver(vulkanSoportado = true))
        assertEquals(BackendRender.OPENGL, BackendRender.AUTOMATICO.resolver(vulkanSoportado = false))
    }

    @Test
    fun vulkanPideFallbackAOpenGLSinSoporte() {
        assertEquals(BackendRender.OPENGL, BackendRender.VULKAN.resolver(vulkanSoportado = false))
        assertEquals(BackendRender.VULKAN, BackendRender.VULKAN.resolver(vulkanSoportado = true))
    }

    @Test
    fun openglSiempreDisponible() {
        assertEquals(BackendRender.OPENGL, BackendRender.OPENGL.resolver(vulkanSoportado = false))
        assertEquals(BackendRender.OPENGL, BackendRender.OPENGL.resolver(vulkanSoportado = true))
    }

    @Test
    fun vulkanSoloDisponibleConSoporte() {
        assertTrue(BackendRender.OPENGL.disponible(vulkanSoportado = false))
        assertTrue(BackendRender.AUTOMATICO.disponible(vulkanSoportado = false))
        assertTrue(!BackendRender.VULKAN.disponible(vulkanSoportado = false))
        assertTrue(BackendRender.VULKAN.disponible(vulkanSoportado = true))
    }
}

class HistorialTest {
    @Test
    fun deshacerRestaura() {
        val l = Lienzo(5, 5)
        val h = Historial()
        h.guardar(l)
        l.pintar(0, 0, Colores.ROJO)
        assertTrue(h.deshacer(l))
        assertEquals(Colores.BLANCO, l.pixel(0, 0))
    }

    @Test
    fun rehacerRestauraCambio() {
        val l = Lienzo(5, 5)
        val h = Historial()
        h.guardar(l)
        l.pintar(2, 2, Colores.ROJO)
        h.deshacer(l)
        assertTrue(h.rehacer(l))
        assertEquals(Colores.ROJO, l.pixel(2, 2))
    }

    @Test
    fun rehacerSeInvalidaConCambioNuevo() {
        val l = Lienzo(5, 5)
        val h = Historial()
        h.guardar(l)
        l.pintar(0, 0, Colores.ROJO)
        h.deshacer(l)
        h.guardar(l)
        l.pintar(1, 1, Colores.AZUL)
        assertTrue(!h.puedeRehacer())
    }
}

class LapizTest {
    @Test
    fun grosorEfectivoPorHerramienta() {
        assertEquals(2, Lapiz(herramienta = Herramienta.LAPIZ).grosorEfectivo())
        assertEquals(1, Lapiz(herramienta = Herramienta.PORTAMINA, grosor = 3).grosorEfectivo())
        assertEquals(1, Lapiz(herramienta = Herramienta.PORTAMINA, grosor = 1).grosorEfectivo())
        assertEquals(6, Lapiz(herramienta = Herramienta.CARBONCILLO, grosor = 4).grosorEfectivo())
        assertEquals(6, Lapiz(herramienta = Herramienta.BORRADOR, grosor = 2).grosorEfectivo())
        // PINTAR no ajusta el grosor (rellena, no traza).
        assertEquals(5, Lapiz(herramienta = Herramienta.PINTAR, grosor = 5).grosorEfectivo())
        // DOBLE usa el grosor para cada una de sus dos líneas.
        assertEquals(5, Lapiz(herramienta = Herramienta.DOBLE, grosor = 5).grosorEfectivo())
    }
}

class ComandoLineaTest {    @Test
    fun simple() {
        val c = ComandoLinea("m")
        assertEquals("m", c.letra)
        assertEquals(null, c.numero)
    }

    @Test
    fun conteoPegado() {
        val c = ComandoLinea("3d")
        assertEquals("d", c.letra)
        assertEquals(3, c.numero)
    }

    @Test
    fun conteoSeparado() {
        val c = ComandoLinea("d 5")
        assertEquals("d", c.letra)
        assertEquals(5, c.numero)
    }

    @Test
    fun vacia() {
        val c = ComandoLinea("   ")
        assertTrue(c.esVacia)
    }
}
