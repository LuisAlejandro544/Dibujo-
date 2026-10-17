package com.dibujo.android.debug

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lee el logcat propio de la app para capturar warnings, errores y fallos
 * fatales "crudos" (sin adb, que es justo el caso del usuario en el móvil).
 *
 * Desde Android 4.1 una app solo ve sus propios logs, y eso alcanza: son los
 * warnings/errores que genera ella misma (Compose, EGL, Vulkan, LeakCanary...).
 * Se ejecuta en un hilo daemon; si el dispositivo restringe `logcat`, el monitor
 * sigue funcionando con su propio buffer y el capturador de crashes.
 */
class CapturaLogs(private val alEntrada: (EntradaLog) -> Unit) {

    private val activo = AtomicBoolean(false)
    private var proceso: Process? = null
    private var hilo: Thread? = null

    fun iniciar() {
        if (!activo.compareAndSet(false, true)) return
        hilo = Thread({ leer() }, "dibujo-logcat").apply {
            isDaemon = true
            start()
        }
    }

    fun detener() {
        activo.set(false)
        runCatching { proceso?.destroy() }
        proceso = null
        hilo?.interrupt()
        hilo = null
    }

    private fun leer() {
        try {
            val p = ProcessBuilder("logcat", "-v", "threadtime")
                .redirectErrorStream(true)
                .start()
            proceso = p
            p.inputStream.bufferedReader().useLines { lineas ->
                for (linea in lineas) {
                    if (!activo.get()) break
                    parsear(linea)?.let(alEntrada)
                }
            }
        } catch (_: Throwable) {
            // Sin logcat disponible: seguimos con el buffer propio y crashes.
        }
    }

    private fun parsear(linea: String): EntradaLog? {
        val coincidencia = PATRON.matchEntire(linea) ?: return null
        val nivel = NivelLog.desdeLetra(coincidencia.groupValues[1].firstOrNull() ?: ' ') ?: return null
        val tag = coincidencia.groupValues[2].trim()
        val mensaje = coincidencia.groupValues[3]
        if (!interesa(nivel, tag)) return null
        return EntradaLog(System.currentTimeMillis(), nivel, tag, mensaje)
    }

    /**
     * Conserva W/E/F siempre; de V/D/I solo los tags que nos importan. Antes se
     * descartan los tags de ruido de proveedor (firmware TECNO/Transsion y
     * similares): son warnings/errores del framework del equipo, no de la app,
     * y llenaban el reporte con volcados irrelevantes.
     */
    private fun interesa(nivel: NivelLog, tag: String): Boolean {
        val t = tag.lowercase()
        if (t in TAGS_RUIDO) return false
        if (nivel.ordinal >= NivelLog.WARN.ordinal) return true
        return TAGS_INTERES.any { t.contains(it) }
    }

    private companion object {
        // 06-15 12:34:56.789  1234  5678 I Tag     : mensaje
        val PATRON = Regex(
            """^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]*?)\s*:\s?(.*)$""",
        )
        val TAGS_INTERES = listOf(
            "dibujo",
            "leakcanary",
            "egl",
            "vulkan",
            "opengl",
            "gpu",
            "hwui",
            "graphics",
        )
        // Ruido conocido de firmware de proveedor (TECNO/Transsion, etc.):
        // aparecen en el proceso de la app pero no son de la app. Se comparan
        // en minúsculas y por igualdad exacta para no ocultar tags parecidos.
        val TAGS_RUIDO = setOf(
            "tranclassinfo",
            "tranchoreographerimpl",
            "appresolutiontuner",
            "fbi",
            "qt",
        )
    }
}
