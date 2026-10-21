package com.dibujo.core

/**
 * Estabilizador de mano: suaviza los puntos de entrada para que el temblor
 * natural de la mano no se note en el trazo.
 *
 * Es un **filtro exponencial**: cada punto nuevo se acerca al anterior sólo
 * una parte de la distancia que falta. Con [suavizado] en 0 el trazo sale tal
 * cual (sin estabilizar) y con 1 el trazo apenas se mueve.
 *
 * Los puntos se suavizan en **espacio de pantalla** (donde el temblor es
 * siempre del mismo tamaño) y recién después se convierten a coordenadas de
 * hoja: si se suavizaran en la hoja, al acercar la vista el trazo temblaría
 * más (el temblor de la mano también se amplía con el zoom).
 */
class EstabilizadorTrazo(suavizado: Float = 0f) {

    /** Suavizado aplicado (0 = sin estabilizar, 0,95 = muy suave). */
    var suavizado: Float = suavizado.coerceIn(0f, MAX_SUAVIZADO)
        set(valor) {
            field = valor.coerceIn(0f, MAX_SUAVIZADO)
        }

    /** Último punto suavizado (x de pantalla). */
    var x: Float = 0f
        private set

    /** Último punto suavizado (y de pantalla). */
    var y: Float = 0f
        private set

    private var iniciado = false

    /** Empieza un trazo nuevo: el primer punto pasa sin suavizar. */
    fun reiniciar() {
        iniciado = false
    }

    /**
     * Suaviza el punto de entrada ([px], [py] en pantalla) y deja el
     * resultado en [x] e [y].
     */
    fun estabilizar(px: Float, py: Float) {
        if (!iniciado) {
            x = px
            y = py
            iniciado = true
            return
        }
        val factor = 1f - suavizado
        x += (px - x) * factor
        y += (py - y) * factor
    }

    companion object {
        /** Tope del suavizado: con 1 el trazo nunca llega al dedo. */
        const val MAX_SUAVIZADO = 0.95f
    }
}
