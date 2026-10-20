package com.dibujo.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dibujo.core.BackendRender

/**
 * Apartado "Motor de renderizado del lienzo": elige OpenGL ES / Vulkan /
 * automático y muestra el diagnóstico del equipo (versión de OpenGL, render
 * GPU en uso y si Vulkan está soportado).
 */
@Composable
fun PantallaOpcionesMotor(
    estado: DibujoEstado,
    alElegirBackend: (BackendRender) -> Unit,
    alVolver: () -> Unit,
) {
    MarcoOpcion(
        titulo = stringResource(R.string.config_motor_titulo),
        alVolver = alVolver,
    ) {
        TituloSeccion(stringResource(R.string.config_backend))
        Column(Modifier.selectableGroup()) {
            BackendRender.entries.forEach { backend ->
                FilaOpcion(
                    etiqueta = stringResource(backend.recursoNombre()),
                    seleccionada = estado.backendSeleccionado == backend,
                    habilitada = backend.disponible(estado.vulkanSoportado),
                    alElegir = { alElegirBackend(backend) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextoAyuda(
            stringResource(
                R.string.config_backend_efectivo,
                stringResource(estado.backendEfectivo.recursoNombre()),
            ),
        )
        TextoAyuda(stringResource(R.string.config_opengl_version, estado.openglEsVersion))
        TextoAyuda(
            stringResource(
                when {
                    estado.gpuActivo -> R.string.config_render_gpu
                    estado.gpuFallo -> R.string.config_render_cpu_fallo
                    else -> R.string.config_render_cpu
                },
                estado.gpuDescripcion,
            ),
        )
        if (!estado.vulkanSoportado) {
            TextoAyuda(stringResource(R.string.config_vulkan_no_soportado))
        }
    }
}

/** Nombre legible de cada backend, desde los strings (sin texto duro). */
internal fun BackendRender.recursoNombre(): Int = when (this) {
    BackendRender.AUTOMATICO -> R.string.backend_automatico
    BackendRender.OPENGL -> R.string.backend_opengl
    BackendRender.VULKAN -> R.string.backend_vulkan
}
