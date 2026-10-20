package com.dibujo.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Apartado "Retroalimentación": vibración háptica y sonidos breves al dibujar,
 * deshacer y rehacer. Ambos se guardan en las preferencias de la app.
 */
@Composable
fun PantallaOpcionesRetroalimentacion(
    estado: DibujoEstado,
    alAlternarVibracion: () -> Unit,
    alAlternarSonidos: () -> Unit,
    alVolver: () -> Unit,
) {
    MarcoOpcion(
        titulo = stringResource(R.string.config_feedback_titulo),
        alVolver = alVolver,
    ) {
        TextoAyuda(stringResource(R.string.config_feedback_desc))
        Spacer(Modifier.height(12.dp))
        FilaInterruptor(
            titulo = stringResource(R.string.config_vibracion),
            descripcion = stringResource(R.string.config_vibracion_desc),
            activo = estado.vibracionActiva,
            alCambiar = alAlternarVibracion,
        )
        Spacer(Modifier.height(10.dp))
        FilaInterruptor(
            titulo = stringResource(R.string.config_sonidos),
            descripcion = stringResource(R.string.config_sonidos_desc),
            activo = estado.sonidosActivos,
            alCambiar = alAlternarSonidos,
        )
    }
}

/** Fila con título, ayuda y un interruptor a la derecha. */
@Composable
private fun FilaInterruptor(
    titulo: String,
    descripcion: String,
    activo: Boolean,
    alCambiar: () -> Unit,
) {
    Surface(
        onClick = alCambiar,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$titulo. $descripcion" },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(
                    descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = activo,
                onCheckedChange = { alCambiar() },
            )
        }
    }
}
