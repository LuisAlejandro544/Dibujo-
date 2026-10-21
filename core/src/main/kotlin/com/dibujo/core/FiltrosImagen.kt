package com.dibujo.core

/**
 * Filtros que se pueden aplicar a la **imagen de calco** (la foto o el dibujo
 * que se pone debajo para calcar).
 *
 * Calcar una foto real sin ayuda es casi imposible: el ruido de color y los
 * degradados hacen que no se distinga la línea. Con estos filtros la imagen se
 * prepara para calcar (escala de grises, contraste, umbral, realce de bordes)
 * o se le saca el fondo para ver sólo la tinta.
 *
 * Son operaciones de **imagen**, no de dibujo: no tocan el motor ni la paridad
 * C++/Kotlin (igual que el [Cuantizador], que también es una utilidad local
 * de `:core`).
 */
enum class FiltroImagen {
    /** La imagen se ve tal cual (sin filtro). */
    NINGUNO,

    /** Escala de grises: se pierde el color y queda la luminancia. */
    GRIS,

    /** Contraste: separa más (o menos) los tonos claros de los oscuros. */
    CONTRASTE,

    /** Umbral: la imagen pasa a blanco y negro (línea y fondo). */
    UMBRAL,

    /** Invierte los tonos: el blanco queda negro y viceversa. */
    INVERTIR,

    /** Realce de bordes (Sobel): deja sólo el contorno de las formas. */
    BORDES,

    /** Quita el fondo: lo claro se vuelve transparente y queda la tinta. */
    SIN_FONDO,
}

/**
 * Filtros de imagen sobre buffers ARGB (una vez por píxel, en orden de
 * lectura). Cada filtro usa un parámetro con su propio rango; el valor por
 * defecto y el rango se piden con [valorPorDefecto] y [rango].
 */
object FiltrosImagen {

