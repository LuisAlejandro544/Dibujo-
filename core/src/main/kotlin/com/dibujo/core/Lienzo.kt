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
     * Reemplaza el contenido de la hoja por [desde] (ancho * alto ARGB). El
     * llamador es responsable de haberlo validado; acá sólo se exige el tamaño.
     */
    fun reemplazar(desde: IntArray) {
        require(desde.size == pixeles.size) { "tamaño distinto: ${desde.size}" }
        System.arraycopy(desde, 0, pixeles, 0, pixeles.size)
    }

    /**
     * Transforma el contenido de la hoja sin cambiar su tamaño: lo **voltea**
     * (horizontal/vertical), lo **mueve** [desplazamientoX]/[desplazamientoY] px
     * de hoja y lo **escala** [escala] veces alrededor del centro. Lo que queda
     * afuera de la hoja se recorta y lo que no se cubre queda con el [fondo].
     *
     * El mapeo es inverso (por cada píxel destino se busca su origen), así que
     * ampliar no deja huecos. La aritmética va en `Double` y el redondeo es
     * `floor(v + 0.5)` en los dos motores, para que la paridad píxel a píxel no
     * dependa de la última ulp de `round`/`lround`. Devuelve `false` si no hay
     * nada que transformar (sin espejos, sin desplazamiento y escala 1) o si la
     * escala no es positiva.
     */
    fun transformar(
        voltearHorizontal: Boolean,
        voltearVertical: Boolean,
        desplazamientoX: Float,
        desplazamientoY: Float,
        escala: Float,
    ): Boolean {
        if (escala <= 0f) return false
        if (!voltearHorizontal && !voltearVertical &&
            desplazamientoX == 0f && desplazamientoY == 0f && escala == 1f
        ) {
            return false
        }
        val fuente = pixeles.copyOf()
        val cx = (ancho - 1) / 2.0
        val cy = (alto - 1) / 2.0
        val e = escala.toDouble()
        val tx = desplazamientoX.toDouble()
        val ty = desplazamientoY.toDouble()
        val fx = if (voltearHorizontal) -1.0 else 1.0
        val fy = if (voltearVertical) -1.0 else 1.0
        for (y in 0 until alto) {
            val v = y - cy
            val sy = kotlin.math.floor(cy + fy * ((v - ty) / e) + 0.5).toInt()
            val fila = y * ancho
            for (x in 0 until ancho) {
                val u = x - cx
                val sx = kotlin.math.floor(cx + fx * ((u - tx) / e) + 0.5).toInt()
                pixeles[fila + x] =
                    if (sx in 0 until ancho && sy in 0 until alto) fuente[sy * ancho + sx] else fondo
            }
        }
        return true
    }

    /**
     * Disco relleno y suavizado (punta redonda del lápiz). La cobertura de
     * cada píxel se calcula analíticamente (`0.5 + radio − distancia`) y se
     * mezcla con el fondo, así los bordes del trazo quedan con antialiasing
     * en lugar de escalonados. La distancia usa `Double` y el C++ idéntico
     * para que la paridad no dependa del redondeo de `sqrt` en `Float`.
     */
    fun disco(
        cx: Float,
        cy: Float,
        radio: Float,
        color: Int,
        textura: Textura = Textura.LISA,
    ) {
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
                pintarMezcla(x, y, color, textura.aplicar(alpha, x, y))
            }
        }
    }

    /** Bloque cuadrado (punta cuadrada del lápiz). */
    fun bloque(
        cx: Float,
        cy: Float,
        radio: Float,
        color: Int,
        textura: Textura = Textura.LISA,
    ) {
        val px = kotlin.math.round(cx).toInt()
        val py = kotlin.math.round(cy).toInt()
        val r = kotlin.math.round(radio).toInt()
        for (oy in -r..r) {
            for (ox in -r..r) {
                val x = px + ox
                val y = py + oy
                if (textura == Textura.LISA) {
                    pintar(x, y, color)
                } else {
                    pintarMezcla(x, y, color, textura.aplicar(255, x, y))
                }
            }
        }
    }

    /**
     * Línea gruesa entre dos puntos (punta redonda o cuadrada).
     *
     * Con punta redonda se rasteriza como una **cápsula**: la cobertura de
     * cada píxel sale de la distancia al segmento y se mezcla **una sola vez**.
     * Antes se estampaba un disco cada ≤1 px y cada estampado volvía a mezclar,
     * así que los bordes acumulaban cobertura y quedaban irregulares. El C++
     * replica esta misma aritmética para mantener la paridad píxel a píxel.
     */
    fun lineaGruesa(
        x0: Float, y0: Float, x1: Float, y1: Float,
        grosor: Int, redonda: Boolean, color: Int,
        textura: Textura = Textura.LISA,
    ) {
        if (!redonda) {
            val radio = grosor / 2.0f
            val pasos = maxOf(
                1,
                kotlin.math.ceil(maxOf(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0))).toInt(),
            )
            for (i in 0..pasos) {
                val t = i.toFloat() / pasos
                bloque(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, radio, color, textura)
            }
            return
        }
        val puntos = floatArrayOf(x0, y0, x1, y1)
        polilineaGruesa(puntos, 4, grosor, redonda = true, color, textura)
    }

    /**
     * Rasteriza una polilínea de punta redonda con **cobertura máxima por
     * píxel** y mezcla una sola vez. Así los segmentos que comparten un vértice
     * no oscurecen el empalme por doble mezcla. [puntos] son coordenadas
     * intercaladas (x, y) y [cantidad] la cantidad de floats usados (2 por
     * punto). El C++ replica el algoritmo idéntico (paridad).
     */
    fun polilineaGruesa(
        puntos: FloatArray,
        cantidad: Int,
        grosor: Int,
        redonda: Boolean,
        color: Int,
        textura: Textura = Textura.LISA,
    ) {
        if (cantidad < 2) return
        if (!redonda) {
            var i = 0
            while (i + 3 < cantidad) {
                lineaGruesa(
                    puntos[i], puntos[i + 1], puntos[i + 2], puntos[i + 3],
                    grosor, redonda = false, color, textura,
                )
                i += 2
            }
            if (cantidad == 2) bloque(puntos[0], puntos[1], grosor / 2.0f, color, textura)
            return
        }
        val radio = grosor / 2.0f
        if (cantidad == 2) {
            disco(puntos[0], puntos[1], radio, color, textura)
            return
        }
        val margen = radio + 1.0f
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < cantidad) {
            val px = puntos[i]
            val py = puntos[i + 1]
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py
            i += 2
        }
        val bx0 = kotlin.math.floor((minX - margen).toDouble()).toInt().coerceAtLeast(0)
        val by0 = kotlin.math.floor((minY - margen).toDouble()).toInt().coerceAtLeast(0)
        val bx1 = kotlin.math.ceil((maxX + margen).toDouble()).toInt().coerceAtMost(ancho - 1)
        val by1 = kotlin.math.ceil((maxY + margen).toDouble()).toInt().coerceAtMost(alto - 1)
        if (bx0 > bx1 || by0 > by1) return
        val anchoCaja = bx1 - bx0 + 1
        val altoCaja = by1 - by0 + 1
        val cobertura = ByteArray(anchoCaja * altoCaja)
        i = 0
        while (i + 3 < cantidad) {
            cubrirSegmento(
                cobertura, anchoCaja, bx0, by0, bx1, by1,
                puntos[i], puntos[i + 1], puntos[i + 2], puntos[i + 3], radio,
            )
            i += 2
        }
        for (y in by0..by1) {
            val fila = (y - by0) * anchoCaja
            for (x in bx0..bx1) {
                val alpha = cobertura[fila + (x - bx0)].toInt() and 0xFF
                if (alpha > 0) pintarMezcla(x, y, color, textura.aplicar(alpha, x, y))
            }
        }
    }

    /**
     * Acumula en [cobertura] la cobertura máxima (0..255) del segmento
     * (ax, ay)-(bx, by) con radio [radio]. La distancia punto-segmento va en
     * `Double` y la cobertura se redondea con `floor(v + 0.5)` para que Kotlin
     * y C++ den exactamente el mismo entero. No mezcla: sólo llena la máscara.
     */
    private fun cubrirSegmento(
        cobertura: ByteArray,
        anchoCaja: Int,
        bx0: Int,
        by0: Int,
        bx1: Int,
        by1: Int,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        radio: Float,
    ) {
        val margen = radio + 1.0f
        val x0 = kotlin.math.floor((ax - margen).toDouble()).toInt().coerceAtLeast(bx0)
        val x1 = kotlin.math.ceil((ax + margen).toDouble()).toInt().coerceAtMost(bx1)
        val y0 = kotlin.math.floor((ay - margen).toDouble()).toInt().coerceAtLeast(by0)
        val y1 = kotlin.math.ceil((ay + margen).toDouble()).toInt().coerceAtMost(by1)
        val x2 = kotlin.math.floor((bx - margen).toDouble()).toInt().coerceAtLeast(bx0)
        val x3 = kotlin.math.ceil((bx + margen).toDouble()).toInt().coerceAtMost(bx1)
        val y2 = kotlin.math.floor((by - margen).toDouble()).toInt().coerceAtLeast(by0)
        val y3 = kotlin.math.ceil((by + margen).toDouble()).toInt().coerceAtMost(by1)
        val desdeX = minOf(x0, x2)
        val hastaX = maxOf(x1, x3)
        val desdeY = minOf(y0, y2)
        val hastaY = maxOf(y1, y3)
        if (desdeX > hastaX || desdeY > hastaY) return
        val axD = ax.toDouble()
        val ayD = ay.toDouble()
        val dx = bx.toDouble() - axD
        val dy = by.toDouble() - ayD
        val largo2 = dx * dx + dy * dy
        val r = radio.toDouble()
        for (y in desdeY..hastaY) {
            val fila = (y - by0) * anchoCaja
            val pdy = y - ayD
            for (x in desdeX..hastaX) {
                val pdx = x - axD
                val dist: Double
                if (largo2 <= 1e-12) {
                    dist = kotlin.math.sqrt(pdx * pdx + pdy * pdy)
                } else {
                    val t = ((pdx * dx + pdy * dy) / largo2).coerceIn(0.0, 1.0)
                    val qx = pdx - t * dx
                    val qy = pdy - t * dy
                    dist = kotlin.math.sqrt(qx * qx + qy * qy)
                }
                val cob = (0.5 + r - dist).coerceIn(0.0, 1.0)
                if (cob <= 0.0) continue
                val alpha = kotlin.math.floor(cob * 255.0 + 0.5).toInt()
                val idx = fila + (x - bx0)
                if (alpha > (cobertura[idx].toInt() and 0xFF)) cobertura[idx] = alpha.toByte()
            }
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
        textura: Textura = Textura.LISA,
    ) {
        val dx = x1 - x0
        val dy = y1 - y0
        val largo = kotlin.math.sqrt((dx.toDouble() * dx + dy.toDouble() * dy))
        if (largo < 1e-6) return
        val nx = (-dy / largo).toFloat()
        val ny = (dx / largo).toFloat()
        val ox = nx * separacion
        val oy = ny * separacion
        lineaGruesa(x0 + ox, y0 + oy, x1 + ox, y1 + oy, grosor, redonda, color, textura)
        lineaGruesa(x0 - ox, y0 - oy, x1 - ox, y1 - oy, grosor, redonda, color, textura)
    }

    /**
     * Polilínea doble (herramienta DOBLE): dibuja el recorrido principal y una
     * segunda línea configurable (estilo, color de sombra y colocación).
     *
     * La geometría usa una normal suavizada por vértice (promedio de las
     * direcciones de los segmentos vecinos), así las líneas no tiemblan en los
     * empalmes. [modo] decide dónde va la copia: alrededor del trazo
     * ([ModoDoble.CENTRADO]), a un lado ([ModoDoble.LADO_A]/[ModoDoble.LADO_B])
     * o reflejada al otro lado del primer punto ([ModoDoble.ESPEJO]). El C++
     * replica el mismo cálculo (paridad píxel a píxel).
     */
    fun polilineaDoble(
        puntos: FloatArray,
        cantidad: Int,
        grosorPrincipal: Int,
        redondaPrincipal: Boolean,
        colorPrincipal: Int,
        grosorCopia: Int,
        redondaCopia: Boolean,
        colorCopia: Int,
        separacion: Float,
        modo: ModoDoble,
        texturaPrincipal: Textura = Textura.LISA,
        texturaCopia: Textura = Textura.LISA,
    ) {
        if (cantidad < 2) return
        if (cantidad == 2) {
            if (redondaPrincipal) {
                disco(puntos[0], puntos[1], grosorPrincipal / 2.0f, colorPrincipal, texturaPrincipal)
            } else {
                bloque(puntos[0], puntos[1], grosorPrincipal / 2.0f, colorPrincipal, texturaPrincipal)
            }
            return
        }
        val n = cantidad / 2
        val ladoA = FloatArray(cantidad)
        val ladoB = FloatArray(cantidad)
        for (i in 0 until n) {
            val base = i * 2
            val dx: Float
            val dy: Float
            when (i) {
                0 -> {
                    dx = puntos[2] - puntos[0]
                    dy = puntos[3] - puntos[1]
                }
                n - 1 -> {
                    dx = puntos[base] - puntos[base - 2]
                    dy = puntos[base + 1] - puntos[base - 1]
                }
                else -> {
                    dx = puntos[base + 2] - puntos[base - 2]
                    dy = puntos[base + 3] - puntos[base - 1]
                }
            }
            val largo = kotlin.math.sqrt(dx.toDouble() * dx + dy.toDouble() * dy)
            val nx: Float
            val ny: Float
            if (largo < 1e-6) {
                nx = 0f
                ny = 0f
            } else {
                nx = (-dy / largo).toFloat()
                ny = (dx / largo).toFloat()
            }
            val ox = nx * separacion
            val oy = ny * separacion
            ladoA[base] = puntos[base] + ox
            ladoA[base + 1] = puntos[base + 1] + oy
            ladoB[base] = puntos[base] - ox
            ladoB[base + 1] = puntos[base + 1] - oy
        }
        when (modo) {
            ModoDoble.CENTRADO -> {
                polilineaGruesa(
                    ladoA, cantidad, grosorPrincipal, redondaPrincipal,
                    colorPrincipal, texturaPrincipal,
                )
                polilineaGruesa(
                    ladoB, cantidad, grosorCopia, redondaCopia,
                    colorCopia, texturaCopia,
                )
            }
            ModoDoble.LADO_A, ModoDoble.LADO_B -> {
                polilineaGruesa(
                    puntos, cantidad, grosorPrincipal, redondaPrincipal,
                    colorPrincipal, texturaPrincipal,
                )
                val lado = if (modo == ModoDoble.LADO_A) ladoA else ladoB
                polilineaGruesa(lado, cantidad, grosorCopia, redondaCopia, colorCopia, texturaCopia)
            }
            ModoDoble.ESPEJO -> {
                polilineaGruesa(
                    puntos, cantidad, grosorPrincipal, redondaPrincipal,
                    colorPrincipal, texturaPrincipal,
                )
                val espejo = FloatArray(cantidad)
                val ejeX = puntos[0]
                for (i in 0 until n) {
                    val base = i * 2
                    espejo[base] = 2.0f * ejeX - puntos[base]
                    espejo[base + 1] = puntos[base + 1]
                }
                polilineaGruesa(
                    espejo, cantidad, grosorCopia, redondaCopia,
                    colorCopia, texturaCopia,
                )
            }
        }
    }

    /**
     * Relleno por inundación a partir de (x, y), limitado a la región
     * **cerrada** que lo contiene.
     *
     * El balde sólo debe colorear lo que el usuario encerró con un trazo. Como
     * el dibujo a mano deja aperturas de pocos píxeles en el contorno (una cara
     * o un brazo grandes tienen más chance de quedar con un hueco que un trazo
     * chico), primero se inunda la región exacta del píxel tocado y, si el
     * relleno se escapa hacia el fondo, se cierran esos huecos (morfología) y
     * se reintenta. Si aun así la región alcanza el borde de la hoja (fondo
     * abierto), no se pinta nada y se devuelve `false`: tocar afuera de una
     * figura no inunda toda la hoja. Devuelve `true` si cambió algún píxel.
     *
     * En vez de apilar cada píxel vecino (lo que hacía millones de `push` y
     * tardaba segundos en hojas grandes), apila **tramos horizontales**: al
     * procesar una semilla se extiende el tramo contiguo a izquierda y derecha,
     * se marca entero y sólo se apila una semilla por cada tramo nuevo en las
     * filas de arriba y abajo. El resultado es el mismo conjunto rellenado
     * (4 vecinos, igualdad exacta) que el C++, que replica este mismo algoritmo
     * para no romper la paridad píxel a píxel.
     */
    fun rellenar(x: Int, y: Int, color: Int): Boolean {
        if (!dentro(x, y)) return false
        val semilla = y * ancho + x
        val origen = pixeles[semilla]
        if (origen == color) return false

        // Intento directo: el trazo ya encierra la región (caso habitual).
        val directo = inundar(semilla, origen, bloqueado = null)
        if (directo != null) {
            for (i in directo.indices) if (directo[i]) pixeles[i] = color
            return true
        }

        // El relleno se escapó por un hueco del contorno: cierra huecos
        // pequeños y reintenta. Sólo se pintan los píxeles que siguen siendo
        // del color de origen (nunca el trazo ni su antialiasing).
        val bloqueado = cerrarHuecos(origen)
        val cerrado = inundar(semilla, origen, bloqueado) ?: return false
        var pinto = false
        for (i in cerrado.indices) {
            if (cerrado[i] && pixeles[i] == origen) {
                pixeles[i] = color
                pinto = true
            }
        }
        return pinto
    }

    /**
     * Inunda la región de [semilla] y devuelve qué píxeles la forman, o `null`
     * si la región alcanza el borde de la hoja (fondo abierto). Si [bloqueado]
     * es `null` sólo se atraviesan los píxeles del color [origen]; si no, se
     * atraviesa todo lo que no esté bloqueado (así el cierre de huecos define
     * la conectividad sin pintar sobre el trazo).
     */
    private fun inundar(semilla: Int, origen: Int, bloqueado: BooleanArray?): BooleanArray? {
        val marca = BooleanArray(pixeles.size)
        val pila = ArrayDeque<Int>()
        pila.add(semilla)
        var tocaBorde = false
        while (pila.isNotEmpty()) {
            val i = pila.removeLast()
            if (marca[i] || !libre(i, origen, bloqueado)) continue
            val cy = i / ancho
            val cx = i % ancho
            val base = cy * ancho
            var izq = cx
            while (izq > 0 && libre(base + izq - 1, origen, bloqueado)) izq--
            var der = cx
            while (der < ancho - 1 && libre(base + der + 1, origen, bloqueado)) der++
            for (xx in izq..der) marca[base + xx] = true
            if (izq == 0 || der == ancho - 1 || cy == 0 || cy == alto - 1) tocaBorde = true
            if (cy > 0) agregarTramos(pila, marca, base - ancho, izq, der, origen, bloqueado)
            if (cy < alto - 1) agregarTramos(pila, marca, base + ancho, izq, der, origen, bloqueado)
        }
        return if (tocaBorde) null else marca
    }

    /** Un píxel es atravesable si es del color [origen] o no está bloqueado. */
    private fun libre(i: Int, origen: Int, bloqueado: BooleanArray?): Boolean =
        if (bloqueado == null) pixeles[i] == origen else !bloqueado[i]

    /** Apila una semilla por cada tramo contiguo atravesable sin marcar en la fila [base]. */
    private fun agregarTramos(
        pila: ArrayDeque<Int>,
        marca: BooleanArray,
        base: Int,
        izq: Int,
        der: Int,
        origen: Int,
        bloqueado: BooleanArray?,
    ) {
        var x = izq
        while (x <= der) {
            if (!marca[base + x] && libre(base + x, origen, bloqueado)) {
                pila.add(base + x)
                while (x <= der && libre(base + x, origen, bloqueado)) x++
            } else {
                x++
            }
        }
    }

    /**
     * Cierra los huecos pequeños del contorno: toma como barrera todo píxel que
     * no sea del color [origen] y le aplica un cierre morfológico (dilatar y
     * erosionar) con un cuadrado de lado 2*[RADIO_CIERRE]+1. Así un trazo con
     * aperturas de pocos píxeles vuelve a encerrar su región. Fuera de la hoja
     * se considera barrera (así los huecos cerca del borde también se cierran;
     * si el relleno igual llega al borde, se descarta). El cuadrado es
     * separable, así que se dilata y erosiona en horizontal y en vertical (más
     * rápido que el elemento 2D y mismo resultado); el C++ replica idéntico el
     * cálculo (paridad).
     */
    private fun cerrarHuecos(origen: Int): BooleanArray {
        val n = pixeles.size
        val a = BooleanArray(n)
        val b = BooleanArray(n)
        // Dilatación horizontal.
        for (y in 0 until alto) {
            val base = y * ancho
            for (x in 0 until ancho) {
                if (pixeles[base + x] == origen) continue
                val desde = (x - RADIO_CIERRE).coerceAtLeast(0)
                val hasta = (x + RADIO_CIERRE).coerceAtMost(ancho - 1)
                for (xx in desde..hasta) a[base + xx] = true
            }
        }
        // Dilatación vertical.
        for (y in 0 until alto) {
            val base = y * ancho
            for (x in 0 until ancho) {
                if (!a[base + x]) continue
                val desde = (y - RADIO_CIERRE).coerceAtLeast(0)
                val hasta = (y + RADIO_CIERRE).coerceAtMost(alto - 1)
                for (yy in desde..hasta) b[yy * ancho + x] = true
            }
        }
        // Erosión horizontal (fuera de la hoja cuenta como barrera).
        a.fill(false)
        for (y in 0 until alto) {
            val base = y * ancho
            for (x in 0 until ancho) {
                var todo = true
                for (dx in -RADIO_CIERRE..RADIO_CIERRE) {
                    val xx = x + dx
                    if (xx in 0 until ancho && !b[base + xx]) {
                        todo = false
                        break
                    }
                }
                a[base + x] = todo
            }
        }
        // Erosión vertical.
        b.fill(false)
        for (y in 0 until alto) {
            for (x in 0 until ancho) {
                var todo = true
                for (dy in -RADIO_CIERRE..RADIO_CIERRE) {
                    val yy = y + dy
                    if (yy in 0 until alto && !a[yy * ancho + x]) {
                        todo = false
                        break
                    }
                }
                b[y * ancho + x] = todo
            }
        }
        return b
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

        /**
         * Radio (px) del cierre de huecos del contorno que usa el balde para
         * tolerar aperturas pequeñas del trazo: cierra huecos de hasta
         * 2*RADIO_CIERRE px (coincide con el C++).
         */
        const val RADIO_CIERRE = 3
    }
}
