package com.dibujo.ui

import com.dibujo.engine.Motores

/**
 * Punto de entrada de la app de consola.
 *
 * La hoja es más grande que el viewport para que el modo MOVER tenga
 * sentido: se puede recorrer libremente con pan/zoom.
 */
fun main(args: Array<String>) {
    val anchoHoja = 80
    val altoHoja = 40
    val vw = 40
    val vh = 20
    // Color solo si es una terminal interactiva (no en tuberías).
    val conColor = System.console() != null && args.none { it == "--no-color" }

    println("=== Dibujo === hoja ${anchoHoja}x${altoHoja}, viewport ${vw}x${vh} (h para ayuda)")
    val motor = Motores.crear(anchoHoja, altoHoja)
    val controlador = ControladorConsola(motor, vw, vh, conColor)
    controlador.ejecutar(generateSequence { readlnOrNull() }.iterator(), System.out)
    motor.close()
}
