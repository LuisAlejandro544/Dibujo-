package com.dibujo.core

/**
 * Cuantización de color por k-medias (k-means): agrupa los píxeles de una
 * imagen por similitud y devuelve sus colores dominantes.
 *
 * Es aprendizaje no supervisado **local**: no usa ninguna red neuronal ni
 * dependencia externa (peso 0 MB). Se usa para sacar la paleta de la imagen de
 * calco y ofrecérsela al usuario para terminar de colorear.
 */
object Cuantizador {

    /**
     * Devuelve hasta [k] colores dominantes en ARGB opaco a partir de las
     * [muestras]. Es determinista para una [semilla] dada (tests estables) e
     * ignora las muestras transparentes. El orden es por población: los colores
     * más frecuentes primero.
     */
    fun paleta(
        muestras: IntArray,
        k: Int,
        iteraciones: Int = 12,
        semilla: Long = 20261017L,
    ): List<Int> {
        val datos = muestras.filter { (it ushr 24) >= 16 }.toIntArray()
        if (datos.isEmpty() || k <= 0) return emptyList()
        val kk = minOf(k, datos.size)
        val azar = java.util.Random(semilla)

        val cent = FloatArray(kk * 3)
        inicializarKMeansMasMas(datos, cent, kk, azar)

        val suma = FloatArray(kk * 3)
        val cuenta = IntArray(kk)
        repeat(iteraciones) {
            java.util.Arrays.fill(suma, 0f)
            java.util.Arrays.fill(cuenta, 0)
            for (c in datos) {
                val r = ((c ushr 16) and 0xFF).toFloat()
                val g = ((c ushr 8) and 0xFF).toFloat()
                val b = (c and 0xFF).toFloat()
                var mejor = 0
                var mejorDist = Double.MAX_VALUE
                for (j in 0 until kk) {
                    val dr = r - cent[j * 3]
                    val dg = g - cent[j * 3 + 1]
                    val db = b - cent[j * 3 + 2]
                    val d = (dr * dr + dg * dg + db * db).toDouble()
                    if (d < mejorDist) {
                        mejorDist = d
                        mejor = j
                    }
                }
                suma[mejor * 3] += r
                suma[mejor * 3 + 1] += g
                suma[mejor * 3 + 2] += b
                cuenta[mejor]++
            }
            for (j in 0 until kk) {
                if (cuenta[j] > 0) {
                    cent[j * 3] = suma[j * 3] / cuenta[j]
                    cent[j * 3 + 1] = suma[j * 3 + 1] / cuenta[j]
                    cent[j * 3 + 2] = suma[j * 3 + 2] / cuenta[j]
                }
            }
        }

        val orden = (0 until kk)
            .filter { cuenta[it] > 0 }
            .sortedWith(compareByDescending<Int> { cuenta[it] }.thenBy { it })
        return orden.map { j ->
            val r = cent[j * 3].toInt().coerceIn(0, 255)
            val g = cent[j * 3 + 1].toInt().coerceIn(0, 255)
            val b = cent[j * 3 + 2].toInt().coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    /** k-means++: primer centroide al azar y los siguientes, sesgados a lo lejano. */
    private fun inicializarKMeansMasMas(
        datos: IntArray,
        cent: FloatArray,
        k: Int,
        azar: java.util.Random,
    ) {
        val primero = datos[azar.nextInt(datos.size)]
        cent[0] = ((primero ushr 16) and 0xFF).toFloat()
        cent[1] = ((primero ushr 8) and 0xFF).toFloat()
        cent[2] = (primero and 0xFF).toFloat()
        val dist = DoubleArray(datos.size) { Double.MAX_VALUE }
        for (j in 1 until k) {
            val cr = cent[(j - 1) * 3]
            val cg = cent[(j - 1) * 3 + 1]
            val cb = cent[(j - 1) * 3 + 2]
            var total = 0.0
            for (i in datos.indices) {
                val c = datos[i]
                val dr = ((c ushr 16) and 0xFF) - cr
                val dg = ((c ushr 8) and 0xFF) - cg
                val db = (c and 0xFF) - cb
                val d = (dr * dr + dg * dg + db * db).toDouble()
                if (d < dist[i]) dist[i] = d
                total += dist[i]
            }
            val objetivo = azar.nextDouble() * total
            var acumulado = 0.0
            var elegido = 0
            for (i in datos.indices) {
                acumulado += dist[i]
                if (acumulado >= objetivo) {
                    elegido = i
                    break
                }
            }
            val c = datos[elegido]
            cent[j * 3] = ((c ushr 16) and 0xFF).toFloat()
            cent[j * 3 + 1] = ((c ushr 8) and 0xFF).toFloat()
            cent[j * 3 + 2] = (c and 0xFF).toFloat()
        }
    }
}
