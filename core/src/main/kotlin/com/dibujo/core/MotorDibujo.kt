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

    /**
     * Expande la hoja agregando píxeles nuevos (rellenos con el fondo) en cada
     * lado. El contenido existente conserva sus coordenadas de hoja. El tamaño
     * resultante se acota a `Lienzo.MAX_LADO` por lado. El historial se limpia
     * (los snapshots viejos tienen otras dimensiones).
     */
    fun expandir(izquierda: Int, arriba: Int, derecha: Int, abajo: Int)

    /** Cámara: la vista sobre la hoja (modo moverse libremente). */
    fun pan(dx: Float, dy: Float)
    fun zoom(factor: Float)

    /**
     * Gira la hoja [delta] radianes alrededor del origen de la hoja (como el
     * gesto de rotación de dos dedos de las apps de dibujo). La rotación forma
     * parte de la transformación de cámara:
     * `pantalla = R(rot) * (hoja * zoom) − pan`.
     */
    fun rotar(delta: Float)
    fun centrarVista()
    fun camara(): Camara

    /** Rasteriza el viewport (vw x vh píxeles) en [destino] aplicando la cámara. */
    fun renderizar(destino: IntArray, vw: Int, vh: Int)

    /**
     * Copia la hoja COMPLETA (ancho * alto píxeles ARGB, sin cámara) a
     * [destino], para exportarla a imagen. [destino] debe tener al menos
     * `ancho * alto` celdas. Devuelve `true` si se copió. El C++ y el respaldo
     * Kotlin copian exactamente el mismo buffer (la hoja es la fuente de
     * verdad), así que no hay diferencias de rasterizado entre motores.
     */
    fun copiarHoja(destino: IntArray): Boolean

    /**
     * Handle nativo opaco del motor para el render GPU (Android). Es `0` en
     * motores que no exponen memoria nativa compartible (p. ej. `MotorKotlin`),
     * caso en el que la app cae al render por CPU.
     */
    val handleNativo: Long get() = 0L
}

/**
 * Estado de la cámara: pan (px de hoja), zoom y rotación (radianes).
 *
 * La transformación canonical es
 * `pantalla = R(rotacion) · (hoja · zoom) − (panX, panY)`, así que su inversa
 * es `hoja = R(−rotacion) · (pantalla + pan) / zoom`. Con `rotacion = 0`
 * coincide con la de siempre (`hoja = (pantalla + pan) / zoom`).
 */
data class Camara(
    val panX: Float,
    val panY: Float,
    val zoom: Float,
    val rotacion: Float = 0f,
)
