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

    /**
     * Línea gruesa doble (herramienta DOBLE): dibuja dos líneas paralelas
     * separadas [separacion] px respecto a la normal del segmento. La
     * aritmética es idéntica a `lineaDoble` del C++ para mantener la paridad
     * píxel a píxel.
     */
    fun lineaDoble(
        x0: Float, y0: Float, x1: Float, y1: Float,
        grosor: Int, redonda: Boolean, color: Int, separacion: Float,
    ) {
        val dx = x1 - x0
        val dy = y1 - y0
        val largo = kotlin.math.sqrt((dx.toDouble() * dx + dy.toDouble() * dy))
        if (largo < 1e-6) return
        val nx = (-dy / largo).toFloat()
        val ny = (dx / largo).toFloat()
        val ox = nx * separacion
        val oy = ny * separacion
        lineaGruesa(x0 + ox, y0 + oy, x1 + ox, y1 + oy, grosor, redonda, color)
        lineaGruesa(x0 - ox, y0 - oy, x1 - ox, y1 - oy, grosor, redonda, color)
    }

    /**
     * Relleno por inundación a partir de (x, y).
     *
     * En vez de apilar cada píxel vecino (lo que hacía millones de `push` y
     * tardaba segundos en hojas grandes), apila **tramos horizontales**: al
     * procesar una semilla se extiende el tramo contiguo de [origen] a
     * izquierda y derecha, se rellena entero y sólo se apila una semilla por
     * cada tramo nuevo en las filas de arriba y abajo. El resultado es el
     * mismo conjunto rellenado (4 vecinos, igualdad exacta) que el C++, que
     * replica este mismo algoritmo para no romper la paridad píxel a píxel.
     */
    fun rellenar(x: Int, y: Int, color: Int) {
        if (!dentro(x, y)) return
        val origen = pixeles[y * ancho + x]
        if (origen == color) return
        val pila = ArrayDeque<Int>()
        pila.add(y * ancho + x)
        while (pila.isNotEmpty()) {
            val i = pila.removeLast()
            if (pixeles[i] != origen) continue
            val cy = i / ancho
            val cx = i % ancho
            var izq = cx
            while (izq > 0 && pixeles[cy * ancho + izq - 1] == origen) izq--
            var der = cx
            while (der < ancho - 1 && pixeles[cy * ancho + der + 1] == origen) der++
            val base = cy * ancho
            for (xx in izq..der) pixeles[base + xx] = color
            if (cy > 0) agregarTramos(pila, base - ancho, izq, der, origen)
            if (cy < alto - 1) agregarTramos(pila, base + ancho, izq, der, origen)
        }
    }

    /** Apila una semilla por cada tramo contiguo de [origen] en la fila [base]. */
    private fun agregarTramos(pila: ArrayDeque<Int>, base: Int, izq: Int, der: Int, origen: Int) {
        var x = izq
        while (x <= der) {
            if (pixeles[base + x] == origen) {
                pila.add(base + x)
                while (x <= der && pixeles[base + x] == origen) x++
            } else {
                x++
            }
        }
    }

    /**
     * "Limpiar trazos": adelgaza toda la tinta de la hoja a un esqueleto de
     * 1 px (Zhang-Suen) y la vuelve a dibujar con un ancho uniforme [radio].
     *
     * Sirve cuando el usuario repasa el mismo trazo muchas veces y la línea
     * queda gruesa e irregular: el resultado es una línea pareja. Cada píxel
     * del esqueleto conserva el color que tenía en la hoja original (el
     * esqueleto es un subconjunto de la tinta). Devuelve `false` si la hoja
     * está vacía (no hay nada que limpiar).
     *
     * Es visión por computadora con aritmética entera, así que el C++ replica
     * idéntico el algoritmo para mantener la paridad píxel a píxel.
     */
    fun limpiarTrazos(radio: Float = RADIO_LIMPIEZA): Boolean {
        val w = ancho
        val h = alto
        val origen = pixeles.copyOf()
        val tinta = BooleanArray(w * h)
        var hay = false
        for (i in origen.indices) {
            val esTinta = origen[i] != fondo
            tinta[i] = esTinta
            if (esTinta) hay = true
        }
        if (!hay) return false
        adelgazar(tinta, w, h)
        pixeles.fill(fondo)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (tinta[y * w + x]) {
                    disco(x.toFloat(), y.toFloat(), radio, origen[y * w + x])
                }
            }
        }
        return true
    }

    /**
     * Adelgazado morfológico de Zhang-Suen sobre una máscara binaria. Deja cada
     * región de tinta reducida a una línea de 1 píxel. Sólo usa booleanos, por
     * eso el C++ puede replicar exactamente el mismo resultado.
     */
    private fun adelgazar(img: BooleanArray, w: Int, h: Int) {
        if (w < 3 || h < 3) return
        val borrar = ArrayList<Int>()
        var cambio = true
        while (cambio) {
            cambio = false
            // Sub-iteración 1: elimina el borde norte/este.
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
            // Sub-iteración 2: elimina el borde sur/oeste.
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

    /** Condiciones comunes: 2 <= B <= 6 y exactamente un cruce 0→1 (A == 1). */
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

    companion object {
        /** Lado máximo de la hoja (coincide con el C++ y con `DibujoViewModel`). */
        const val MAX_LADO = 4096

        /** Radio (px) con que se redibuja la línea limpia (coincide con el C++). */
        const val RADIO_LIMPIEZA = 1.0f
    }
}
