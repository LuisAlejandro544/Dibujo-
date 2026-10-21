package com.dibujo.ui

import com.dibujo.core.Colores
import com.dibujo.core.Herramienta
import com.dibujo.core.HerramientaDoble
import com.dibujo.core.LadoDoble
import com.dibujo.core.Lapiz
import com.dibujo.core.Punta

/**
 * Menús cómodos: listas numeradas para elegir lápiz y color.
 * Se navega con la misma entrada de consola de la app.
 */
object Menu {

    /** Menú de colores. Devuelve el color elegido o null si cancela. */
    fun colores(entrada: Iterator<String>, salida: Appendable, actual: Int): Int? {
        salida.append("— Elige un color —\n")
        Colores.PALETA.forEachIndexed { i, c ->
            val marca = if (c.argb == actual) " (actual)" else ""
            salida.append("  ${i + 1}) ${c.nombre} ${c.hex()}$marca\n")
        }
        salida.append("  0) cancelar\n> ")
        val linea = siguienteLinea(entrada) ?: return null
        val n = linea.trim().toIntOrNull() ?: return null.also { salida.append("opción inválida\n") }
        if (n == 0) return null
        if (n !in 1..Colores.PALETA.size) {
            salida.append("opción fuera de rango\n")
            return null
        }
        val elegido = Colores.porIndice(n - 1)
        salida.append("color: ${elegido.nombre}\n")
        return elegido.argb
    }

    /** Menú de lápices. Devuelve un Lapiz nuevo o null si cancela. */
    fun lapices(entrada: Iterator<String>, salida: Appendable, lapiz: Lapiz): Lapiz? {
        salida.append("— Elige un lápiz —\n")
        salida.append("  1) portamina   (trazo fino)\n")
        salida.append("  2) lápiz       (normal)\n")
        salida.append("  3) lápiz grueso\n")
        salida.append("  4) carboncillo (suave, grueso)\n")
        salida.append("  5) punta ").append(if (lapiz.punta == Punta.REDONDA) "cuadrada" else "redonda").append('\n')
        salida.append("  6) ").append(if (lapiz.herramienta == Herramienta.LAPIZ) "borrador" else "lápiz").append('\n')
        salida.append("  7) pintar (balde: rellena la región)\n")
        salida.append("  8) doble lápiz (trazo principal + trazo doble)\n")
        salida.append("  9) doble: con qué herramienta\n")
        salida.append(" 10) doble: de qué lado\n")
        salida.append(" 11) doble: color de sombra\n")
        salida.append("  0) cancelar\n> ")
        val linea = siguienteLinea(entrada) ?: return null
        val n = linea.trim().toIntOrNull() ?: run {
            salida.append("opción inválida\n")
            return null
        }
        return when (n) {
            0 -> null
            1 -> lapiz.copy(herramienta = Herramienta.PORTAMINA, grosor = 1)
            2 -> lapiz.copy(herramienta = Herramienta.LAPIZ, grosor = 2)
            3 -> lapiz.copy(herramienta = Herramienta.LAPIZ, grosor = 4)
            4 -> lapiz.copy(herramienta = Herramienta.CARBONCILLO, grosor = 4)
            5 -> lapiz.copy(
                punta = if (lapiz.punta == Punta.REDONDA) Punta.CUADRADA else Punta.REDONDA,
            )
            6 -> lapiz.copy(
                herramienta = if (lapiz.herramienta == Herramienta.LAPIZ) Herramienta.BORRADOR else Herramienta.LAPIZ,
            )
            7 -> lapiz.copy(herramienta = Herramienta.PINTAR)
            8 -> lapiz.copy(herramienta = Herramienta.DOBLE)
            9 -> dobleHerramienta(entrada, salida, lapiz)
            10 -> dobleLado(entrada, salida, lapiz)
            11 -> dobleColor(entrada, salida, lapiz)
            else -> {
                salida.append("opción fuera de rango\n")
                null
            }
        }?.also { salida.append("lápiz actualizado\n") }
    }

    /** Herramienta del trazo acompañante ("la doble"). */
    private fun dobleHerramienta(
        entrada: Iterator<String>,
        salida: Appendable,
        lapiz: Lapiz,
    ): Lapiz? {
        salida.append("— Doble: con qué herramienta —\n")
        salida.append("  1) lápiz\n")
        salida.append("  2) portamina\n")
        salida.append("  3) carbóncillo\n")
        salida.append("  4) pincel vectorial\n")
        salida.append("  0) cancelar\n> ")
        val n = siguienteLinea(entrada)?.trim()?.toIntOrNull() ?: return null
        return when (n) {
            1 -> lapiz.copy(herramientaDoble = HerramientaDoble.LAPIZ)
            2 -> lapiz.copy(herramientaDoble = HerramientaDoble.PORTAMINA)
            3 -> lapiz.copy(herramientaDoble = HerramientaDoble.CARBONCILLO)
            4 -> lapiz.copy(herramientaDoble = HerramientaDoble.PLUMA)
            else -> null
        }
    }

    /** Lado donde se dibuja el trazo acompañante. */
    private fun dobleLado(
        entrada: Iterator<String>,
        salida: Appendable,
        lapiz: Lapiz,
    ): Lapiz? {
        salida.append("— Doble: de qué lado —\n")
        salida.append("  1) sobre el trazo\n")
        salida.append("  2) izquierda\n")
        salida.append("  3) derecha\n")
        salida.append("  0) cancelar\n> ")
        val n = siguienteLinea(entrada)?.trim()?.toIntOrNull() ?: return null
        return when (n) {
            1 -> lapiz.copy(ladoDoble = LadoDoble.MISMO)
            2 -> lapiz.copy(ladoDoble = LadoDoble.IZQUIERDA)
            3 -> lapiz.copy(ladoDoble = LadoDoble.DERECHA)
            else -> null
        }
    }

    /** Color de sombra del trazo acompañante. */
    private fun dobleColor(
        entrada: Iterator<String>,
        salida: Appendable,
        lapiz: Lapiz,
    ): Lapiz? {
        val color = colores(entrada, salida, lapiz.colorDoble) ?: return null
        return lapiz.copy(colorDoble = color)
    }

    private fun siguienteLinea(entrada: Iterator<String>): String? =
        if (entrada.hasNext()) entrada.next() else null
}
