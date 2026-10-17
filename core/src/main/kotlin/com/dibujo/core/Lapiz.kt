package com.dibujo.core

/** Herramientas del lápiz. */
enum class Herramienta { LAPIZ, PORTAMINA, CARBONCILLO, BORRADOR }

/** Punta del lápiz. */
enum class Punta { REDONDA, CUADRADA }

/**
 * Lápiz seleccionado: color, grosor, punta y herramienta.
 */
data class Lapiz(
    val color: Int = Colores.NEGRO,
    val grosor: Int = 2,
    val punta: Punta = Punta.REDONDA,
    val herramienta: Herramienta = Herramienta.LAPIZ,
) {
    init {
        require(grosor in 1..64) { "grosor fuera de rango: $grosor" }
    }

    /**
     * Grosor real del trazo según la herramienta: PORTAMINA es fino,
     * CARBONCILLO y BORRADOR son más gruesos. El motor C++ replica
     * esta fórmula (ver `grosorHerramienta` en `DibujoEngine.cpp`).
     */
    fun grosorEfectivo(): Int = when (herramienta) {
        Herramienta.PORTAMINA -> maxOf(1, grosor / 2)
        Herramienta.CARBONCILLO -> grosor + 2
        Herramienta.BORRADOR -> grosor + 4
        else -> grosor
    }
}
