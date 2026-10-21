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
 * Herramientas que **trazan** (no borran, no rellenan y no son vectoriales):
 * sirven como lápiz y también como trazo principal o acompañante del modo
 * DOBLE.
 */
val Herramienta.esTrazo: Boolean
    get() = this == Herramienta.LAPIZ ||
        this == Herramienta.PORTAMINA ||
        this == Herramienta.CARBONCILLO

/**
 * Lápiz seleccionado: color, grosor, punta, herramienta y textura.
 *
 * Cuando la herramienta es [Herramienta.DOBLE] el gesto dibuja **dos** trazos:
 * el principal (con [herramientaPrincipal]) y el acompañante o "doble" (con
 * [herramientaDoble], [colorDoble] y [ladoDoble]). Los campos de abajo sólo
 * tienen efecto en ese modo; el resto de las herramientas los ignora.
 */
data class Lapiz(
    val color: Int = Colores.NEGRO,
    val grosor: Int = 2,
    val punta: Punta = Punta.REDONDA,
    val herramienta: Herramienta = Herramienta.LAPIZ,
    val textura: Textura = Textura.LISA,
    /**
     * Herramienta del trazo **principal** cuando [herramienta] es
     * [Herramienta.DOBLE] (lápiz, portamina o carbóncillo).
     */
    val herramientaPrincipal: Herramienta = Herramienta.LAPIZ,
    /** Herramienta del trazo acompañante ("la doble"). */
    val herramientaDoble: HerramientaDoble = HerramientaDoble.LAPIZ,
    /** Lado donde se dibuja el trazo acompañante. */
    val ladoDoble: LadoDoble = LadoDoble.IZQUIERDA,
    /** Color del trazo acompañante (el "color de sombra"). */
    val colorDoble: Int = Colores.NEGRO,
) {
    init {
        require(grosor in GROSOR_MIN..GROSOR_MAX) { "grosor fuera de rango: $grosor" }
    }

    companion object {
        const val GROSOR_MIN = 1
        const val GROSOR_MAX = 64
    }

    /**
     * Grosor real del trazo según la herramienta: PORTAMINA es fino,
     * CARBONCILLO y BORRADOR son más gruesos. PINTAR no usa grosor (rellena
     * una región) y DOBLE usa el grosor de su trazo principal. El motor C++
     * replica esta fórmula (ver `grosorHerramienta` en `DibujoEngine.cpp`).
     */
    fun grosorEfectivo(): Int = when (herramienta) {
        // En modo DOBLE el grosor del gesto es el del trazo principal.
        Herramienta.DOBLE -> grosorEfectivo(herramientaPrincipal)
        else -> grosorEfectivo(herramienta)
    }

    /**
     * Grosor real del trazo de una herramienta concreta (sin mirar la
     * herramienta activa). Sirve para el trazo principal y para el
     * acompañante del modo DOBLE. El motor C++ replica esta fórmula.
     */
    fun grosorEfectivo(herramienta: Herramienta): Int = when (herramienta) {
        Herramienta.PORTAMINA -> maxOf(1, grosor / 2)
        Herramienta.CARBONCILLO -> grosor + 2
        Herramienta.BORRADOR -> grosor + 4
        Herramienta.DOBLE -> grosor
        else -> grosor
    }
}
