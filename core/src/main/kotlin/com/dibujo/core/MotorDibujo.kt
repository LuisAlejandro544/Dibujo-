package com.dibujo.core

/**
 * Fachada del motor de dibujo. La UI solo conoce esta interfaz.
 *
 * Dos implementaciones:
 *  - `engine/MotorNativo`: núcleo C++ vía JNI (preferida).
 *  - `engine/MotorKotlin`: respaldo puro Kotlin con la misma lógica.
 */
interface MotorDibujo : AutoCloseable {
    val ancho: Int
    val alto: Int

    /** Nombre del motor (para la barra de estado). */
    val nombre: String

    fun version(): String = nombre

    fun configurarLapiz(lapiz: Lapiz)

    /** Trazo en curso, en coordenadas de hoja. */
    fun iniciarTrazo(x: Float, y: Float)
    fun continuarTrazo(x: Float, y: Float)
    fun terminarTrazo(): Boolean
    fun cancelarTrazo()

    fun deshacer(): Boolean
    fun rehacer(): Boolean
    fun puedeDeshacer(): Boolean
    fun puedeRehacer(): Boolean
    fun limpiarHoja()

    /** Cámara: la vista sobre la hoja (modo moverse libremente). */
    fun pan(dx: Float, dy: Float)
    fun zoom(factor: Float)
    fun centrarVista()
    fun camara(): Camara

    /** Rasteriza el viewport (vw x vh píxeles) en [destino] aplicando la cámara. */
    fun renderizar(destino: IntArray, vw: Int, vh: Int)
}

/** Estado de la cámara: pan (px de hoja) y zoom. */
data class Camara(val panX: Float, val panY: Float, val zoom: Float)
