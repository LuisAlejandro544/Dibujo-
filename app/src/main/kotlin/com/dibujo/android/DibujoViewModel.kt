package com.dibujo.android

import androidx.lifecycle.ViewModel
import com.dibujo.core.Camara
import com.dibujo.core.Herramienta
import com.dibujo.core.Lapiz
import com.dibujo.core.Modo
import com.dibujo.core.MotorDibujo
import com.dibujo.engine.Motores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Estado de la pantalla de dibujo (inmutable, se actualiza con copy). */
data class DibujoEstado(
    val lapiz: Lapiz = Lapiz(),
    val modo: Modo = Modo.EDICION,
    val camara: Camara = Camara(0f, 0f, 1f),
    /** Trazo en curso, pares (x, y) en coordenadas de hoja; null si no hay. */
    val trazoEnCurso: List<Float>? = null,
    /** Marco de render: cambia con cada operación para invalidar el lienzo. */
    val marco: Int = 0,
    val motorNombre: String = "",
)

/**
 * ViewModel de la app de dibujo: dueño del [MotorDibujo].
 *
 * El motor vive fuera de la composición, así la hoja sobrevive rotaciones y
 * cambios de configuración. Todos los eventos táctiles llegan en píxeles de
 * pantalla y se convierten a coordenadas de hoja con la cámara del motor:
 * hoja = (pantalla + pan) / zoom, la misma transformación de
 * `MotorDibujo.renderizar`.
 */
class DibujoViewModel : ViewModel() {

    private var motor: MotorDibujo? = null
    private val _estado = MutableStateFlow(DibujoEstado())
    val estado: StateFlow<DibujoEstado> = _estado.asStateFlow()

    /** Crea el motor una sola vez con el tamaño físico de la pantalla (px). */
    fun inicializar(vw: Int, vh: Int) {
        if (vw <= 0 || vh <= 0 || motor != null) return
        val m = Motores.crear(vw.coerceIn(1, MAX_LADO), vh.coerceIn(1, MAX_LADO))
        motor = m
        m.configurarLapiz(_estado.value.lapiz)
        _estado.update { it.copy(motorNombre = m.nombre) }
    }

    fun iniciarTrazo(px: Float, py: Float) {
        val m = motor ?: return
        if (_estado.value.modo != Modo.EDICION) return
        val (hx, hy) = aHoja(m, px, py)
        m.iniciarTrazo(hx, hy)
        _estado.update { it.copy(trazoEnCurso = listOf(hx, hy), marco = it.marco + 1) }
    }

    fun continuarTrazo(px: Float, py: Float) {
        val m = motor ?: return
        if (_estado.value.trazoEnCurso == null) return
        val (hx, hy) = aHoja(m, px, py)
        m.continuarTrazo(hx, hy)
        _estado.update {
            it.copy(
                trazoEnCurso = it.trazoEnCurso.orEmpty() + hx + hy,
                marco = it.marco + 1,
            )
        }
    }

    fun soltarTrazo() {
        val m = motor ?: return
        m.terminarTrazo()
        _estado.update { it.copy(trazoEnCurso = null, marco = it.marco + 1) }
    }

    fun cancelarTrazo() {
        val m = motor ?: return
        m.cancelarTrazo()
        _estado.update { it.copy(trazoEnCurso = null, marco = it.marco + 1) }
    }

    /** Pan con el dedo: el contenido sigue al dedo (dx, dy en px de pantalla). */
    fun panear(dx: Float, dy: Float) {
        val m = motor ?: return
        if (_estado.value.modo != Modo.MOVER) return
        m.pan(-dx, -dy)
        _estado.update { it.copy(camara = m.camara(), marco = it.marco + 1) }
    }

    /** Pinch de zoom anclado al punto donde están los dedos. */
    fun zoomear(factor: Float, focoX: Float, focoY: Float) {
        val m = motor ?: return
        if (_estado.value.modo != Modo.MOVER) return
        val cam = m.camara()
        val hfx = (focoX + cam.panX) / cam.zoom
        val hfy = (focoY + cam.panY) / cam.zoom
        m.zoom(factor)
        val cam2 = m.camara()
        m.pan(hfx * cam2.zoom - focoX - cam2.panX, hfy * cam2.zoom - focoY - cam2.panY)
        _estado.update { it.copy(camara = m.camara(), marco = it.marco + 1) }
    }

    fun elegirColor(argb: Int) = actualizarLapiz { it.copy(color = argb) }

    fun elegirHerramienta(herramienta: Herramienta) =
        actualizarLapiz { it.copy(herramienta = herramienta) }

    fun cambiarGrosor(delta: Int) {
        val actual = _estado.value.lapiz.grosor
        val nuevo = (actual + delta).coerceIn(1, 64)
        if (nuevo == actual) return
        actualizarLapiz { it.copy(grosor = nuevo) }
    }

    fun alternarModo() {
        val m = motor ?: return
        m.cancelarTrazo()
        val nuevo = if (_estado.value.modo == Modo.EDICION) Modo.MOVER else Modo.EDICION
        _estado.update { it.copy(modo = nuevo, trazoEnCurso = null, marco = it.marco + 1) }
    }

    fun deshacer() { motor?.let { it.deshacer(); marco() } }

    fun rehacer() { motor?.let { it.rehacer(); marco() } }

    fun limpiar() { motor?.limpiarHoja(); marco() }

    fun centrar() {
        val m = motor ?: return
        m.centrarVista()
        _estado.update { it.copy(camara = m.camara(), marco = it.marco + 1) }
    }

    /** Rasteriza la vista (vw x vh px) con la cámara aplicada, para el lienzo. */
    fun renderizar(destino: IntArray, vw: Int, vh: Int) {
        motor?.renderizar(destino, vw, vh)
    }

    override fun onCleared() {
        motor?.close()
        motor = null
        super.onCleared()
    }

    private fun marco() {
        val m = motor ?: return
        _estado.update { it.copy(camara = m.camara(), marco = it.marco + 1) }
    }

    private fun actualizarLapiz(transformar: (Lapiz) -> Lapiz) {
        val m = motor ?: return
        val nuevo = transformar(_estado.value.lapiz)
        m.configurarLapiz(nuevo)
        _estado.update { it.copy(lapiz = nuevo) }
    }

    private fun aHoja(m: MotorDibujo, px: Float, py: Float): Pair<Float, Float> {
        val cam = m.camara()
        return (px + cam.panX) / cam.zoom to (py + cam.panY) / cam.zoom
    }

    private companion object {
        /** Coincide con el límite de Lienzo. */
        const val MAX_LADO = 4096
    }
}
