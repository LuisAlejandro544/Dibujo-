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
    const val ROJO_OSCURO = 0xFF8E0000.toInt()
    const val LIMA = 0xFFCDDC39.toInt()
    const val VERDE_CLARO = 0xFF8BC34A.toInt()
    const val VERDE_OSCURO = 0xFF1B5E20.toInt()
    const val CELESTE = 0xFF03A9F4.toInt()
    const val AZUL_OSCURO = 0xFF0D47A1.toInt()
    const val AZUL_MARINO = 0xFF1A237E.toInt()
    const val TURQUESA = 0xFF26A69A.toInt()
    const val INDIGO = 0xFF3F51B5.toInt()
    const val VIOLETA = 0xFF9C27B0.toInt()
    const val FUCSIA = 0xFFE91E63.toInt()
    const val DORADO = 0xFFFFB300.toInt()
    const val PIEL = 0xFFFFCC9E.toInt()
    const val MELOCOTON = 0xFFFFAB91.toInt()
    const val BEIGE = 0xFFF5E6C8.toInt()
    const val GRIS_CLARO = 0xFFE0E0E0.toInt()
    const val GRIS_OSCURO = 0xFF424242.toInt()

    /**
     * Paleta del menú: colores cómodos de elegir por número.
     *
     * Los primeros 12 son estables (la consola y sus tests los eligen por
     * número, p. ej. `3` = rojo): los colores nuevos se agregan al final para
     * no correr los índices.
     */
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
        Color("rojo oscuro", ROJO_OSCURO),
        Color("lima", LIMA),
        Color("verde claro", VERDE_CLARO),
        Color("verde oscuro", VERDE_OSCURO),
        Color("celeste", CELESTE),
        Color("azul oscuro", AZUL_OSCURO),
        Color("azul marino", AZUL_MARINO),
        Color("turquesa", TURQUESA),
        Color("índigo", INDIGO),
        Color("violeta", VIOLETA),
        Color("fucsia", FUCSIA),
        Color("dorado", DORADO),
        Color("piel", PIEL),
        Color("melocotón", MELOCOTON),
        Color("beige", BEIGE),
        Color("gris claro", GRIS_CLARO),
        Color("gris oscuro", GRIS_OSCURO),
    )

    fun porIndice(i: Int): Color = PALETA[i.coerceIn(0, PALETA.size - 1)]
}

data class Color(val nombre: String, val argb: Int) {
    fun hex(): String = "#%06X".format(argb and 0xFFFFFF)
}
