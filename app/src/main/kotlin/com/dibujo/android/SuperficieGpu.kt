package com.dibujo.android

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.dibujo.core.BackendRender
import com.dibujo.engine.NativeEngine
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Superficie de dibujo presentada por GPU (OpenGL ES o Vulkan).
 *
 * El `SurfaceView` queda en una capa detrás de la ventana; la UI de Compose
 * (gestos y vista previa del trazo) se dibuja encima. La hoja y la cámara
 * viven en el motor nativo y el renderizador las lee directamente: la app no
 * copia píxeles por cuadro, solo avisa cuando hay algo nuevo ([solicitar]).
 *
 * Si el renderizador no se puede crear en el equipo, el hilo avisa por
 * [alEstado] y la pantalla cae al render por CPU (`LienzoCpu`).
 */
@Composable
fun SuperficieGpu(
    estadoState: State<DibujoEstado>,
    vm: DibujoViewModel,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val vista = remember { SuperficieGpuView(contexto) }
    val estado = estadoState.value

    vista.alEstado = { activo, descripcion ->
        if (activo) vm.gpuListo(descripcion) else vm.gpuFracaso()
    }

    AndroidView(
        factory = { vista },
        modifier = modifier,
    )

    // Se recompone en cada cambio de estado (se lee `estado`) y solicita un
    // cuadro: así pan/zoom, trazo terminado, deshacer o limpiar se ven al
    // instante sin rasterizar por CPU.
    SideEffect {
        vista.configurar(vm.handleMotor(), estado.backendEfectivo)
        vista.solicitar()
    }

    DisposableEffect(vista) {
        onDispose { vista.liberar() }
    }
}

