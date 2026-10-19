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

    /**
     * Activa o desactiva el **trazo asistido**: al terminar un trazo, su
     * recorrido se reemplaza por una recta entre el primer y el último punto.
     * Sirve para las líneas rectas dibujadas a mano alzada (si sale chueca, se
     * endereza). No afecta a la herramienta PINTAR. El C++ y Kotlin lo
     * implementan igual (paridad).
     */
    fun asistenciaRecta(activa: Boolean)

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
     * "Limpiar trazos": adelgaza la tinta de la hoja a un esqueleto de 1 px
     * (Zhang-Suen) y la vuelve a dibujar con ancho uniforme. Sirve cuando el
     * usuario repasa el mismo trazo muchas veces y la línea queda gruesa e
     * irregular. Devuelve `true` si cambió algo (queda en el historial para
     * poder deshacer). El C++ y Kotlin lo implementan idéntico (paridad).
     */
    fun limpiarTrazos(): Boolean

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
     * Transforma el dibujo dentro de la misma hoja: lo **voltea**
     * (horizontal/vertical), lo **mueve** [desplazamientoX]/[desplazamientoY] px
     * de hoja y lo **escala** [escala] alrededor del centro. Deja el estado
     * anterior en el historial (se puede deshacer). Devuelve `false` si no hay
     * nada que transformar. El C++ y Kotlin lo hacen idéntico (paridad).
     */
    fun transformarHoja(
        voltearHorizontal: Boolean,
        voltearVertical: Boolean,
        desplazamientoX: Float,
        desplazamientoY: Float,
        escala: Float,
    ): Boolean

    /**
     * Color ARGB de un píxel de la hoja (coordenadas de hoja, sin cámara). Si
     * (x, y) está fuera de la hoja devuelve el color de fondo. Lo usa el
     * cuentagotas de `:app`. El C++ lo lee bajo bloqueo.
     */
    fun pixelHoja(x: Int, y: Int): Int

    /**
     * Reemplaza la hoja COMPLETA por [pixeles] (ancho * alto ARGB) y deja el
     * estado anterior en el historial (se puede deshacer). La usan las
     * herramientas que procesan la hoja fuera del motor, como el limpiador de
     * trazos con modelo IA de `:app`. Devuelve `false` si [pixeles] no tiene el
     * tamaño exacto de la hoja (no toca nada en ese caso). El C++ y el respaldo
     * Kotlin lo implementan igual.
     */
    fun reemplazarHoja(pixeles: IntArray): Boolean

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
