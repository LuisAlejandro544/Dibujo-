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

    // Colores con brillo (neón) y metálicos: pensados para resaltar sobre la
    // hoja, como los rotuladores fluorescentes o las pinturas con brillo.
    const val VERDE_NEON = 0xFF39FF14.toInt()
    const val ROSA_NEON = 0xFFFF2D95.toInt()
    const val CIAN_NEON = 0xFF00E5FF.toInt()
    const val AMARILLO_NEON = 0xFFEEFF41.toInt()
    const val NARANJA_NEON = 0xFFFF6D00.toInt()
    const val VIOLETA_NEON = 0xFFD500F9.toInt()
    const val LIMA_BRILLANTE = 0xFFC6FF00.toInt()
    const val TURQUESA_BRILLANTE = 0xFF1DE9B6.toInt()
    const val MAGENTA = 0xFFFF00FF.toInt()
    const val ORO = 0xFFFFD700.toInt()
    const val PLATA = 0xFFC0C0C0.toInt()
    const val BRONCE = 0xFFCD7F32.toInt()
    const val COBRE = 0xFFB87333.toInt()
    const val ROJO_BRILLANTE = 0xFFFF1744.toInt()
    const val AZUL_BRILLANTE = 0xFF00B0FF.toInt()
    const val VERDE_BRILLANTE = 0xFF00E676.toInt()
    const val MORADO_CLARO = 0xFFBA68C8.toInt()
    const val ROSA_PASTEL = 0xFFF8BBD0.toInt()
    const val AZUL_PASTEL = 0xFFB3E5FC.toInt()
    const val VERDE_PASTEL = 0xFFC8E6C9.toInt()
    const val AMARILLO_PASTEL = 0xFFFFF9C4.toInt()
    const val MENTA = 0xFF98FF98.toInt()
    const val LAVANDA = 0xFFE6E6FA.toInt()
    const val SALMON = 0xFFFA8072.toInt()
    const val CORAL = 0xFFFF7043.toInt()
    const val OLIVA = 0xFF808000.toInt()
    const val ESMERALDA = 0xFF2E8B57.toInt()
    const val CHOCOLATE = 0xFF6D4C41.toInt()
    const val VINO = 0xFF7B1E3A.toInt()

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
        // Brillos (neón) y metálicos: al final para no correr los índices.
        Color("verde neón", VERDE_NEON),
        Color("rosa neón", ROSA_NEON),
        Color("cian neón", CIAN_NEON),
        Color("amarillo neón", AMARILLO_NEON),
        Color("naranja neón", NARANJA_NEON),
        Color("violeta neón", VIOLETA_NEON),
        Color("lima brillante", LIMA_BRILLANTE),
        Color("turquesa brillante", TURQUESA_BRILLANTE),
        Color("magenta", MAGENTA),
        Color("oro", ORO),
        Color("plata", PLATA),
        Color("bronce", BRONCE),
        Color("cobre", COBRE),
        Color("rojo brillante", ROJO_BRILLANTE),
        Color("azul brillante", AZUL_BRILLANTE),
        Color("verde brillante", VERDE_BRILLANTE),
        Color("morado claro", MORADO_CLARO),
        Color("rosa pastel", ROSA_PASTEL),
        Color("azul pastel", AZUL_PASTEL),
        Color("verde pastel", VERDE_PASTEL),
        Color("amarillo pastel", AMARILLO_PASTEL),
        Color("menta", MENTA),
        Color("lavanda", LAVANDA),
        Color("salmón", SALMON),
        Color("coral", CORAL),
        Color("oliva", OLIVA),
        Color("esmeralda", ESMERALDA),
        Color("chocolate", CHOCOLATE),
        Color("vino", VINO),
    )

    fun porIndice(i: Int): Color = PALETA[i.coerceIn(0, PALETA.size - 1)]
}

data class Color(val nombre: String, val argb: Int) {
    fun hex(): String = "#%06X".format(argb and 0xFFFFFF)
}