/** `SurfaceView` que arranca y para el hilo de render GPU según su ciclo. */
private class SuperficieGpuView(context: Context) :
    SurfaceView(context), SurfaceHolder.Callback {

    /** Avisa (en el hilo principal) si el render GPU arrancó o falló. */
    var alEstado: ((Boolean, String) -> Unit)? = null

    private var handleMotor: Long = 0L
    private var backend: BackendRender = BackendRender.OPENGL
    private var hilo: HiloRenderGpu? = null

    init {
        holder.addCallback(this)
    }

    /** Fija el motor y el backend; reinicia el render si alguno cambió. */
    fun configurar(handleMotor: Long, backend: BackendRender) {
        if (this.handleMotor == handleMotor && this.backend == backend) return
        this.handleMotor = handleMotor
        this.backend = backend
        hilo?.detener()
        hilo = null
        if (holder.surface.isValid) arrancar(holder)
    }

    /** Pide un cuadro nuevo (pan/zoom/trazo/deshacer...). */
    fun solicitar() {
        hilo?.solicitar()
    }

    fun liberar() {
        hilo?.detener()
        hilo = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        arrancar(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        hilo?.redimensionar(width, height)
        hilo?.solicitar()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Debe parar antes de que el sistema invalide la superficie; si no,
        // `eglSwapBuffers`/`vkQueuePresentKHR` fallan con la ventana muerta.
        hilo?.detener()
        hilo = null
    }

    private fun arrancar(holder: SurfaceHolder) {
        if (hilo != null || handleMotor == 0L) return
        val ancho = if (width > 0) width else holder.surfaceFrame.width()
        val alto = if (height > 0) height else holder.surfaceFrame.height()
        val h = HiloRenderGpu(
            surface = holder.surface,
            backend = backend,
            motor = handleMotor,
            ancho = ancho,
            alto = alto,
            alListo = { descripcion -> post { alEstado?.invoke(true, descripcion) } },
            alFallo = { motivo -> post { alEstado?.invoke(false, motivo) } },
        )
        hilo = h
        h.start()
    }
}

/**
 * Hilo de render GPU: crea el renderizador nativo, presenta cuadros a pedido y
 * lo destruye al terminar. Toda la interacción con EGL/Vulkan pasa por acá.
 */
private class HiloRenderGpu(
    private val surface: Surface,
    private val backend: BackendRender,
    private val motor: Long,
    @Volatile private var ancho: Int,
    @Volatile private var alto: Int,
    private val alListo: (String) -> Unit,
    private val alFallo: (String) -> Unit,
) : Thread("dibujo-gpu") {

    private val candado = ReentrantLock()
    private val hayCuadro = candado.newCondition()
    private var pendiente = true
    @Volatile private var activo = true

    fun solicitar() {
        candado.withLock {
            pendiente = true
            hayCuadro.signalAll()
        }
    }

    fun redimensionar(nuevoAncho: Int, nuevoAlto: Int) {
        candado.withLock {
            ancho = nuevoAncho
            alto = nuevoAlto
            pendiente = true
            hayCuadro.signalAll()
        }
    }

    /** Pide parar y espera a que el hilo suelte la superficie y la GPU. */
    fun detener() {
        candado.withLock {
            activo = false
            pendiente = false
            hayCuadro.signalAll()
        }
        try {
            join()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** Espera [ms]; devuelve false si el hilo debe detenerse mientras tanto. */
    private fun dormir(ms: Long): Boolean {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }
        return activo
    }

    override fun run() {
        // El renderizador puede no crearse si la superficie todavía no está
        // lista (cambio brusco de interfaz, rotación): se reintenta un rato
        // antes de dar por perdido el render GPU.
        var gpu = 0L
        var intentos = 0
        while (gpu == 0L && intentos < INTENTOS_CREACION) {
            gpu = try {
                NativeEngine.nativoGpuCrear(motor, surface, backend.ordinal)
            } catch (_: Throwable) {
                0L
            }
            if (gpu == 0L) {
                intentos++
                if (intentos < INTENTOS_CREACION && !dormir(40)) return
            }
        }
        if (gpu == 0L) {
            alFallo("no-disponible")
            return
        }

        try {
            NativeEngine.nativoGpuRedimensionar(gpu, ancho, alto)
            val descripcion = try {
                NativeEngine.nativoGpuNombre(gpu)
            } catch (_: Throwable) {
                ""
            }

            // Primer cuadro ANTES de avisar "listo": así la pantalla de carga se
            // retira con la hoja ya presentada y no se alcanza a ver un cuadro
            // negro (sobre todo en Vulkan). Un error transitorio (la
            // superficie aún no está lista) se reintenta; sólo un fallo real
            // hace caer a CPU.
            var intentosCuadro = 0
            while (true) {
                val estado = try {
                    NativeEngine.nativoGpuRender(gpu)
                } catch (_: Throwable) {
                    ESTADO_FALLO
                }
                when (estado) {
                    ESTADO_PRESENTADO -> break
                    ESTADO_REINTENTAR -> {
                        intentosCuadro++
                        if (intentosCuadro > INTENTOS_ESPERA) {
                            alFallo("contexto-perdido")
                            return
                        }
                        if (!dormir(30)) return
                    }
                    else -> {
                        alFallo("contexto-perdido")
                        return
                    }
                }
            }
            alListo(descripcion)

            var intentosRetrazo = 0
            while (true) {
                candado.withLock {
                    while (!pendiente && activo) {
                        hayCuadro.await()
                    }
                    if (!activo) return
                    pendiente = false
                }
                val estado = try {
                    NativeEngine.nativoGpuRender(gpu)
                } catch (_: Throwable) {
                    ESTADO_FALLO
                }
                when (estado) {
                    ESTADO_PRESENTADO -> intentosRetrazo = 0
                    ESTADO_REINTENTAR -> {
                        // Error transitorio (superficie o cadena de intercambio
                        // en transición): NO se pierde el render GPU, se
                        // reintenta en el cuadro siguiente. La espera crece un
                        // poco para no martillear la GPU si la superficie
                        // tarda en estar lista.
                        intentosRetrazo = (intentosRetrazo + 1).coerceAtMost(5)
                        if (!dormir(16L * intentosRetrazo)) return
                        solicitar()
                    }
                    else -> {
                        alFallo("contexto-perdido")
                        return
                    }
                }
            }
        } finally {
            NativeEngine.nativoGpuDestruir(gpu)
        }
    }

    private companion object {
        /** Cuadro presentado en pantalla. */
        const val ESTADO_PRESENTADO = 0

        /** Error transitorio: reintentar, el render GPU sigue vivo. */
        const val ESTADO_REINTENTAR = 1

        /** Fallo fatal: el render GPU se perdió. */
        const val ESTADO_FALLO = 2

        /** Intentos para crear el renderizador antes de caer a CPU. */
        const val INTENTOS_CREACION = 10

        /** Intentos de cuadro antes de dar por perdido el render. */
        const val INTENTOS_ESPERA = 250
    }
}
