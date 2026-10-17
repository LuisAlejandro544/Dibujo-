package com.dibujo.android.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.dibujo.android.CapacidadesGraficas
import com.dibujo.android.R

/**
 * Construye el reporte de depuración en texto plano: pensado para copiarlo o
 * compartirlo desde el móvil y pegarlo directo en un issue, sin adb ni PC.
 */
fun construirReporte(contexto: Context): String {
    val texto = StringBuilder()
    val app = contexto.applicationContext

    // --- Encabezado -----------------------------------------------------
    texto.appendLine(contexto.getString(R.string.debug_reporte_titulo))
    texto.appendLine("=".repeat(40))

    val paquete = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0)
    }.getOrNull()
    val versionNombre = paquete?.versionName ?: "?"
    @Suppress("DEPRECATION")
    val versionCodigo = paquete?.versionCode?.toLong() ?: 0L
    val esDebug = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    texto.appendLine(
        contexto.getString(R.string.debug_entorno_version, versionNombre, versionCodigo),
    )
    texto.appendLine(contexto.getString(R.string.debug_entorno_build, if (esDebug) "debug" else "release"))
    texto.appendLine(contexto.getString(R.string.debug_uptime, formatearDuracion(contexto, System.currentTimeMillis() - MonitorDibujo.inicioMs)))

    // --- Entorno --------------------------------------------------------
    texto.appendLine()
    texto.appendLine(contexto.getString(R.string.debug_reporte_seccion_entorno))
    texto.appendLine(contexto.getString(R.string.debug_entorno_abi, Build.SUPPORTED_ABIS.joinToString(", ")))
    texto.appendLine(contexto.getString(R.string.debug_entorno_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
    texto.appendLine(contexto.getString(R.string.debug_entorno_fabricante, Build.MANUFACTURER))
    texto.appendLine(contexto.getString(R.string.debug_entorno_modelo, Build.MODEL))
    val capacidades = CapacidadesGraficas(app)
    texto.appendLine(contexto.getString(R.string.debug_entorno_gl, capacidades.openglEsVersion))
    texto.appendLine(
        contexto.getString(
            R.string.debug_entorno_vulkan,
            if (capacidades.vulkanSoportado) contexto.getString(R.string.debug_si) else contexto.getString(R.string.debug_no),
        ),
    )

    // --- Memoria --------------------------------------------------------
    texto.appendLine()
    texto.appendLine(contexto.getString(R.string.debug_reporte_seccion_memoria))
    val muestra = MonitorDibujo.ultimaMuestra()
    if (muestra == null) {
        texto.appendLine(contexto.getString(R.string.debug_reporte_sin_datos))
    } else {
        texto.appendLine(contexto.getString(R.string.debug_mem_java_heap, formatearBytes(contexto, muestra.javaUsado), formatearBytes(contexto, muestra.javaMax)))
        texto.appendLine(contexto.getString(R.string.debug_mem_nativo, formatearBytes(contexto, muestra.nativoAsignado), formatearBytes(contexto, muestra.nativoTamano)))
        texto.appendLine(contexto.getString(R.string.debug_mem_pss, formatearKb(contexto, muestra.pssTotalKb)))
        texto.appendLine(contexto.getString(R.string.debug_mem_pss_java, formatearKb(contexto, muestra.pssJavaKb)))
        texto.appendLine(contexto.getString(R.string.debug_mem_pss_nativo, formatearKb(contexto, muestra.pssNativoKb)))
        texto.appendLine(contexto.getString(R.string.debug_mem_pss_graficos, formatearKb(contexto, muestra.pssGraficosKb)))
        texto.appendLine(contexto.getString(R.string.debug_mem_pss_otros, formatearKb(contexto, muestra.pssOtrosKb)))
        texto.appendLine(contexto.getString(R.string.debug_mem_private_dirty, formatearKb(contexto, muestra.privateDirtyKb)))
    }

    // --- Hilos ----------------------------------------------------------
    texto.appendLine()
    texto.appendLine(contexto.getString(R.string.debug_reporte_seccion_hilos))
    val hilos = MonitorDibujo.hilosActuales()
    texto.appendLine(contexto.getString(R.string.debug_hilos_titulo, hilos.size))
    hilos.forEach { hilo ->
        val tipo = if (hilo.daemon) {
            contexto.getString(R.string.debug_hilo_daemon)
        } else {
            contexto.getString(R.string.debug_hilo_usuario)
        }
        texto.appendLine("  ${hilo.nombre} [${hilo.estado}, $tipo]")
    }

    texto.appendLine()
    texto.appendLine(contexto.getString(R.string.debug_reporte_seccion_cambios))
    MonitorDibujo.cambiosHilos(80).forEach { evento ->
        val tipo = if (evento.tipo == TipoCambioHilo.APARECIO) {
            contexto.getString(R.string.debug_cambio_aparecio)
        } else {
            contexto.getString(R.string.debug_cambio_termino)
        }
        texto.appendLine("  ${formatearHora(evento.tiempoMs)}  ${evento.nombre}  $tipo")
    }

    texto.appendLine()
    texto.appendLine(contexto.getString(R.string.debug_reporte_seccion_acciones))
    MonitorDibujo.accionesRecientes(100).forEach { accion ->
        texto.appendLine("  ${formatearHora(accion.tiempoMs)}  ${accion.nombre}")
    }

    // --- Registro -------------------------------------------------------
    texto.appendLine()
    texto.appendLine(contexto.getString(R.string.debug_reporte_seccion_logs))
    val registros = MonitorDibujo.logsRecientes(NivelLog.WARN, 200)
    if (registros.isEmpty()) {
        texto.appendLine(contexto.getString(R.string.debug_reporte_sin_datos))
    } else {
        registros.forEach { entrada ->
            texto.appendLine("  ${formatearHora(entrada.tiempoMs)} ${entrada.nivel.letra}/${entrada.tag}: ${entrada.mensaje}")
        }
    }

    return texto.toString()
}
