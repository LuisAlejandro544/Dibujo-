package com.dibujo.android.debug

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Debug
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/** Nivel del log, con la letra que usa logcat. */
enum class NivelLog(val letra: Char) {
    VERBOSE('V'),
    DEBUG('D'),
    INFO('I'),
    WARN('W'),
    ERROR('E'),
    FATAL('F'),
    ;

    companion object {
        fun desdeLetra(letra: Char): NivelLog? = entries.firstOrNull { it.letra == letra }
    }
}

/** Una línea de log capturada (logcat propio o eventos internos del monitor). */
data class EntradaLog(
    val tiempoMs: Long,
    val nivel: NivelLog,
    val tag: String,
    val mensaje: String,
)

/** Resumen de un hilo del proceso en el momento del muestreo. */
data class HiloResumen(
    val nombre: String,
    val estado: String,
    val prioridad: Int,
    val daemon: Boolean,
    /** Primera vez que se ve este nombre desde que arrancó el monitor. */
    val nuevo: Boolean,
)

/** Instantánea de memoria + hilos en un instante. */
data class Muestra(
    val tiempoMs: Long,
    val javaUsado: Long,
    val javaTotal: Long,
    val javaMax: Long,
    val nativoAsignado: Long,
    val nativoTamano: Long,
    val pssTotalKb: Int,
    val pssJavaKb: Int,
    val pssNativoKb: Int,
    val pssGraficosKb: Int,
    val pssOtrosKb: Int,
    val privateDirtyKb: Int,
    val hilos: List<HiloResumen>,
)

/** Tipo de cambio en el conjunto de hilos. */
enum class TipoCambioHilo { APARECIO, TERMINO }

data class EventoHilo(val tiempoMs: Long, val nombre: String, val tipo: TipoCambioHilo)

data class EventoAccion(val tiempoMs: Long, val nombre: String)

/**
 * Monitor de la app principal, activo durante toda la vida del proceso debug.
 *
 * Lo arranca [MonitorProvider] en cuanto el sistema crea el proceso (antes de
 * `Application.onCreate`), así que "escucha" siempre, aunque el usuario nunca
 * abra el panel. Muestrea en un hilo propio cada [INTERVALO_MS]:
 *
 * - **Memoria**: heap Java, heap nativo y PSS por categoría (Java, nativo,
 *   gráficos y otros) leído de [Debug.getMemoryInfo].
 * - **Hilos**: enumeración barata con [Thread.enumerate], con detección de
 *   apariciones/terminaciones entre muestras.
 * - **Acciones**: ciclo de vida de las Activities de la app principal.
 * - **Logs**: warnings/errores/fatales propios vía [CapturaLogs] y crashes
 *   capturados con un `UncaughtExceptionHandler` (encadenado al anterior).
 *
 * Todo vive en buffers en memoria acotados (ring buffers) para no crecer sin
 * control. Es exclusivo del variant debug.
 */
object MonitorDibujo {

    private const val INTERVALO_MS = 2_000L
    private const val MAX_MUESTRAS = 900
    private const val MAX_LOGS = 2_000
    private const val MAX_EVENTOS = 400
    private const val MAX_CRASHES = 20
    private const val MAX_ARCHIVO_CRASH = 300_000

    private val candado = Any()
    private val arrancado = AtomicBoolean(false)

    private val muestras = ArrayDeque<Muestra>()
    private val eventosHilos = ArrayDeque<EventoHilo>()
    private val acciones = ArrayDeque<EventoAccion>()
    private val logs = ArrayDeque<EntradaLog>()
    private val nombresHilos = HashSet<String>()

    private var fallosFatales = 0
    private var capturaLogs: CapturaLogs? = null

    /** Momento en que arrancó el monitor (para la duración de la sesión). */
    val inicioMs: Long = System.currentTimeMillis()

    /** Arranca el monitor una sola vez. Seguro de llamar repetido. */
    fun iniciar(contexto: Context) {
        if (!arrancado.compareAndSet(false, true)) return

        instalarCierreDeCrashes(contexto)
        (contexto.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
            ObservadorCiclo(),
        )
        capturaLogs = CapturaLogs(::agregarLog).also { it.iniciar() }
        arrancarMuestreo()
        registrarAccion("Monitor iniciado")
    }

    /** Cierra hilos y procesos auxiliares (no suele hacer falta: es debug). */
    fun detener() {
        capturaLogs?.detener()
        capturaLogs = null
    }

    /** Registra una acción de la app (inicio de una Activity, apertura, etc.). */
    fun registrarAccion(nombre: String) {
        synchronized(candado) {
            acciones.addLast(EventoAccion(System.currentTimeMillis(), nombre))
            while (acciones.size > MAX_EVENTOS) acciones.removeFirst()
        }
    }

    fun agregarLog(entrada: EntradaLog) {
        synchronized(candado) {
            logs.addLast(entrada)
            while (logs.size > MAX_LOGS) logs.removeFirst()
            if (entrada.nivel == NivelLog.FATAL) fallosFatales++
        }
    }

    fun ultimaMuestra(): Muestra? = synchronized(candado) { muestras.lastOrNull() }

    fun muestras(limite: Int = MAX_MUESTRAS): List<Muestra> =
        synchronized(candado) { muestras.toList().takeLast(limite) }

    fun hilosActuales(): List<HiloResumen> = ultimaMuestra()?.hilos.orEmpty()

    fun cambiosHilos(limite: Int = 200): List<EventoHilo> =
        synchronized(candado) { eventosHilos.toList().takeLast(limite) }

