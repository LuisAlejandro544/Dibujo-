package com.dibujo.android

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.dibujo.core.BackendRender
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
    /** Hay pasos que deshacer (habilita el botón de deshacer). */
    val puedeDeshacer: Boolean = false,
    /** Hay pasos que rehacer (habilita el botón de rehacer). */
    val puedeRehacer: Boolean = false,
    val motorNombre: String = "",
    /** Backend gráfico elegido por el usuario (persistido). */
    val backendSeleccionado: BackendRender = BackendRender.AUTOMATICO,
    /** Backend que realmente corre según la selección y el soporte del equipo. */
    val backendEfectivo: BackendRender = BackendRender.OPENGL,
    /** El equipo soporta Vulkan 1.1 (habilita esa opción en configuración). */
    val vulkanSoportado: Boolean = false,
    /** Versión de OpenGL ES del equipo (informativo). */
    val openglEsVersion: String = "2.0",
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
class DibujoViewModel(app: Application) : AndroidViewModel(app) {

    private val capacidades = CapacidadesGraficas(app)
    private val preferencias =
        app.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)

    private var motor: MotorDibujo? = null
    private val seleccionInicial = leerBackend()
    private val _estado = MutableStateFlow(
        DibujoEstado(
            backendSeleccionado = seleccionInicial,
            backendEfectivo = seleccionInicial.resolver(capacidades.vulkanSoportado),
            vulkanSoportado = capacidades.vulkanSoportado,
            openglEsVersion = capacidades.openglEsVersion,
        ),
    )
    val estado: StateFlow<DibujoEstado> = _estado.asStateFlow()

    /** Crea el motor una sola vez con el tamaño físico de la pantalla (px). */
    fun inicializar(vw: Int, vh: Int) {
        if (vw <= 0 || vh <= 0 || motor != null) return
        val m = Motores.crear(vw.coerceIn(1, MAX_LADO), vh.coerceIn(1, MAX_LADO))
        motor = m
        m.configurarLapiz(_estado.value.lapiz)
        _estado.update { it.copy(motorNombre = m.nombre) }
    }

    /**
     * Elige el backend gráfico y lo persiste. Una opción no disponible en el
     * equipo (Vulkan sin soporte) se ignora; la UI ya la muestra bloqueada.
     */
    fun elegirBackend(backend: BackendRender) {
        if (!backend.disponible(capacidades.vulkanSoportado)) return
        val efectivo = backend.resolver(capacidades.vulkanSoportado)
        if (backend == _estado.value.backendSeleccionado &&
            efectivo == _estado.value.backendEfectivo
        ) {
            return
        }
        preferencias.edit().putString(CLAVE_BACKEND, backend.name).apply()
        _estado.update {
            it.copy(backendSeleccionado = backend, backendEfectivo = efectivo)
        }
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
        _estado.update {
            it.copy(
                trazoEnCurso = null,
                puedeDeshacer = m.puedeDeshacer(),
                puedeRehacer = m.puedeRehacer(),
                marco = it.marco + 1,
            )
        }
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

    fun deshacer() {
        val m = motor ?: return
        m.deshacer()
        _estado.update {
            it.copy(
                puedeDeshacer = m.puedeDeshacer(),
                puedeRehacer = m.puedeRehacer(),
                marco = it.marco + 1,
            )
        }
    }

    fun rehacer() {
        val m = motor ?: return
        m.rehacer()
        _estado.update {
            it.copy(
                puedeDeshacer = m.puedeDeshacer(),
                puedeRehacer = m.puedeRehacer(),
                marco = it.marco + 1,
            )
        }
    }

    fun limpiar() {
        val m = motor ?: return
        m.limpiarHoja()
        _estado.update {
            it.copy(
                puedeDeshacer = m.puedeDeshacer(),
                puedeRehacer = m.puedeRehacer(),
                marco = it.marco + 1,
            )
        }
    }

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

    private fun leerBackend(): BackendRender =
        preferencias.getString(CLAVE_BACKEND, null)
            ?.let { nombre -> runCatching { BackendRender.valueOf(nombre) }.getOrNull() }
            ?: BackendRender.AUTOMATICO

    private companion object {
        /** Coincide con el límite de Lienzo. */
        const val MAX_LADO = 4096
        const val PREFERENCIAS = "dibujo_config"
        const val CLAVE_BACKEND = "backend_render"
    }
}
