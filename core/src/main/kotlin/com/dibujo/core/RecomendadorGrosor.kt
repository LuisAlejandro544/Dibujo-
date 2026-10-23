package com.dibujo.core

import kotlin.math.roundToInt

/**
 * Recomendación de grosor para calcar: estima el grosor medio de los trazos de
 * una imagen (ancho de línea) y lo mapea al rango de grosores del lápiz.
 *
 * Es una utilidad de **imagen** (como `Cuantizador` y `FiltrosImagen`): no toca
 * el motor ni la paridad C++/Kotlin. El cálculo se hace sobre los píxeles ARGB
 * de la imagen (ya filtrada o no) y devuelve un grosor sugerido en el rango
 * válido de `Lapiz`, o `null` si la imagen no tiene tinta analizable.
 */
object RecomendadorGrosor {

    /** Radio máximo (px) que se considera al medir el ancho de un trazo. */
    private const val RADIO_MAX = 32

    /**
     * Grosor recomendado para [pixeles] (ARGB, [ancho] × [alto]). Devuelve
     * `null` si no hay tinta suficiente o no se pudo medir.
     */
    fun grosorRecomendado(
        pixeles: IntArray,
        ancho: Int,
        alto: Int,
        fondo: Int = Colores.BLANCO,
    ): Int? {
        if (ancho <= 0 || alto <= 0 || pixeles.size < ancho * alto) return null
        val n = ancho * alto
        val tinta = BooleanArray(n)
        var cuentaTinta = 0
        for (i in 0 until n) {
            val p = pixeles[i]
            val alfa = (p ushr 24) and 0xFF
            val lum = FiltrosImagen.luminancia(p)
            val esTinta = alfa >= 16 && lum < 128
            tinta[i] = esTinta
            if (esTinta) cuentaTinta++
        }
        // Si casi todo o casi nada es tinta, no hay trazos medibles.
        if (cuentaTinta < 16 || cuentaTinta > n * 9 / 10) return null

        // Esqueleto de 1 px de la tinta (Zhang-Suen), para medir por el centro.
        val esqueleto = tinta.copyOf()
        adelgazar(esqueleto, ancho, alto)

        // Medir, para cada píxel del esqueleto, la distancia al fondo más
        // cercano (radio del trazo en ese punto).
        val radios = ArrayList<Int>()
        for (y in 0 until alto) {
            for (x in 0 until ancho) {
                val i = y * ancho + x
                if (!esqueleto[i]) continue
                var mejor = RADIO_MAX * RADIO_MAX + 1
                for (dy in -RADIO_MAX..RADIO_MAX) {
                    val yy = y + dy
                    if (yy < 0 || yy >= alto) continue
                    for (dx in -RADIO_MAX..RADIO_MAX) {
                        val xx = x + dx
                        val esFondo = if (xx < 0 || xx >= ancho) true else !tinta[yy * ancho + xx]
                        if (!esFondo) continue
                        val d = dx * dx + dy * dy
                        if (d < mejor) mejor = d
                    }
                }
                if (mejor <= RADIO_MAX * RADIO_MAX) {
                    val r = kotlin.math.sqrt(mejor.toDouble()).toInt().coerceAtLeast(1)
                    radios.add(r)
                }
            }
        }
        if (radios.isEmpty()) return null

        // Grosor ≈ 2 × radio medio (el radio es la mitad del ancho).
        val promedio = radios.sum().toDouble() / radios.size
        val grosor = (promedio * 2.0).roundToInt().coerceIn(Lapiz.GROSOR_MIN, Lapiz.GROSOR_MAX)
        return grosor
    }

    /** Zhang-Suen sobre una máscara binaria (copia del algoritmo de Lienzo). */
    private fun adelgazar(img: BooleanArray, w: Int, h: Int) {
        if (w < 3 || h < 3) return
        val borrar = ArrayList<Int>()
        var cambio = true
        while (cambio) {
            cambio = false
            borrar.clear()
            for (y in 1 until h - 1) {
                val fila = y * w
                for (x in 1 until w - 1) {
                    val i = fila + x
                    if (!img[i]) continue
                    val p2 = img[i - w]
                    val p3 = img[i - w + 1]
                    val p4 = img[i + 1]
                    val p5 = img[i + w + 1]
                    val p6 = img[i + w]
                    val p7 = img[i + w - 1]
                    val p8 = img[i - 1]
                    val p9 = img[i - w - 1]
                    if (!condicionesZhangSuen(p2, p3, p4, p5, p6, p7, p8, p9)) continue
                    if (p2 && p4 && p6) continue
                    if (p4 && p6 && p8) continue
                    borrar.add(i)
                }
            }
            for (i in borrar) img[i] = false
            if (borrar.isNotEmpty()) cambio = true

            borrar.clear()
            for (y in 1 until h - 1) {
                val fila = y * w
                for (x in 1 until w - 1) {
                    val i = fila + x
                    if (!img[i]) continue
                    val p2 = img[i - w]
                    val p3 = img[i - w + 1]
                    val p4 = img[i + 1]
                    val p5 = img[i + w + 1]
                    val p6 = img[i + w]
                    val p7 = img[i + w - 1]
                    val p8 = img[i - 1]
                    val p9 = img[i - w - 1]
                    if (!condicionesZhangSuen(p2, p3, p4, p5, p6, p7, p8, p9)) continue
                    if (p2 && p4 && p8) continue
                    if (p2 && p6 && p8) continue
                    borrar.add(i)
                }
            }
            for (i in borrar) img[i] = false
            if (borrar.isNotEmpty()) cambio = true
        }
    }

    private fun condicionesZhangSuen(
        p2: Boolean, p3: Boolean, p4: Boolean, p5: Boolean,
        p6: Boolean, p7: Boolean, p8: Boolean, p9: Boolean,
    ): Boolean {
        var b = 0
        if (p2) b++
        if (p3) b++
        if (p4) b++
        if (p5) b++
        if (p6) b++
        if (p7) b++
        if (p8) b++
        if (p9) b++
        if (b < 2 || b > 6) return false
        var a = 0
        if (!p2 && p3) a++
        if (!p3 && p4) a++
        if (!p4 && p5) a++
        if (!p5 && p6) a++
        if (!p6 && p7) a++
        if (!p7 && p8) a++
        if (!p8 && p9) a++
        if (!p9 && p2) a++
        return a == 1
    }
}