    /**
     * Aplica [filtro] a [pixeles] (ARGB, de [ancho] × [alto]) y devuelve un
     * **buffer nuevo** con el resultado; el original no se toca. [parametro] es
     * el ajuste del filtro (umbral, contraste o sensibilidad, según el
     * filtro); si se pasa uno fuera de rango se recorta al rango válido.
     */
    fun aplicar(
        pixeles: IntArray,
        ancho: Int,
        alto: Int,
        filtro: FiltroImagen,
        parametro: Int,
    ): IntArray {
        if (ancho <= 0 || alto <= 0 || pixeles.size < ancho * alto) return IntArray(0)
        if (filtro == FiltroImagen.NINGUNO) return pixeles.copyOf()
        val salida = IntArray(ancho * alto)
        when (filtro) {
            FiltroImagen.NINGUNO -> Unit

            FiltroImagen.GRIS -> {
                for (i in pixeles.indices) {
                    val p = pixeles[i]
                    val y = luminancia(p)
                    salida[i] = (p and ALFA) or (y shl 16) or (y shl 8) or y
                }
            }

            FiltroImagen.CONTRASTE -> {
                val factor = 1.0 + parametro.coerceIn(-100, 100) / 100.0
                for (i in pixeles.indices) {
                    val p = pixeles[i]
                    val r = ajustarContraste((p ushr 16) and 0xFF, factor)
                    val g = ajustarContraste((p ushr 8) and 0xFF, factor)
                    val b = ajustarContraste(p and 0xFF, factor)
                    salida[i] = (p and ALFA) or (r shl 16) or (g shl 8) or b
                }
            }

            FiltroImagen.UMBRAL -> {
                val umbral = parametro.coerceIn(0, 255)
                for (i in pixeles.indices) {
                    val y = luminancia(pixeles[i])
                    // Lo que está por debajo del umbral es tinta (negro); lo de
                    // arriba queda como fondo (blanco).
                    val v = if (y >= umbral) 255 else 0
                    salida[i] = OPAQUO or (v shl 16) or (v shl 8) or v
                }
            }

            FiltroImagen.INVERTIR -> {
                for (i in pixeles.indices) {
                    val p = pixeles[i]
                    val r = 255 - ((p ushr 16) and 0xFF)
                    val g = 255 - ((p ushr 8) and 0xFF)
                    val b = 255 - (p and 0xFF)
                    salida[i] = (p and ALFA) or (r shl 16) or (g shl 8) or b
                }
            }

            FiltroImagen.BORDES -> {
                // Sobel: la magnitud del gradiente marca el contorno. Se dibuja
                // oscuro sobre blanco, que es lo que se quiere calcar.
                val sensibilidad = parametro.coerceIn(1, 32)
                salida.fill(OPAQUO or 0x00FFFFFF)
                for (y in 1 until alto - 1) {
                    for (x in 1 until ancho - 1) {
                        val i = y * ancho + x
                        val tl = luminancia(pixeles[i - ancho - 1])
                        val t = luminancia(pixeles[i - ancho])
                        val tr = luminancia(pixeles[i - ancho + 1])
                        val l = luminancia(pixeles[i - 1])
                        val r = luminancia(pixeles[i + 1])
                        val bl = luminancia(pixeles[i + ancho - 1])
                        val b = luminancia(pixeles[i + ancho])
                        val br = luminancia(pixeles[i + ancho + 1])
                        val gx = -tl - 2 * l - bl + tr + 2 * r + br
                        val gy = -tl - 2 * t - tr + bl + 2 * b + br
                        val magnitud = (kotlin.math.abs(gx) + kotlin.math.abs(gy)) / sensibilidad
                        val v = (255 - magnitud).coerceIn(0, 255)
                        salida[i] = OPAQUO or (v shl 16) or (v shl 8) or v
                    }
                }
            }

            FiltroImagen.SIN_FONDO -> {
                val umbral = parametro.coerceIn(1, 255)
                for (i in pixeles.indices) {
                    val p = pixeles[i]
                    val y = luminancia(p)
                    // Alfa suave: la tinta queda opaca y el fondo se va.
                    val alfa = (255 * (umbral - y) / umbral).coerceIn(0, 255)
                    salida[i] = (alfa shl 24) or (p and 0x00FFFFFF)
                }
            }
        }
        return salida
    }

    /** Ajuste por defecto del filtro (el que se usa al activarlo). */
    fun valorPorDefecto(filtro: FiltroImagen): Int = when (filtro) {
        FiltroImagen.NINGUNO, FiltroImagen.GRIS, FiltroImagen.INVERTIR -> 0
        FiltroImagen.CONTRASTE -> 40
        FiltroImagen.UMBRAL -> 140
        FiltroImagen.BORDES -> 8
        FiltroImagen.SIN_FONDO -> 235
    }

    /** Rango válido del ajuste del filtro (para los deslizadores). */
    fun rango(filtro: FiltroImagen): IntRange = when (filtro) {
        FiltroImagen.NINGUNO, FiltroImagen.GRIS, FiltroImagen.INVERTIR -> 0..0
        FiltroImagen.CONTRASTE -> -100..100
        FiltroImagen.UMBRAL -> 0..255
        FiltroImagen.BORDES -> 1..32
        FiltroImagen.SIN_FONDO -> 1..255
    }

    /** Luminancia perceptual (0..255) de un píxel ARGB. */
    fun luminancia(pixel: Int): Int {
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /** Ajusta el contraste de un canal alrededor del gris medio (128). */
    private fun ajustarContraste(valor: Int, factor: Double): Int {
        val ajustado = 128.0 + (valor - 128.0) * factor
        return kotlin.math.floor(ajustado + 0.5).toInt().coerceIn(0, 255)
    }

    /** Alfa opaco (0xFF000000). */
    private const val ALFA = 0xFF000000.toInt()

    /** Pixel opaco blanco (0xFFFFFFFF). */
    private const val OPAQUO = 0xFF000000.toInt()
}
