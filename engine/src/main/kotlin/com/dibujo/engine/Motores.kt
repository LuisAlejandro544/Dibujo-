package com.dibujo.engine

import com.dibujo.core.MotorDibujo

/**
 * Fábrica de motores: usa el motor C++ si la librería está disponible y
 * si no, el respaldo puro Kotlin. La UI nunca sabe la diferencia.
 */
object Motores {
    fun crear(ancho: Int, alto: Int): MotorDibujo {
        return if (NativeEngine.disponible()) {
            try {
                MotorNativo(ancho, alto)
            } catch (_: IllegalStateException) {
                MotorKotlin(ancho, alto)
            }
        } else {
            MotorKotlin(ancho, alto)
        }
    }
}
