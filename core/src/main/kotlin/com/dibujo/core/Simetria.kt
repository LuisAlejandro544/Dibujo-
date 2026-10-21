package com.dibujo.core

/**
 * Simetría del trazo: el motor dibuja el trazo **y sus reflejos** en la hoja.
 *
 * Los reflejos son respecto del centro de la hoja:
 *  - [VERTICAL]: también del lado derecho (espejo izquierdo/derecho).
 *  - [HORIZONTAL]: también hacia abajo (espejo arriba/abajo).
 *  - [CUADRADA]: las tres copias (derecha, abajo y la diagonal).
 *
 * El motor los dibuja al **terminar** el trazo (no mientras): así el gesto es
 * un solo paso del historial y se deshace de una vez. La aritmética del
 * reflejo es una resta (sin trigonometría) para que C++ y Kotlin den el
 * mismo resultado píxel a píxel.
 */
enum class Simetria {
    /** Sin simetría: el trazo se dibuja una sola vez. */
    NINGUNA,

    /** Espejo respecto del eje vertical (izquierda/derecha). */
    VERTICAL,

    /** Espejo respecto del eje horizontal (arriba/abajo). */
    HORIZONTAL,

    /** Espejo en los dos ejes (las cuatro copias). */
    CUADRADA,
}
