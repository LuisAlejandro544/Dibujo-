package com.dibujo.core

/**
 * La hoja: un lienzo ARGB con fondo y operaciones de dibujo puras en Kotlin.
 *
 * Es la implementación de referencia; el motor nativo (C++) replica esta
 * lógica y se usa automáticamente si la librería está disponible.
 */
class Lienzo(
    anchoInicial: Int,
    altoInicial: Int,
    val fondo: Int = Colores.BLANCO,
) {
    init {
        require(anchoInicial in 1..MAX_LADO && altoInicial in 1..MAX_LADO) {
            "hoja inválida: ${anchoInicial}x$altoInicial"
        }
    }

    /** Ancho de la hoja en píxeles. Cambia al expandir. */
    var ancho: Int = anchoInicial
        private set

    /** Alto de la hoja en píxeles. Cambia al expandir. */
    var alto: Int = altoInicial
        private set

    var pixeles: IntArray = IntArray(ancho * alto) { fondo }
        private set

    fun dentro(x: Int, y: Int): Boolean = x in 0 until ancho && y in 0 until alto

    fun pixel(x: Int, y: Int): Int = if (dentro(x, y)) pixeles[y * ancho + x] else fondo

    fun pintar(x: Int, y: Int, color: Int) {
        if (dentro(x, y)) pixeles[y * ancho + x] = color
    }

    /**
     * Pinta [color] sobre el píxel (x, y) mezclando por cobertura [alpha]
     * (0..255): 255 reemplaza, 0 no hace nada, el resto interpola canal a
     * canal. `alpha` sale del antialiasing del disco; el C++ replica esta
     * misma aritmética entera para mantener la paridad píxel a píxel.
     */
    fun pintarMezcla(x: Int, y: Int, color: Int, alpha: Int) {
        if (!dentro(x, y)) return
        val i = y * ancho + x
        if (alpha >= 255) {
            pixeles[i] = color
            return
        }
        if (alpha <= 0) return
        val destino = pixeles[i]
        val inv = 255 - alpha
        val r = (((color ushr 16) and 0xFF) * alpha + ((destino ushr 16) and 0xFF) * inv + 127) / 255
        val g = (((color ushr 8) and 0xFF) * alpha + ((destino ushr 8) and 0xFF) * inv + 127) / 255
        val b = ((color and 0xFF) * alpha + (destino and 0xFF) * inv + 127) / 255
        pixeles[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun limpiar() = pixeles.fill(fondo)

    /**
     * Expande la hoja agregando [izquierda]/[arriba]/[derecha]/[abajo] píxeles
     * nuevos (rellenos con el [fondo]). El contenido existente conserva sus
     * coordenadas: al agregar por la izquierda/arriba el dibujo se desplaza en
     * el buffer para no moverse de lugar. El tamaño final no puede pasar de
     * [MAX_LADO] por lado.
     */
    fun expandir(izquierda: Int, arriba: Int, derecha: Int, abajo: Int) {
        val izq = izquierda.coerceAtLeast(0)
        val arr = arriba.coerceAtLeast(0)
        val der = derecha.coerceAtLeast(0)
        val aba = abajo.coerceAtLeast(0)
        if (izq == 0 && arr == 0 && der == 0 && aba == 0) return
        val nuevoAncho = (ancho + izq + der).coerceIn(1, MAX_LADO)
        val nuevoAlto = (alto + arr + aba).coerceIn(1, MAX_LADO)
        if (nuevoAncho == ancho && nuevoAlto == alto) return
        val nuevo = IntArray(nuevoAncho * nuevoAlto) { fondo }
        val copiaAncho = minOf(ancho, nuevoAncho - izq).coerceAtLeast(0)
        val copiaAlto = minOf(alto, nuevoAlto - arr).coerceAtLeast(0)
        for (y in 0 until copiaAlto) {
            System.arraycopy(
                pixeles, y * ancho,
                nuevo, (y + arr) * nuevoAncho + izq,
                copiaAncho,
            )
        }
        ancho = nuevoAncho
        alto = nuevoAlto
        pixeles = nuevo
    }

    fun clonar(): Lienzo {
        val copia = Lienzo(ancho, alto, fondo)
        System.arraycopy(pixeles, 0, copia.pixeles, 0, pixeles.size)
        return copia
    }

    fun copiarDe(otro: Lienzo) {
        require(otro.ancho == ancho && otro.alto == alto) { "tamaños distintos" }
        System.arraycopy(otro.pixeles, 0, pixeles, 0, pixeles.size)
    }

    /**
     * Disco relleno y suavizado (punta redonda del lápiz). La cobertura de
     * cada píxel se calcula analíticamente (`0.5 + radio − distancia`) y se
     * mezcla con el fondo, así los bordes del trazo quedan con antialiasing
     * en lugar de escalonados. La distancia usa `Double` y el C++ idéntico
     * para que la paridad no dependa del redondeo de `sqrt` en `Float`.
     */
    fun disco(cx: Float, cy: Float, radio: Float, color: Int) {
        val margen = radio + 1.0f
        val x0 = kotlin.math.floor(cx - margen).toInt()
        val x1 = kotlin.math.ceil(cx + margen).toInt()
        val y0 = kotlin.math.floor(cy - margen).toInt()
        val y1 = kotlin.math.ceil(cy + margen).toInt()
        val r = radio.toDouble()
        val cxD = cx.toDouble()
        val cyD = cy.toDouble()
        for (y in y0..y1) {
            for (x in x0..x1) {
                if (x < 0 || y < 0 || x >= ancho || y >= alto) continue
                val dx = x - cxD
                val dy = y - cyD
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val cobertura = (0.5 + r - dist).coerceIn(0.0, 1.0)
                if (cobertura <= 0.0) continue
                val alpha = kotlin.math.round(cobertura * 255.0).toInt()
                pintarMezcla(x, y, color, alpha)
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

    companion object {
        /** Lado máximo de la hoja (coincide con el C++ y con `DibujoViewModel`). */
        const val MAX_LADO = 4096
    }
}
