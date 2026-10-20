package com.dibujo.android

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Apartado "Historial": tope de pasos que se pueden deshacer. Antes estaba
 * fijo en 30; ahora se elige entre 5 y 100 (más pasos = más RAM, porque cada
 * paso es un snapshot completo de la hoja).
 */
@Composable
fun PantallaOpcionesHistorial(
    estado: DibujoEstado,
    alElegirLimite: (Int) -> Unit,
    alVolver: () -> Unit,
) {
    MarcoOpcion(
        titulo = stringResource(R.string.config_historial_titulo),
        alVolver = alVolver,
    ) {
        TextoAyuda(stringResource(R.string.config_historial_desc))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.config_historial_valor, estado.limiteHistorial),
            style = MaterialTheme.typography.titleMedium,
        )
        var limiteLocal by remember(estado.limiteHistorial) {
            mutableFloatStateOf(estado.limiteHistorial.toFloat())
        }
        Slider(
            value = limiteLocal,
            onValueChange = { limiteLocal = it },
            onValueChangeFinished = {
                val pasos = (limiteLocal / PASO).roundToInt() * PASO
                alElegirLimite(pasos)
            },
            valueRange = MINIMO.toFloat()..MAXIMO.toFloat(),
            steps = (MAXIMO - MINIMO) / PASO - 1,
        )
        TextoAyuda(stringResource(R.string.config_historial_memoria))
    }
}

private const val MINIMO = 5
private const val MAXIMO = 100
private const val PASO = 5
