package com.dibujo.engine

import com.dibujo.core.Camara
import com.dibujo.core.Lapiz
import com.dibujo.core.MotorDibujo
import com.dibujo.core.Punta

/**
 * Motor de dibujo nativo (C++) conectado por JNI.
 *
 * La geometría pesada (rasterizado, suavizado, historial, render con
 * cámara) vive en C++; Kotlin solo orquesta y refleja el estado de la
 * cámara para que la UI pueda dibujar el cursor.
 */
class MotorNativo(
    anchoInicial: Int,
    altoInicial: Int,
) : MotorDibujo {

    private val handle: Long = NativeEngine.nativoCrear(anchoInicial, altoInicial)

    init {
        check(handle != 0L) { "no se pudo crear el motor nativo" }
    }

    override var ancho: Int = anchoInicial
        private set

    override var alto: Int = altoInicial
        private set

    override val nombre: String = "C++ nativo"

    /** Puntero crudo al `DibujoEngine`, compartido con el render GPU. */
    override val handleNativo: Long get() = handle

    // Buffer reutilizable para leer la cámara nativa (un solo hilo: la UI).
    private val bufferCamara = FloatArray(4)

    override fun version(): String =
        try { NativeEngine.nativoVersion() } catch (_: UnsatisfiedLinkError) { nombre }

    override fun configurarLapiz(lapiz: Lapiz) {
        NativeEngine.nativoConfigurarLapiz(
            handle,
            lapiz.color,
            lapiz.grosor,
            lapiz.punta == Punta.REDONDA,
            lapiz.herramienta.ordinal,
            lapiz.textura.ordinal,
        )
    }

    override fun asistenciaRecta(activa: Boolean) =
        NativeEngine.nativoAsistenciaRecta(handle, activa)

    override fun iniciarTrazo(x: Float, y: Float) = NativeEngine.nativoIniciarTrazo(handle, x, y)
    override fun continuarTrazo(x: Float, y: Float) = NativeEngine.nativoContinuarTrazo(handle, x, y)
    override fun terminarTrazo(): Boolean = NativeEngine.nativoTerminarTrazo(handle)
    override fun cancelarTrazo() = NativeEngine.nativoCancelarTrazo(handle)

    override fun deshacer(): Boolean = NativeEngine.nativoDeshacer(handle)
    override fun rehacer(): Boolean = NativeEngine.nativoRehacer(handle)
    override fun puedeDeshacer(): Boolean = NativeEngine.nativoPuedeDeshacer(handle)
    override fun puedeRehacer(): Boolean = NativeEngine.nativoPuedeRehacer(handle)
    override fun limpiarHoja() = NativeEngine.nativoLimpiarHoja(handle)

    override fun limiteHistorial(pasos: Int) =
        NativeEngine.nativoLimiteHistorial(handle, pasos)

    override fun limpiarTrazos(): Boolean = NativeEngine.nativoLimpiarTrazos(handle)

    override fun expandir(izquierda: Int, arriba: Int, derecha: Int, abajo: Int) {
        NativeEngine.nativoExpandir(handle, izquierda, arriba, derecha, abajo)
        ancho = NativeEngine.nativoAncho(handle)
        alto = NativeEngine.nativoAlto(handle)
    }

    override fun pan(dx: Float, dy: Float) = NativeEngine.nativoPan(handle, dx, dy)

    override fun zoom(factor: Float) = NativeEngine.nativoZoom(handle, factor)

    override fun rotar(delta: Float) = NativeEngine.nativoRotar(handle, delta)

    override fun centrarVista() = NativeEngine.nativoCentrarVista(handle)

    /**
     * Lee la cámara REAL del motor nativo (no una copia en Kotlin): antes se
     * llevaba un espejo que no replicaba el acotado de pan/zoom del C++, así
     * que se desincronizaba al hacer mucho zoom o al topar el pan y los trazos
     * caían en otro lado. Leerla del nativo garantiza que la UI convierta
     * pantalla↔hoja con exactamente la misma transformación que `renderizar`.
     */
    override fun camara(): Camara {
        NativeEngine.nativoLeerCamara(handle, bufferCamara)
        return Camara(bufferCamara[0], bufferCamara[1], bufferCamara[2], bufferCamara[3])
    }

    override fun renderizar(destino: IntArray, vw: Int, vh: Int) {
        NativeEngine.nativoRenderizar(handle, destino, vw, vh)
    }

    override fun copiarHoja(destino: IntArray): Boolean =
        NativeEngine.nativoCopiarHoja(handle, destino)

    override fun transformarHoja(
        voltearHorizontal: Boolean,
        voltearVertical: Boolean,
        desplazamientoX: Float,
        desplazamientoY: Float,
        escala: Float,
    ): Boolean = NativeEngine.nativoTransformar(
        handle, voltearHorizontal, voltearVertical, desplazamientoX, desplazamientoY, escala,
    )

    override fun reemplazarHoja(pixeles: IntArray): Boolean =
        NativeEngine.nativoReemplazarHoja(handle, pixeles)

    override fun pixelHoja(x: Int, y: Int): Int = NativeEngine.nativoPixelHoja(handle, x, y)

    override fun close() {
        NativeEngine.nativoDestruir(handle)
    }

    companion object {
        /** La librería C++ está lista para usarse. */
        fun disponible(): Boolean = NativeEngine.disponible()
    }
}
