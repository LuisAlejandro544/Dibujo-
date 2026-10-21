package com.dibujo.core

/**
 * Simplificación de polilíneas con **Ramer-Douglas-Peucker**: deja sólo los
 * vértices que hacen falta para dibujar la misma forma dentro de una
 * tolerancia dada (en px de hoja).
 *
 * Sirve para aligerar los trazos: una línea con 500 puntos de entrada se
 * guarda con 20, sin que se vea el cambio. Es el mismo algoritmo que usa la
 * pluma vectorial de la app (`VectorHoja.simplificar`).
 */
object RamerDouglasPeucker {

    /** Tolerancia mínima (px): por debajo el trazo se ve "temblado". */
    const val TOLERANCIA_MINIMA = 0.35f

    /**
     * Simplifica la polilínea [puntos] (pares x, y) con la [tolerancia]
     * indicada. Devuelve los puntos conservados (mismo formato de entrada).
     * Con dos puntos o menos no hay nada que simplificar.
     */
    fun simplificar(puntos: List<Float>, tolerancia: Float): List<Float> {
        val vertices = puntos.size / 2
        if (vertices <= 2) return puntos
        val limite = maxOf(tolerancia, TOLERANCIA_MINIMA)
        val limite2 = limite * limite
        val conservar = BooleanArray(vertices)
        conservar[0] = true
        conservar[vertices - 1] = true
        val pendientes = ArrayDeque<IntArray>()
        pendientes.addLast(intArrayOf(0, vertices - 1))
        while (pendientes.isNotEmpty()) {
            val rango = pendientes.removeLast()
            val inicio = rango[0]
            val fin = rango[1]
            val ax = puntos[inicio * 2]
            val ay = puntos[inicio * 2 + 1]
            val bx = puntos[fin * 2]
            val by = puntos[fin * 2 + 1]
            var lejano = -1
            var mayor = limite2
            for (i in inicio + 1 until fin) {
                val distancia = distanciaCuadrada(
                    puntos[i * 2],
                    puntos[i * 2 + 1],
                    ax,
                    ay,
                    bx,
                    by,
                )
                if (distancia > mayor) {
                    mayor = distancia
                    lejano = i
                }
            }
            if (lejano > 0) {
                conservar[lejano] = true
                pendientes.addLast(intArrayOf(inicio, lejano))
                pendientes.addLast(intArrayOf(lejano, fin))
            }
        }
        val salida = ArrayList<Float>(vertices * 2)
        for (i in 0 until vertices) {
            if (conservar[i]) {
                salida.add(puntos[i * 2])
                salida.add(puntos[i * 2 + 1])
            }
        }
        return salida
    }

    /** Distancia al cuadrado del punto ([px], [py]) al segmento AB. */
    private fun distanciaCuadrada(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val largo2 = dx * dx + dy * dy
        if (largo2 <= 1e-9f) {
            val ex = px - ax
            val ey = py - ay
            return ex * ex + ey * ey
        }
        val t = (((px - ax) * dx + (py - ay) * dy) / largo2).coerceIn(0f, 1f)
        val ex = px - (ax + t * dx)
        val ey = py - (ay + t * dy)
        return ex * ex + ey * ey
    }
}
