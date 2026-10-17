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
    /** Ancho actual de la hoja en píxeles (cambia al expandir). */
    val hojaAncho: Int = 0,
    /** Alto actual de la hoja en píxeles (cambia al expandir). */
    val hojaAlto: Int = 0,
    /** Imagen de calco (para calcar debajo del trazo); null si no hay. */
    val calcoUri: String? = null,
    /** La imagen de calco se muestra (se puede alternar en cualquier momento). */
    val calcoVisible: Boolean = false,
    /** Opacidad de la imagen de calco (0..1). */
    val calcoAlfa: Float = 0.45f,
    /** Backend gráfico elegido por el usuario (persistido). */
    val backendSeleccionado: BackendRender = BackendRender.AUTOMATICO,
    /** Backend que realmente corre según la selección y el soporte del equipo. */
    val backendEfectivo: BackendRender = BackendRender.OPENGL,
    /** El equipo soporta Vulkan 1.1 (habilita esa opción en configuración). */
    val vulkanSoportado: Boolean = false,
    /** Versión de OpenGL ES del equipo (informativo). */
    val openglEsVersion: String = "2.0",
    /** El motor expone un handle nativo compartible con el render GPU. */
    val gpuDisponible: Boolean = false,
    /** El render GPU está en uso (si no, se pinta por CPU con Compose). */
    val gpuActivo: Boolean = false,
    /** El render GPU se está creando/creando de nuevo: pantalla de carga. */
    val gpuCargando: Boolean = false,
    /** Descripción del renderizador GPU en uso (diagnóstico). */
    val gpuDescripcion: String = "",
    /** El intento de GPU falló: se cae al render por CPU. */
    val gpuFallo: Boolean = false,
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
    private var vistaAncho = 0
    private var vistaAlto = 0
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
        if (vw <= 0 || vh <= 0) return
        vistaAncho = vw
        vistaAlto = vh
        if (motor != null) return
        val m = Motores.crear(vw.coerceIn(1, MAX_LADO), vh.coerceIn(1, MAX_LADO))
        motor = m
        m.configurarLapiz(_estado.value.lapiz)
        val hayGpu = m.handleNativo != 0L
        _estado.update {
            it.copy(
                motorNombre = m.nombre,
                hojaAncho = m.ancho,
                hojaAlto = m.alto,
                gpuDisponible = hayGpu,
                // Con GPU disponible arranca la pantalla de carga hasta que el
                // renderizador nativo confirme (`gpuListo`) o falle (`gpuFracaso`).
                gpuCargando = hayGpu,
            )
        }
    }

    /** Handle nativo del motor para el render GPU (0 si no aplica). */
    fun handleMotor(): Long = motor?.handleNativo ?: 0L

    /** El render GPU arrancó: se pinta por GPU y se registra su descripción. */
    fun gpuListo(descripcion: String) {
        _estado.update {
            it.copy(
                gpuActivo = true,
                gpuDescripcion = descripcion,
                gpuFallo = false,
                gpuCargando = false,
            )
        }
    }

    /** El render GPU falló: se cae al render por CPU con Compose. */
    fun gpuFracaso() {
        _estado.update {
            it.copy(
                gpuActivo = false,
                gpuDescripcion = "",
                gpuFallo = true,
                gpuCargando = false,
            )
        }
    }

    /**
     * Elige el backend gráfico y lo persiste. Una opción no disponible en el
     * equipo (Vulkan sin soporte) se ignora; la UI ya la muestra bloqueada.
     */
    fun elegirBackend(backend: BackendRender) {
        if (!backend.disponible(capacidades.vulkanSoportado)) return
        val efectivo = backend.resolver(capacidades.vulkanSoportado)
        val actual = _estado.value
        if (backend == actual.backendSeleccionado &&
            efectivo == actual.backendEfectivo &&
            !actual.gpuFallo
        ) {
            return
        }
        preferencias.edit().putString(CLAVE_BACKEND, backend.name).apply()
        // El renderizador se recrea si cambia el backend EFECTIVO (p. ej. pasar
        // de Automático a OpenGL sigue usando OpenGL) o si el intento anterior
        // había fallado (se reintenta la GPU). En cambio, si todo sigue igual, no
        // hay nada que recrear ni pantalla de carga que mostrar.
        val cambiaRender = efectivo != actual.backendEfectivo || actual.gpuFallo
        _estado.update {
            it.copy(
                backendSeleccionado = backend,
                backendEfectivo = efectivo,
                gpuActivo = if (cambiaRender) false else it.gpuActivo,
                gpuDescripcion = if (cambiaRender) "" else it.gpuDescripcion,
                gpuFallo = if (cambiaRender) false else it.gpuFallo,
                gpuCargando = if (cambiaRender) it.gpuDisponible else it.gpuCargando,
            )
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

    /**
     * Pinch de zoom anclado al punto donde están los dedos: la hoja que está
     * bajo el foco no se mueve. La transformación incluye la rotación:
     * `hoja = R(−rot)·(pantalla + pan)/zoom` y
     * `pantalla = R(rot)·(hoja·zoom) − pan`.
     */
    fun zoomear(factor: Float, focoX: Float, focoY: Float) {
        val m = motor ?: return
        if (_estado.value.modo != Modo.MOVER) return
        val cam = m.camara()
        val hf = pantallaAUnrotado(cam, focoX, focoY)
        m.zoom(factor)
        anclarFoco(m, hf.first, hf.second, focoX, focoY)
        _estado.update { it.copy(camara = m.camara(), marco = it.marco + 1) }
    }

    /**
     * Gira la hoja [delta] radianes anclado a [focoX]/[focoY]: el punto de la
     * hoja bajo los dedos queda quieto. Es el gesto de rotación de dos dedos
     * (modo MOVER), como en las apps de dibujo profesionales.
     */
    fun rotar(delta: Float, focoX: Float, focoY: Float) {
        val m = motor ?: return
        if (_estado.value.modo != Modo.MOVER) return
        val cam = m.camara()
        val hf = pantallaAUnrotado(cam, focoX, focoY)
        m.rotar(delta)
        anclarFoco(m, hf.first, hf.second, focoX, focoY)
        _estado.update { it.copy(camara = m.camara(), marco = it.marco + 1) }
    }

    /** Endereza la hoja (rotación 0) sin tocar el zoom, anclado al centro. */
    fun enderezar() {
        val m = motor ?: return
        val cam = m.camara()
        if (cam.rotacion == 0f) return
        val cx = vistaAncho / 2f
        val cy = vistaAlto / 2f
        val hf = pantallaAUnrotado(cam, cx, cy)
        m.rotar(-cam.rotacion)
        anclarFoco(m, hf.first, hf.second, cx, cy)
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

    /**
     * Expande la hoja agregando [extraAncho] px a la derecha y [extraAlto] px
     * abajo (el contenido no se mueve). Al cambiar el tamaño, los motores
     * limpian el historial. Si hay render GPU, se recrea para reasignar la
     * textura al nuevo tamaño (por eso se enciende `gpuCargando`).
     */
    fun expandirHoja(extraAncho: Int, extraAlto: Int) {
        val m = motor ?: return
        val dAncho = extraAncho.coerceAtLeast(0)
        val dAlto = extraAlto.coerceAtLeast(0)
        if (dAncho == 0 && dAlto == 0) return
        m.expandir(0, 0, dAncho, dAlto)
        _estado.update {
            it.copy(
                hojaAncho = m.ancho,
                hojaAlto = m.alto,
                puedeDeshacer = m.puedeDeshacer(),
                puedeRehacer = m.puedeRehacer(),
                // Sólo se muestra la pantalla de carga si el render GPU se va a
                // recrear (si falló, se sigue por CPU y no hay nada que esperar).
                gpuCargando = it.gpuDisponible && !it.gpuFallo,
                marco = it.marco + 1,
            )
        }
    }

    /**
     * Toque sin arrastre: pinta un punto con el lápiz o rellena una región con
     * la herramienta PINTAR. Un arrastre (trazo) lo maneja la capa de gestos.
     */
    fun tocarHoja(px: Float, py: Float) {
        val m = motor ?: return
        if (_estado.value.modo != Modo.EDICION) return
        val (hx, hy) = aHoja(m, px, py)
        m.iniciarTrazo(hx, hy)
        m.terminarTrazo()
        _estado.update {
            it.copy(
                puedeDeshacer = m.puedeDeshacer(),
                puedeRehacer = m.puedeRehacer(),
                marco = it.marco + 1,
            )
        }
    }

    /** Fija (o quita con null) la imagen de calco y la muestra al elegirla. */
    fun elegirCalco(uri: String?) {
        _estado.update {
            it.copy(
                calcoUri = uri,
                calcoVisible = uri != null,
            )
        }
    }

    /** Muestra u oculta la imagen de calco en cualquier momento. */
    fun alternarCalco() {
        if (_estado.value.calcoUri == null) return
        _estado.update { it.copy(calcoVisible = !it.calcoVisible) }
    }

    fun cambiarCalcoAlfa(alfa: Float) {
        _estado.update { it.copy(calcoAlfa = alfa.coerceIn(0f, 1f)) }
    }

    /** Rasteriza la vista (vw x vh px) con la cámara aplicada, para el lienzo. */
    fun renderizar(destino: IntArray, vw: Int, vh: Int) {
        motor?.renderizar(destino, vw, vh)
    }

    /**
     * Copia la hoja completa (ancho * alto px ARGB) a [destino] para exportarla
     * como imagen. Devuelve `false` si el motor no está listo o [destino] no
     * alcanza. Es la misma hoja que ve el usuario, sin cámara.
     */
    fun copiarHoja(destino: IntArray): Boolean = motor?.copiarHoja(destino) ?: false

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

    private fun aHoja(m: MotorDibujo, px: Float, py: Float): Pair<Float, Float> =
        pantallaAUnrotado(m.camara(), px, py)

    /** Punto de hoja bajo (px, py) según la cámara: `R(−rot)(pantalla+pan)/zoom`. */
    private fun pantallaAUnrotado(cam: Camara, px: Float, py: Float): Pair<Float, Float> {
        val ex = px + cam.panX
        val ey = py + cam.panY
        val c = kotlin.math.cos(cam.rotacion.toDouble()).toFloat()
        val s = kotlin.math.sin(cam.rotacion.toDouble()).toFloat()
        return ((c * ex + s * ey) / cam.zoom) to ((-s * ex + c * ey) / cam.zoom)
    }

    /**
     * Ajusta el pan para que el punto de hoja (hfx, hfy) vuelva a caer en el
     * foco de pantalla (focoX, focoY) tras cambiar zoom/rotación: despeja
     * `pan = R(rot)·(hoja·zoom) − foco` leyendo la cámara ya actualizada.
     */
    private fun anclarFoco(
        m: MotorDibujo,
        hfx: Float,
        hfy: Float,
        focoX: Float,
        focoY: Float,
    ) {
        val cam2 = m.camara()
        val zx = hfx * cam2.zoom
        val zy = hfy * cam2.zoom
        val c = kotlin.math.cos(cam2.rotacion.toDouble()).toFloat()
        val s = kotlin.math.sin(cam2.rotacion.toDouble()).toFloat()
        val deseadoX = (c * zx - s * zy) - focoX
        val deseadoY = (s * zx + c * zy) - focoY
        m.pan(deseadoX - cam2.panX, deseadoY - cam2.panY)
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
