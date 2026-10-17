package com.dibujo.engine

/**
 * Declaraciones nativas del motor C++ (`libdibujo_engine`).
 *
 * El peer es un puntero crudo (jlong) a `dibujo::DibujoEngine`, gestionado
 * con [nativoCrear]/[nativoDestruir]. Ver `engine/src/main/cpp/`.
 */
object NativeEngine {

    private val cargada: Boolean by lazy { intentarCargar() }

    /** Carga la librería nativa una sola vez. Devuelve true si está disponible. */
    fun disponible(): Boolean = cargada

    external fun nativoCrear(ancho: Int, alto: Int): Long
    external fun nativoDestruir(handle: Long)
    external fun nativoConfigurarLapiz(handle: Long, color: Int, grosor: Int, puntaRedonda: Boolean, herramienta: Int)
    external fun nativoIniciarTrazo(handle: Long, x: Float, y: Float)
    external fun nativoContinuarTrazo(handle: Long, x: Float, y: Float)
    external fun nativoTerminarTrazo(handle: Long): Boolean
    external fun nativoCancelarTrazo(handle: Long)
    external fun nativoDeshacer(handle: Long): Boolean
    external fun nativoRehacer(handle: Long): Boolean
    external fun nativoPuedeDeshacer(handle: Long): Boolean
    external fun nativoPuedeRehacer(handle: Long): Boolean
    external fun nativoLimpiarHoja(handle: Long)
    external fun nativoExpandir(handle: Long, izquierda: Int, arriba: Int, derecha: Int, abajo: Int)
    external fun nativoAncho(handle: Long): Int
    external fun nativoAlto(handle: Long): Int
    external fun nativoPan(handle: Long, dx: Float, dy: Float)
    external fun nativoZoom(handle: Long, factor: Float)
    external fun nativoCentrarVista(handle: Long)
    external fun nativoRenderizar(handle: Long, destino: IntArray, vw: Int, vh: Int)
    external fun nativoVersion(): String

    // --- Render GPU (Android) ---
    // El peer GPU lee la hoja y la cámara del motor `motorHandle`. Se crea con
    // la `Surface` del `SurfaceView` y el backend pedido (ordinal de
    // `BackendRender`); devuelve 0 si el backend no está disponible.

    /** Crea el renderizador GPU; devuelve su peer o 0 si no fue posible. */
    external fun nativoGpuCrear(motorHandle: Long, surface: Any, backend: Int): Long

    /** Dibuja y presenta un cuadro; false si el contexto se perdió. */
    external fun nativoGpuRender(gpu: Long): Boolean

    /** Notifica un cambio de tamaño del backbuffer en px. */
    external fun nativoGpuRedimensionar(gpu: Long, ancho: Int, alto: Int)

    /** Descripción del renderizador y del equipo (diagnóstico). */
    external fun nativoGpuNombre(gpu: Long): String

    /** Libera el renderizador GPU y la referencia a la ventana nativa. */
    external fun nativoGpuDestruir(gpu: Long)

    private fun intentarCargar(): Boolean {
        try {
            System.loadLibrary("dibujo_engine")
            return true
        } catch (_: UnsatisfiedLinkError) {
            // sigue: probamos rutas conocidas del build de Gradle
        }
        val nombre = System.mapLibraryName("dibujo_engine")
        val rutas = listOf(
            "build/cmake/lib/$nombre",
            "engine/build/cmake/lib/$nombre",
            "../engine/build/cmake/lib/$nombre",
        )
        for (ruta in rutas) {
            val f = java.io.File(ruta)
            if (f.isFile) {
                try {
                    System.load(f.absolutePath)
                    return true
                } catch (_: UnsatisfiedLinkError) {
                    // siguiente ruta
                }
            }
        }
        return false
    }
}