    fun accionesRecientes(limite: Int = 120): List<EventoAccion> =
        synchronized(candado) { acciones.toList().takeLast(limite) }

    fun logsRecientes(minimo: NivelLog, limite: Int = 500): List<EntradaLog> =
        synchronized(candado) {
            logs.filter { it.nivel.ordinal >= minimo.ordinal }.takeLast(limite)
        }

    val totalFallosFatales: Int get() = synchronized(candado) { fallosFatales }

    fun limpiarRegistro() {
        synchronized(candado) {
            logs.clear()
            fallosFatales = 0
        }
    }

    private fun arrancarMuestreo() {
        Thread(
            {
                while (true) {
                    runCatching { muestrear() }
                    try {
                        Thread.sleep(INTERVALO_MS)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                }
            },
            "dibujo-monitor",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun muestrear() {
        val tiempo = Runtime.getRuntime()
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        val graficos = info.getMemoryStat("summary.graphics")?.toIntOrNull() ?: 0

        val hilosCrudos = enumerarHilos()
        val tiempoMs = System.currentTimeMillis()
        val javaUsado = tiempo.totalMemory() - tiempo.freeMemory()
        val nativoAsignado = Debug.getNativeHeapAllocatedSize()
        val nativoTamano = Debug.getNativeHeapSize()

        synchronized(candado) {
            val actuales = HashSet<String>(hilosCrudos.size)
            for (h in hilosCrudos) actuales.add(h.nombre)

            for (n in actuales) {
                if (n !in nombresHilos) {
                    eventosHilos.addLast(EventoHilo(tiempoMs, n, TipoCambioHilo.APARECIO))
                }
            }
            for (n in nombresHilos) {
                if (n !in actuales) {
                    eventosHilos.addLast(EventoHilo(tiempoMs, n, TipoCambioHilo.TERMINO))
                }
            }
            while (eventosHilos.size > MAX_EVENTOS) eventosHilos.removeFirst()

            val hilos = hilosCrudos.map { it.copy(nuevo = it.nombre !in nombresHilos) }
            nombresHilos.clear()
            nombresHilos.addAll(actuales)

            muestras.addLast(
                Muestra(
                    tiempoMs = tiempoMs,
                    javaUsado = javaUsado,
                    javaTotal = tiempo.totalMemory(),
                    javaMax = tiempo.maxMemory(),
                    nativoAsignado = nativoAsignado,
                    nativoTamano = nativoTamano,
                    pssTotalKb = info.totalPss,
                    pssJavaKb = info.dalvikPss,
                    pssNativoKb = info.nativePss,
                    pssGraficosKb = graficos,
                    pssOtrosKb = info.otherPss,
                    privateDirtyKb = info.totalPrivateDirty,
                    hilos = hilos,
                ),
            )
            while (muestras.size > MAX_MUESTRAS) muestras.removeFirst()
        }
    }

    /**
     * Enumeración barata de hilos del grupo actual y sus subgrupos (incluye
     * los del proceso). No vuelca stacks (eso sería mucho más caro).
     */
    private fun enumerarHilos(): List<HiloResumen> {
        val arreglo = arrayOfNulls<Thread>(Thread.activeCount() + 32)
        val total = Thread.enumerate(arreglo)
        val lista = ArrayList<HiloResumen>(total)
        for (i in 0 until total) {
            val hilo = arreglo[i] ?: continue
            lista.add(
                HiloResumen(
                    nombre = hilo.name,
                    estado = hilo.state.name,
                    prioridad = hilo.priority,
                    daemon = hilo.isDaemon,
                    nuevo = false,
                ),
            )
        }
        return lista
    }

    private fun instalarCierreDeCrashes(contexto: Context) {
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            runCatching {
                val traza = error.stackTraceToString()
                agregarLog(
                    EntradaLog(
                        tiempoMs = System.currentTimeMillis(),
                        nivel = NivelLog.FATAL,
                        tag = "CRASH",
                        mensaje = "${error.javaClass.name}: ${error.message}\n$traza",
                    ),
                )
                guardarCrash(contexto, hilo.name, traza)
            }
            anterior?.uncaughtException(hilo, error)
        }
    }

    private fun guardarCrash(contexto: Context, hilo: String, traza: String) {
        runCatching {
            val directorio = java.io.File(contexto.filesDir, "crashes")
            if (!directorio.exists()) directorio.mkdirs()
            val recortada = traza.take(MAX_ARCHIVO_CRASH)
            val archivo = java.io.File(directorio, "crash_${System.currentTimeMillis()}.txt")
            archivo.writeText("Hilo: $hilo\n\n$recortada")
            val todos = directorio.listFiles()?.sortedBy { it.name }.orEmpty()
            if (todos.size > MAX_CRASHES) {
                todos.take(todos.size - MAX_CRASHES).forEach { it.delete() }
            }
        }
    }

    /** Registra el ciclo de vida de las Activities de la app (no del panel). */
    private class ObservadorCiclo : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, estado: Bundle?) = registrar(activity, "onCreate")
        override fun onActivityStarted(activity: Activity) = registrar(activity, "onStart")
        override fun onActivityResumed(activity: Activity) = registrar(activity, "onResume")
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = registrar(activity, "onStop")
        override fun onActivitySaveInstanceState(activity: Activity, estado: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = registrar(activity, "onDestroy")

        private fun registrar(activity: Activity, evento: String) {
            if (activity is PanelDebugActivity) return
            MonitorDibujo.registrarAccion("${activity.javaClass.simpleName} $evento")
        }
    }
}
