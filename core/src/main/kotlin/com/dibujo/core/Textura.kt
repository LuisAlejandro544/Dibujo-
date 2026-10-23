package com.dibujo.core

/**
 * Textura del trazo. Modula la cobertura de cada píxel con un patrón
 * determinista, así el lápiz, el portaminas y el carboncillo dejan un grano
 * distinto (papel, trama, salpicado) en lugar de una línea perfectamente lisa.
 *
 * El ordinal viaja por JNI hasta el enum `Textura` del C++ (mismo orden): al
 * agregar una textura hay que añadirla al final en los dos lados.
 */
enum class Textura {
    /** Trazo liso, sin grano (comportamiento original). */
    LISA,

    /** Grano fino de lápiz sobre papel. */
    GRANO,

    /** Grano más grueso, como carboncillo sobre papel rugoso. */
    RUGOSA,

    /** Trama diagonal tenue (rayado). */
    TRAMA,

    /** Salpicado con huecos pequeños, como tiza o grafito suelto. */
    SALPICADA,

    /** Grano de papel: variación suave, como lápiz sobre papel prensado. */
    PAPEL,

    /** Tela o lienzo: trama cruzada (urdimbre y trama). */
    TELA,

    /** Acuarela: manchas suaves y bordes difusos. */
    ACUARELA,

    /** Carbón: grano grueso y oscuro, como carboncillo. */
    CARBON,

    /** Manchas de tinta: gotas dispersas sobre el trazo. */
    MANCHA,

    /** Puntilleo: puntos separados, estilo stippling. */
    PUNTILLES,

    /** Rayas finas diagonales, como papel rayado. */
    RAYAS,
    ;

    /**
     * Factor de cobertura (0..255) del píxel (x, y) de la hoja para esta
     * textura. Es determinista y sólo usa enteros, así el C++ replica
     * exactamente el mismo valor (paridad píxel a píxel).
     */
    fun factor(x: Int, y: Int): Int = when (this) {
        LISA -> 255
        GRANO -> 145 + ruido(x, y) * 110 / 255
        RUGOSA -> 120 + ruido(x shr 2, y shr 2) * 135 / 255
        TRAMA -> if ((x + y) % 7 < 2) 255 else 165
        SALPICADA -> if (ruido(x, y) < 38) 70 else 255
        PAPEL -> 195 + ruido(x, y) * 60 / 255
        TELA -> if ((x % 4 == 0) || (y % 4 == 0)) 175 else 245
        ACUARELA -> 150 + ruido(x shr 3, y shr 3) * 105 / 255
        CARBON -> 95 + ruido(x shr 3, y shr 3) * 160 / 255
        MANCHA -> if (ruido(x, y) < 22) 55 else if (ruido(x, y) > 232) 195 else 255
        PUNTILLES -> if (ruido(x, y) < 30) 80 else 255
        RAYAS -> if ((x + y) % 6 < 2) 170 else 255
    }

    /**
     * Aplica la textura a una cobertura [alpha] (0..255) del píxel (x, y).
     * LISA devuelve el mismo valor (no altera el trazo original).
     */
    fun aplicar(alpha: Int, x: Int, y: Int): Int {
        if (this == LISA || alpha <= 0) return alpha
        return (alpha * factor(x, y) + 127) / 255
    }

    companion object {
        /** Ruido entero determinista por coordenada (idéntico en C++). */
        fun ruido(x: Int, y: Int): Int {
            var h = (x.toLong() * 374761393L + y.toLong() * 668265263L) and 0xFFFFFFFFL
            h = ((h xor (h ushr 13)) * 1274126177L) and 0xFFFFFFFFL
            h = (h xor (h ushr 16)) and 0xFFFFFFFFL
            return (h and 0xFFL).toInt()
        }
    }
}
