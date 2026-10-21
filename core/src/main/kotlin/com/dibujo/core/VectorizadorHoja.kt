package com.dibujo.core

/**
 * Vectoriza un dibujo raster (la hoja) y lo escribe como **SVG**.
 *
 * Es el paso que convierte a Dibujo en una herramienta de ilustración: el
 * boceto se exporta como vectores que se editan, se escalan sin perder
 * nitidez y se usan en cualquier editor de dibujo.
 *
 * Cómo funciona (todo local, sin dependencias):
 *  1. **Máscara de tinta**: un píxel es tinta si se aleja del color de fondo
 *     más que [UMBRAL_TINTA].
 *  2. **Componentes**: las zonas de tinta conectadas (8 vecinos) se separan en
 *     componentes (cada una es un trazo o una figura).
 *  3. **Contorno**: se recorre el borde de cada componente (algoritmo de
 *     Moore) y, si la figura tiene agujeros (un círculo, por ejemplo), se
 *     recorren también.
 *  4. **Simplificación**: los vértices se pasan por Ramer-Douglas-Peucker para
 *     dejar la curva sin puntos de más.
 *  5. **SVG**: cada componente es un `<path>` relleno con su color; los
 *     agujeros se recortan con `fill-rule="evenodd"`.
 *
 * Es una operación de **imagen**, no de dibujo: no toca el motor y no aplica
 * la paridad C++/Kotlin (igual que el [Cuantizador] o los [FiltrosImagen]).
 */
object VectorizadorHoja {

    /** Diferencia mínima con el fondo (0..255 por canal) para que un píxel sea tinta. */
    const val UMBRAL_TINTA = 24

    /** Tolerancia por defecto de la simplificación (px de hoja). */
    const val TOLERANCIA = 1.2f

    /** Tope de caminos del SVG (protege de los dibujos con mucho ruido). */
    const val MAX_CAMINOS = 4000

    /**
     * Vectoriza la hoja ([pixeles] ARGB de [ancho] × [alto]) y devuelve el
     * SVG como texto. Devuelve `null` si no hay nada que vectorizar (hoja en
     * blanco) o si el buffer no corresponde al tamaño.
     *
     * [fondo] es el color que se toma como "papel" (los píxeles iguales se
     * ignoran) y [tolerancia] el margen de simplificación.
     */
    fun aSvg(
        pixeles: IntArray,
        ancho: Int,
        alto: Int,
        fondo: Int = Colores.BLANCO,
        tolerancia: Float = TOLERANCIA,
    ): String? {
        if (ancho <= 0 || alto <= 0 || pixeles.size < ancho * alto) return null
        val tinta = mascaraTinta(pixeles, ancho, alto, fondo)
        val componentes = componentes(tinta, ancho, alto)
        if (componentes.isEmpty()) return null

        val svg = StringBuilder()
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(ancho)
            .append("\" height=\"").append(alto)
            .append("\" viewBox=\"0 0 ").append(ancho).append(' ').append(alto)
            .append("\">\n")
        svg.append("<rect width=\"").append(ancho).append("\" height=\"").append(alto)
            .append("\" fill=\"").append(colorHex(fondo)).append("\"/>\n")

        var caminos = 0
        for (componente in componentes) {
            if (caminos >= MAX_CAMINOS) break
            if (componente.size < 4) continue
            val contorno = trazarContorno(tinta, ancho, alto, componente[0])
            if (contorno.size < 3) continue
            val d = caminoSvg(tinta, ancho, alto, contorno, componente, tolerancia)
            if (d.isEmpty()) continue
            val color = colorDominante(pixeles, ancho, componente)
            svg.append("<path d=\"").append(d).append("\" fill=\"").append(color)
                .append("\" fill-rule=\"evenodd\"/>\n")
            caminos++
        }
        if (caminos == 0) return null
        svg.append("</svg>\n")
        return svg.toString()
    }

    // --- Pasos -------------------------------------------------------------

