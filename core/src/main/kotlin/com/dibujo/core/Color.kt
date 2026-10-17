package com.dibujo.core

/**
 * Colores ARGB (0xAARRGGBB) y la paleta de la app.
 */
object Colores {
    const val TRANSPARENTE = 0x00000000

    const val NEGRO = 0xFF212121.toInt()
    const val BLANCO = 0xFFFFFFFF.toInt()
    const val ROJO = 0xFFD32F2F.toInt()
    const val VERDE = 0xFF388E3C.toInt()
    const val AZUL = 0xFF1976D2.toInt()
    const val AMARILLO = 0xFFFBC02D.toInt()
    const val NARANJA = 0xFFF57C00.toInt()
    const val MORADO = 0xFF7B1FA2.toInt()
    const val CAFE = 0xFF795548.toInt()
    const val GRIS = 0xFF9E9E9E.toInt()
    const val ROSA = 0xFFEC407A.toInt()
    const val CIAN = 0xFF0097A7.toInt()

    /** Paleta del menú: colores cómodos de elegir por número. */
    val PALETA: List<Color> = listOf(
        Color("negro", NEGRO),
        Color("blanco", BLANCO),
        Color("rojo", ROJO),
        Color("verde", VERDE),
        Color("azul", AZUL),
        Color("amarillo", AMARILLO),
        Color("naranja", NARANJA),
        Color("morado", MORADO),
        Color("café", CAFE),
        Color("gris", GRIS),
        Color("rosa", ROSA),
        Color("cian", CIAN),
    )

    fun porIndice(i: Int): Color = PALETA[i.coerceIn(0, PALETA.size - 1)]
}

data class Color(val nombre: String, val argb: Int) {
    fun hex(): String = "#%06X".format(argb and 0xFFFFFF)
}
