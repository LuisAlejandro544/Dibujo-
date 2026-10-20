package com.dibujo.core

/**
 * Herramientas del lápiz.
 *
 * El ordinal viaja por JNI hasta el enum `Tool` del C++ (mismo orden): al
 * agregar una herramienta nueva hay que añadirla al final en los dos lados.
 */
enum class Herramienta { LAPIZ, PORTAMINA, CARBONCILLO, BORRADOR, PINTAR, DOBLE, PLUMA }

/** Punta del lápiz. */
enum class Punta { REDONDA, CUADRADA }

/**
 * Lápiz seleccionado: color, grosor, punta, herramienta y textura.
 */
data class Lapiz(
    val color: Int = Colores.NEGRO,
    val grosor: Int = 2,
    val punta: Punta = Punta.REDONDA,
    val herramienta: Herramienta = Herramienta.LAPIZ,
    val textura: Textura = Textura.LISA,
) {
    init {
        require(grosor in 1..64) { "grosor fuera de rango: $grosor" }
    }

    /**
     * Grosor real del trazo según la herramienta: PORTAMINA es fino,
     * CARBONCILLO y BORRADOR son más gruesos. PINTAR no usa grosor (rellena
     * una región) y DOBLE usa el grosor para cada una de sus dos líneas. El
     * motor C++ replica esta fórmula (ver `grosorHerramienta` en
     * `DibujoEngine.cpp`).
     */
    fun grosorEfectivo(): Int = when (herramienta) {
        Herramienta.PORTAMINA -> maxOf(1, grosor / 2)
        Herramienta.CARBONCILLO -> grosor + 2
        Herramienta.BORRADOR -> grosor + 4
        Herramienta.DOBLE -> grosor
        else -> grosor
    }
}