    /** Píxeles que se apartan del fondo: la "tinta" del dibujo. */
    private fun mascaraTinta(
        pixeles: IntArray,
        ancho: Int,
        alto: Int,
        fondo: Int,
    ): BooleanArray {
        val mascara = BooleanArray(ancho * alto)
        val fr = (fondo ushr 16) and 0xFF
        val fg = (fondo ushr 8) and 0xFF
        val fb = fondo and 0xFF
        val umbral2 = UMBRAL_TINTA * UMBRAL_TINTA
        for (i in 0 until ancho * alto) {
            val p = pixeles[i]
            // Lo transparente cuenta como papel.
            if ((p ushr 24) < 128) continue
            val dr = ((p ushr 16) and 0xFF) - fr
            val dg = ((p ushr 8) and 0xFF) - fg
            val db = (p and 0xFF) - fb
            mascara[i] = dr * dr + dg * dg + db * db > umbral2
        }
        return mascara
    }

    /**
     * Zonas de tinta conectadas (8 vecinos). Cada componente es un `IntArray`
     * con los índices de sus píxeles y empieza en su píxel más arriba y a la
     * izquierda (el que sirve para trazar el borde).
     */
    private fun componentes(mascara: BooleanArray, ancho: Int, alto: Int): List<IntArray> {
        val total = ancho * alto
        val visitados = BooleanArray(total)
        val pila = IntArray(total)
        val lista = ArrayList<IntArray>()
        for (inicio in 0 until total) {
            if (!mascara[inicio] || visitados[inicio]) continue
            visitados[inicio] = true
            var tope = 0
            pila[tope++] = inicio
            val pixeles = ArrayList<Int>(64)
            while (tope > 0) {
                val actual = pila[--tope]
                pixeles.add(actual)
                val x = actual % ancho
                val y = actual / ancho
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= ancho || ny >= alto) continue
                        val i = ny * ancho + nx
                        if (mascara[i] && !visitados[i]) {
                            visitados[i] = true
                            pila[tope++] = i
                        }
                    }
                }
            }
            lista.add(pixeles.toIntArray())
        }
        return lista
    }

    /**
     * Recorre el borde de una zona de tinta (algoritmo de Moore con criterio
     * de terminación de Jacob). Devuelve los índices de los píxeles del
     * contorno, en orden.
     */
    private fun trazarContorno(
        mascara: BooleanArray,
        ancho: Int,
        alto: Int,
        inicio: Int,
    ): IntArray {
        val sx = inicio % ancho
        val sy = inicio / ancho
        val puntos = ArrayList<Int>(64)
        var px = sx
        var py = sy
        // El píxel de fondo por el que se "entra" al trazo: el vecino al
        // oeste. El inicio es el más a la izquierda de la fila más alta de la
        // componente, así que a su izquierda no hay tinta.
        var bIdx = 6
        val bInicial = bIdx
        val maxVueltas = 8 * (ancho * alto) + 16
        var vueltas = 0
        do {
            puntos.add(py * ancho + px)
            var encontrado = false
            for (k in 0 until 8) {
                val i = (bIdx + k) % 8
                val nx = px + VECINOS[i][0]
                val ny = py + VECINOS[i][1]
                if (nx < 0 || ny < 0 || nx >= ancho || ny >= alto) continue
                if (!mascara[ny * ancho + nx]) continue
                // El nuevo fondo de entrada es el vecino anterior en el barrido.
                val previo = (i + 7) % 8
                val dbx = VECINOS[previo][0] - VECINOS[i][0]
                val dby = VECINOS[previo][1] - VECINOS[i][1]
                bIdx = indiceVecino(dbx, dby)
                px = nx
                py = ny
                encontrado = true
                break
            }
            if (!encontrado) break
            vueltas++
            if (vueltas > maxVueltas) break
        } while (px != sx || py != sy || bIdx != bInicial)
        return puntos.toIntArray()
    }

    /**
     * Agujeros de una componente: zonas de papel encerradas por la tinta (el
     * interior de un círculo, por ejemplo). Cada agujero es una lista de
     * índices de píxel.
     */
    private fun agujeros(
        mascara: BooleanArray,
        ancho: Int,
        alto: Int,
        componente: IntArray,
    ): List<IntArray> {
        var x0 = ancho
        var y0 = alto
        var x1 = -1
        var y1 = -1
        for (i in componente) {
            val x = i % ancho
            val y = i / ancho
            if (x < x0) x0 = x
            if (x > x1) x1 = x
            if (y < y0) y0 = y
            if (y > y1) y1 = y
        }
        if (x1 < x0 || y1 < y0) return emptyList()
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        // Local: `true` es papel.
        val papel = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                papel[y * w + x] = !mascara[(y + y0) * ancho + (x + x0)]
            }
        }
        // El papel que toca el borde de la caja NO es un agujero: está
        // conectado con el exterior.
        val alcanzado = BooleanArray(w * h)
        val pila = IntArray(w * h)
        var tope = 0
        fun sembrar(indice: Int) {
            if (papel[indice] && !alcanzado[indice]) {
                alcanzado[indice] = true
                pila[tope++] = indice
            }
        }
        for (x in 0 until w) {
            sembrar(x)
            sembrar((h - 1) * w + x)
        }
        for (y in 0 until h) {
            sembrar(y * w)
            sembrar(y * w + w - 1)
        }
        while (tope > 0) {
            val actual = pila[--tope]
            val x = actual % w
            val y = actual / w
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val i = ny * w + nx
                    if (papel[i] && !alcanzado[i]) {
                        alcanzado[i] = true
                        pila[tope++] = i
                    }
                }
            }
        }
        // El papel no alcanzado son los agujeros.
        val lista = ArrayList<IntArray>()
        val visto = BooleanArray(w * h)
        for (inicio in 0 until w * h) {
            if (!papel[inicio] || alcanzado[inicio] || visto[inicio]) continue
            val region = ArrayList<Int>(16)
            var tope2 = 0
            pila[tope2++] = inicio
            visto[inicio] = true
            while (tope2 > 0) {
                val actual = pila[--tope2]
                region.add(actual)
                val x = actual % w
                val y = actual / w
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                        val i = ny * w + nx
                        if (papel[i] && !visto[i]) {
                            visto[i] = true
                            pila[tope2++] = i
                        }
                    }
                }
            }
            lista.add(IntArray(region.size) { region[it] + y0 * ancho + x0 })
        }
        return lista
    }

    /**
     * Convierte un contorno (índices de píxel) en el atributo `d` de un
     * `<path>`: puntos simplificados y cerrados. Si la figura tiene agujeros,
     * se suman como sub-trazos (con `fill-rule="evenodd"` se recortan).
     */
    private fun caminoSvg(
        tinta: BooleanArray,
        ancho: Int,
        alto: Int,
        contorno: IntArray,
        componente: IntArray,
        tolerancia: Float,
    ): String {
        if (contorno.size < 3) return ""
        val sb = StringBuilder()
        sb.append(subCamino(contorno, ancho, tolerancia))
        for (agujero in agujeros(tinta, ancho, alto, componente)) {
            // El agujero se traza con su propia máscara local (sus píxeles
            // son "tinta") para poder recorrer su borde.
            val local = mascaraLocal(agujero, ancho, alto) ?: continue
            var inicio = -1
            for (i in local.mascara.indices) {
                if (local.mascara[i]) {
                    inicio = i
                    break
                }
            }
            if (inicio < 0) continue
            val borde = trazarContorno(local.mascara, local.ancho, local.alto, inicio)
            if (borde.size < 3) continue
            sb.append(
                subCamino(
                    borde,
                    local.ancho,
                    tolerancia,
                    local.desplazamientoX,
                    local.desplazamientoY,
                ),
            )
        }
        return sb.toString()
    }

    /**
     * Sub-trazado cerrado de un contorno (índices de píxel) ya simplificado.
     * Los desplazamientos suman a las coordenadas (para los agujeros, que se
     * trazan en coordenadas locales).
     */
    private fun subCamino(
        contorno: IntArray,
        ancho: Int,
        tolerancia: Float,
        desplazamientoX: Int = 0,
        desplazamientoY: Int = 0,
    ): String {
        if (contorno.size < 3) return ""
        val puntos = ArrayList<Float>(contorno.size * 2)
        for (i in contorno) {
            puntos.add((i % ancho + desplazamientoX).toFloat())
            puntos.add((i / ancho + desplazamientoY).toFloat())
        }
        val simplificados = RamerDouglasPeucker.simplificar(puntos, tolerancia)
        if (simplificados.size < 6) return ""
        val sb = StringBuilder()
        sb.append('M')
        sb.append(simplificados[0].toInt()).append(' ')
        sb.append(simplificados[1].toInt())
        var i = 2
        while (i + 1 < simplificados.size) {
            sb.append(" L").append(simplificados[i].toInt()).append(' ')
            sb.append(simplificados[i + 1].toInt())
            i += 2
        }
        sb.append(" Z")
        return sb.toString()
    }

    /** Máscara local de un agujero: `true` en sus píxeles. */
    private fun mascaraLocal(
        agujero: IntArray,
        ancho: Int,
        alto: Int,
    ): MascaraLocal? {
        if (agujero.isEmpty()) return null
        var x0 = ancho
        var y0 = alto
        var x1 = -1
        var y1 = -1
        for (i in agujero) {
            val x = i % ancho
            val y = i / ancho
            if (x < x0) x0 = x
            if (x > x1) x1 = x
            if (y < y0) y0 = y
            if (y > y1) y1 = y
        }
        if (x1 < x0 || y1 < y0) return null
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        val mascara = BooleanArray(w * h)
        for (i in agujero) {
            val x = i % ancho
            val y = i / ancho
            mascara[(y - y0) * w + (x - x0)] = true
        }
        return MascaraLocal(mascara, w, h, x0, y0)
    }

    /** Máscara local con su desplazamiento a coordenadas globales. */
    private class MascaraLocal(
        val mascara: BooleanArray,
        val ancho: Int,
        val alto: Int,
        val desplazamientoX: Int,
        val desplazamientoY: Int,
    )

    /**
     * Color de una componente: el color de tinta más frecuente (los bordes
     * con antialiasing quedan en tonos distintos y no ganan).
     */
    private fun colorDominante(pixeles: IntArray, ancho: Int, componente: IntArray): String {
        val cuenta = HashMap<Int, Int>()
        var mejor = 0
        var mejorColor = Colores.NEGRO
        for (i in componente) {
            val p = pixeles[i]
            val c = cuenta[p] ?: 0
            cuenta[p] = c + 1
            if (c + 1 > mejor) {
                mejor = c + 1
                mejorColor = p
            }
        }
        return colorHex(mejorColor)
    }

    /** Color ARGB a `#rrggbb` (sin el alfa). */
    private fun colorHex(argb: Int): String {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return "#" + hex2(r) + hex2(g) + hex2(b)
    }

    /** Dos dígitos hexadecimales de un valor 0..255. */
    private fun hex2(valor: Int): String {
        val hex = Integer.toHexString(valor and 0xFF)
        return if (hex.length == 1) "0$hex" else hex
    }

    /**
     * Vecinos en sentido horario (la y crece hacia abajo): N, NE, E, SE, S,
     * SO, O, NO.
     */
    private val VECINOS = arrayOf(
        intArrayOf(0, -1),
        intArrayOf(1, -1),
        intArrayOf(1, 0),
        intArrayOf(1, 1),
        intArrayOf(0, 1),
        intArrayOf(-1, 1),
        intArrayOf(-1, 0),
        intArrayOf(-1, -1),
    )

    /** Posición de un vecino en la tabla. */
    private fun indiceVecino(dx: Int, dy: Int): Int {
        for (i in VECINOS.indices) {
            if (VECINOS[i][0] == dx && VECINOS[i][1] == dy) return i
        }
        return 0
    }
}
