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
    external fun nativoPan(handle: Long, dx: Float, dy: Float)
    external fun nativoZoom(handle: Long, factor: Float)
    external fun nativoCentrarVista(handle: Long)
    external fun nativoRenderizar(handle: Long, destino: IntArray, vw: Int, vh: Int)
    external fun nativoVersion(): String

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
