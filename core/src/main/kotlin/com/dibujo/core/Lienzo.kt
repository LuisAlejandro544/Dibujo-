package com.dibujo.core

/**
 * La hoja: un lienzo ARGB con fondo y operaciones de dibujo puras en Kotlin.
 *
 * Es la implementación de referencia; el motor nativo (C++) replica esta
 * lógica y se usa automáticamente si la librería está disponible.
 */
class Lienzo(
    val ancho: Int,
    val alto: Int,
    val fondo: Int = Colores.BLANCO,
) {
    init {
        require(ancho in 1..4096 && alto in 1..4096) { "hoja inválida: ${ancho}x$alto" }
    }

    val pixeles: IntArray = IntArray(ancho * alto) { fondo }

    fun dentro(x: Int, y: Int): Boolean = x in 0 until ancho && y in 0 until alto

    fun pixel(x: Int, y: Int): Int = if (dentro(x, y)) pixeles[y * ancho + x] else fondo

    fun pintar(x: Int, y: Int, color: Int) {
        if (dentro(x, y)) pixeles[y * ancho + x] = color
    }

    fun limpiar() = pixeles.fill(fondo)

    fun clonar(): Lienzo {
        val copia = Lienzo(ancho, alto, fondo)
        System.arraycopy(pixeles, 0, copia.pixeles, 0, pixeles.size)
        return copia
    }

    fun copiarDe(otro: Lienzo) {
        require(otro.ancho == ancho && otro.alto == alto) { "tamaños distintos" }
        System.arraycopy(otro.pixeles, 0, pixeles, 0, pixeles.size)
    }

    /** Disco relleno (punta del lápiz). Métrica de cuadrícula: px (x, y). */
    fun disco(cx: Float, cy: Float, radio: Float, color: Int) {
        val x0 = kotlin.math.floor(cx - radio).toInt()
        val x1 = kotlin.math.ceil(cx + radio).toInt()
        val y0 = kotlin.math.floor(cy - radio).toInt()
        val y1 = kotlin.math.ceil(cy + radio).toInt()
        val r2 = radio * radio
        for (y in y0..y1) {
            for (x in x0..x1) {
                if (x < 0 || y < 0 || x >= ancho || y >= alto) continue
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= r2) pintar(x, y, color)
            }
        }
    }

    /** Bloque cuadrado (punta cuadrada del lápiz). */
    fun bloque(cx: Float, cy: Float, radio: Float, color: Int) {
        val px = kotlin.math.round(cx).toInt()
        val py = kotlin.math.round(cy).toInt()
        val r = kotlin.math.round(radio).toInt()
        for (oy in -r..r) for (ox in -r..r) pintar(px + ox, py + oy, color)
    }

    /** Línea gruesa entre dos puntos (punta redonda o cuadrada). */
    fun lineaGruesa(
        x0: Float, y0: Float, x1: Float, y1: Float,
        grosor: Int, redonda: Boolean, color: Int,
    ) {
        val radio = grosor / 2.0f
        val pasos = maxOf(1, kotlin.math.ceil(maxOf(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0))).toInt())
        for (i in 0..pasos) {
            val t = i.toFloat() / pasos
            val x = x0 + (x1 - x0) * t
            val y = y0 + (y1 - y0) * t
            if (redonda) disco(x, y, radio, color) else bloque(x, y, radio, color)
        }
    }

    /** Relleno por inundación a partir de (x, y). */
    fun rellenar(x: Int, y: Int, color: Int) {
        if (!dentro(x, y)) return
        val origen = pixeles[y * ancho + x]
        if (origen == color) return
        val pila = ArrayDeque<Int>()
        pila.add(y * ancho + x)
        while (pila.isNotEmpty()) {
            val i = pila.removeLast()
            if (pixeles[i] != origen) continue
            pixeles[i] = color
            val cx = i % ancho
            val cy = i / ancho
            if (cx > 0) pila.add(i - 1)
            if (cx < ancho - 1) pila.add(i + 1)
            if (cy > 0) pila.add(i - ancho)
            if (cy < alto - 1) pila.add(i + ancho)
        }
    }
}
