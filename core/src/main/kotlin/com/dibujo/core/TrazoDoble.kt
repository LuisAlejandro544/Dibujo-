package com.dibujo.core

/**
 * Herramienta que dibuja el trazo acompañante ("la doble") de la herramienta
 * [Herramienta.DOBLE].
 *
 * La herramienta DOBLE dibuja **dos** trazos con un solo gesto: el principal
 * (con el lápiz elegido) y el acompañante (el "doble"), que se puede pintar
 * con otra herramienta y otro color (el *color de sombra*).
 *
 * El acompañante va **al revés** del gesto (nace donde termina el trazo y
 * vuelve hacia donde empezaste) y desplazado unos píxeles hacia el lado
 * elegido: es automático, no se configura el sentido.
 *
 * El orden de los valores viaja por JNI hasta el enum `ToolDoble` del C++, así
 * que hay que agregarlos al final en los dos lados.
 */
enum class HerramientaDoble(val herramienta: Herramienta) {
    /** Lápiz clásico: el doble se ve igual que el principal. */
    LAPIZ(Herramienta.LAPIZ),

    /** Trazo fino: el doble sale más delgado que el principal. */
    PORTAMINA(Herramienta.PORTAMINA),

    /** Trazo grueso y suave: el doble sale más ancho (sirve de sombra). */
    CARBONCILLO(Herramienta.CARBONCILLO),

    /**
     * Pincel vectorial: el doble **no** lo dibuja el motor (los trazos
     * vectoriales viven en la app, como objetos editables) sino la capa de
     * vectores, con la misma ruta desplazada.
     */
    PLUMA(Herramienta.PLUMA),
}

/**
 * Lado donde se dibuja el trazo acompañante de la herramienta DOBLE.
 *
 * El acompañante **siempre va al revés** del gesto (nace donde termina el
 * trazo y vuelve hacia donde empezó), así que el lado se mide respecto del
 * propio acompañante: si el doble va hacia la izquierda, su izquierda es la
 * de ese recorrido, no la del gesto original.
 *
 * El orden de los valores viaja por JNI hasta el enum `LadoDoble` del C++, así
 * que hay que agregarlos al final en los dos lados.
 */
enum class LadoDoble {
    /**
     * El doble se dibuja **encima** del trazo principal (mismo lugar). Queda
     * debajo, así que se ve como una sombra o un contorno cuando es más grueso
     * o de otro color.
     */
    MISMO,

    /** El doble se dibuja a la **izquierda** del sentido en que se dibuja. */
    IZQUIERDA,

    /** El doble se dibuja a la **derecha** del sentido en que se dibuja. */
    DERECHA,
}

/**
 * Geometría del trazo doble: la polilínea acompañante del gesto.
 *
 * Es la **única** implementación (la usan el motor Kotlin, el motor C++ —que
 * la replica— y la capa de vectores de la app), así el doble se ve igual en
 * todas partes.
 */
object TrazoDoble {

    /**
     * Separación entre el trazo principal y el acompañante (px de hoja).
     * Crece con el grosor para que el hueco entre ambos sea visible.
     * El motor C++ replica esta fórmula (paridad).
     */
    fun separacion(grosor: Int): Float = (grosor + 2) * 0.5f

    /**
     * Devuelve la polilínea del trazo acompañante ("la doble").
     *
     * El acompañante va **al revés** del gesto: nace donde termina el trazo
     * principal y vuelve hacia donde empezaste (si dibujás hacia la derecha,
     * el doble va hacia la izquierda). Es automático: no se configura.
     *
     * Además se dibuja desplazado [separacion] píxeles hacia el [lado]
     * elegido. Como el recorrido está invertido, el lado se mide respecto del
     * propio acompañante (su izquierda, no la del gesto).
     *
     * [puntos] son coordenadas intercaladas (x, y) y [cantidad] la cantidad de
     * floats usados (2 por punto). La normal se suaviza por vértice (se usa la
     * dirección de la cuerda que une los vecinos), así las dos líneas no
     * tiemblan en los empalmes.
     *
     * Con [LadoDoble.MISMO] el acompañante se dibuja encima del principal
     * (pero igual al revés).
     */
    fun acompanante(
        puntos: FloatArray,
        cantidad: Int,
        separacion: Float,
        lado: LadoDoble,
    ): FloatArray {
        val salida = FloatArray(cantidad)
        val vertices = cantidad / 2
        // Con un solo vértice no hay dirección: no hay nada que invertir.
        if (vertices < 2) {
            for (i in 0 until cantidad) salida[i] = puntos[i]
            return salida
        }
        // El acompañante va al revés: el vértice i del gesto ocupa el lugar
        // (vertices - 1 - i) de la polilínea acompañante.
        if (lado == LadoDoble.MISMO || separacion == 0f) {
            for (i in 0 until vertices) {
                val base = i * 2
                val destino = (vertices - 1 - i) * 2
                salida[destino] = puntos[base]
                salida[destino + 1] = puntos[base + 1]
            }
            return salida
        }
        // La normal (-dy, dx) apunta a la derecha del sentido del trazo (en
        // coordenadas de pantalla, con la y hacia abajo); la izquierda es la
        // opuesta. Como el acompañante va al revés, su normal es la opuesta
        // a la del gesto.
        val signo = if (lado == LadoDoble.IZQUIERDA) -1f else 1f
        for (i in 0 until vertices) {
            val base = i * 2
            val dx: Float
            val dy: Float
            if (i == 0) {
                dx = puntos[2] - puntos[0]
                dy = puntos[3] - puntos[1]
            } else if (i == vertices - 1) {
                dx = puntos[base] - puntos[base - 2]
                dy = puntos[base + 1] - puntos[base - 1]
            } else {
                dx = puntos[base + 2] - puntos[base - 2]
                dy = puntos[base + 3] - puntos[base - 1]
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
            val ox = -nx * separacion * signo
            val oy = -ny * separacion * signo
            val destino = (vertices - 1 - i) * 2
            salida[destino] = puntos[base] + ox
            salida[destino + 1] = puntos[base + 1] + oy
        }
        return salida
    }
}
