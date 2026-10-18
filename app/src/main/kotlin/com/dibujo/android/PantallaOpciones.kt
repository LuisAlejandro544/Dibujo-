package com.dibujo.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dibujo.core.BackendRender

/**
 * Pantalla "Opciones": antes la configuración era un diálogo dentro de la
 * pantalla de dibujo; ahora tiene su propia pantalla, accesible desde el menú
 * principal y desde la barra superior del lienzo. Por ahora reúne la elección
 * del motor gráfico (OpenGL ES / Vulkan / automático).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaOpciones(
    estado: DibujoEstado,
    alElegirBackend: (BackendRender) -> Unit,
    alVolver: () -> Unit,
) {
    BackHandler(onBack = alVolver)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.config_titulo)) },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_volver),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { margenes ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(margenes)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.config_backend),
                style = MaterialTheme.typography.titleSmall,
            )
            Column(Modifier.selectableGroup()) {
                BackendRender.entries.forEach { backend ->
                    OpcionBackend(
                        backend = backend,
                        seleccionado = estado.backendSeleccionado == backend,
                        habilitado = backend.disponible(estado.vulkanSoportado),
                        alElegir = { alElegirBackend(backend) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.config_backend_efectivo,
                    stringResource(estado.backendEfectivo.recursoNombre()),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.config_opengl_version, estado.openglEsVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    when {
                        estado.gpuActivo -> R.string.config_render_gpu
                        estado.gpuFallo -> R.string.config_render_cpu_fallo
                        else -> R.string.config_render_cpu
                    },
                    estado.gpuDescripcion,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!estado.vulkanSoportado) {
                Text(
                    stringResource(R.string.config_vulkan_no_soportado),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OpcionBackend(
    backend: BackendRender,
    seleccionado: Boolean,
    habilitado: Boolean,
    alElegir: () -> Unit,
) {
    val etiqueta = stringResource(backend.recursoNombre())
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = seleccionado,
                enabled = habilitado,
                role = Role.RadioButton,
                onClick = alElegir,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = seleccionado,
            onClick = null,
            enabled = habilitado,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = if (habilitado) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Nombre legible de cada backend, desde los strings (sin texto duro). */
internal fun BackendRender.recursoNombre(): Int = when (this) {
    BackendRender.AUTOMATICO -> R.string.backend_automatico
    BackendRender.OPENGL -> R.string.backend_opengl
    BackendRender.VULKAN -> R.string.backend_vulkan
}
