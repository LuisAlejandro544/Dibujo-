package com.dibujo.android

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager

/**
 * Detecta el soporte gráfico real del equipo para elegir el backend.
 *
 * - **Vulkan 1.1**: se consulta `FEATURE_VULKAN_HARDWARE_VERSION` (API 24+)
 *   con la versión empaquetada 1.1 (`0x00401000`). Un equipo sin Vulkan, o
 *   con Vulkan 1.0, no cumple y la opción queda bloqueada en la UI.
 * - **OpenGL ES**: `ActivityManager.deviceConfigurationInfo.glEsVersion`
 *   (p. ej. `"3.2"`). OpenGL ES 2.0 está garantizado en todos los Android
 *   soportados, por eso es el respaldo universal.
 */
class CapacidadesGraficas(contexto: Context) {

    private val gestorPaquetes: PackageManager = contexto.packageManager
    private val gestorActividades: ActivityManager =
        contexto.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /** El equipo declara Vulkan 1.1 o superior. */
    val vulkanSoportado: Boolean = detectarVulkan()

    /** Versión de OpenGL ES informada por el sistema, p. ej. "3.2". */
    val openglEsVersion: String =
        gestorActividades.deviceConfigurationInfo?.glEsVersion ?: FALLBACK_OPENGL_ES

    private fun detectarVulkan(): Boolean {
        if (!gestorPaquetes.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)) {
            return false
        }
        return gestorPaquetes.hasSystemFeature(
            PackageManager.FEATURE_VULKAN_HARDWARE_VERSION,
            VULKAN_1_1,
        )
    }

    private companion object {
        /** Versión empaquetada: major<<22 | minor<<12 | patch → 1.1.0. */
        const val VULKAN_1_1 = 0x00401000
        const val FALLBACK_OPENGL_ES = "2.0"
    }
}
