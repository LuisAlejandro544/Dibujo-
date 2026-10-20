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
 * Estilo de la segunda línea de la herramienta DOBLE. Se elige aparte del
 * estilo principal para poder combinar, por ejemplo, lápiz + pincel vector.
 * El ordinal viaja por JNI (mismo orden que `EstiloDoble` en C++).
 */
enum class EstiloDoble { LAPIZ, PORTAMINA, CARBONCILLO, PINCEL }

/**
 * Cómo se coloca la segunda línea de la herramienta DOBLE:
 *  - [CENTRADO]: una línea a cada lado del recorrido (comportamiento clásico).
 *  - [LADO_A]: el trazo principal sigue el dedo y la copia va a un lado.
 *  - [LADO_B]: igual que [LADO_A], pero hacia el otro lado de la normal.
 *  - [ESPEJO]: la copia se refleja al otro lado del primer punto tocado;
 *    dibujás hacia la derecha y la copia aparece cerca, hacia la izquierda.
 * El ordinal viaja por JNI (mismo orden que `ModoDoble` en C++).
 */
enum class ModoDoble { CENTRADO, LADO_A, LADO_B, ESPEJO }

/**
 * Lápiz seleccionado: color, grosor, punta, herramienta y textura.
 *
 * [colorSombra] es el color de la copia de la herramienta DOBLE; si es `null`
 * la copia usa el color principal. [estiloDoble] y [modoDoble] configuran esa
 * copia sin cambiar la herramienta principal.
 */
data class Lapiz(
    val color: Int = Colores.NEGRO,
    val grosor: Int = 2,
    val punta: Punta = Punta.REDONDA,
    val herramienta: Herramienta = Herramienta.LAPIZ,
    val textura: Textura = Textura.LISA,
    val estiloDoble: EstiloDoble = EstiloDoble.LAPIZ,
    val modoDoble: ModoDoble = ModoDoble.CENTRADO,
    val colorSombra: Int? = null,
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

    /**
     * Grosor de la segunda línea de DOBLE según [estiloDoble]: porta fina,
     * carbón grueso y pincel vector algo más ancho y siempre redondo. La misma
     * fórmula vive en C++ (`grosorEstiloDoble`) para mantener la paridad.
     */
    fun grosorCopiaDoble(): Int = when (estiloDoble) {
        EstiloDoble.LAPIZ -> grosor
        EstiloDoble.PORTAMINA -> maxOf(1, grosor / 2)
        EstiloDoble.CARBONCILLO -> grosor + 2
        EstiloDoble.PINCEL -> maxOf(1, (grosor * 3) / 2)
    }

    /** Punta de la copia: el pincel vector siempre es redondo. */
    fun puntaCopiaDoble(): Punta =
        if (estiloDoble == EstiloDoble.PINCEL) Punta.REDONDA else punta

    /** Color de la copia (sombra elegida o color principal si no hay). */
    fun colorCopiaDoble(): Int = colorSombra ?: color
}
