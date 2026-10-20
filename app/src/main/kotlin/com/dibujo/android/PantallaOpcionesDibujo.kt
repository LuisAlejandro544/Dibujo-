package com.dibujo.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
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
import com.dibujo.core.Herramienta
import com.dibujo.core.Lapiz
import com.dibujo.core.Textura
import kotlin.math.roundToInt

/**
 * Apartado "Valores de dibujo": herramienta, grosor y textura con los que
 * arranca un dibujo nuevo. No cambia la herramienta actual del lienzo; se
 * aplica la próxima vez que se empieza desde cero.
 */
@Composable
fun PantallaOpcionesDibujo(
    estado: DibujoEstado,
    alElegirHerramienta: (Herramienta) -> Unit,
    alElegirGrosor: (Int) -> Unit,
    alElegirTextura: (Textura) -> Unit,
    alVolver: () -> Unit,
) {
    MarcoOpcion(
        titulo = stringResource(R.string.config_dibujo_titulo),
        alVolver = alVolver,
    ) {
        TextoAyuda(stringResource(R.string.config_dibujo_ayuda))
        Spacer(Modifier.height(8.dp))

        TituloSeccion(stringResource(R.string.config_herramienta_inicial))
        Column(Modifier.selectableGroup()) {
            HERRAMIENTAS.forEach { (herramienta, recurso) ->
                FilaOpcion(
                    etiqueta = stringResource(recurso),
                    seleccionada = estado.herramientaInicial == herramienta,
                    alElegir = { alElegirHerramienta(herramienta) },
                )
            }
        }

        TituloSeccion(stringResource(R.string.config_grosor_inicial))
        Text(
            stringResource(R.string.config_grosor_valor, estado.grosorInicial),
            style = MaterialTheme.typography.bodyMedium,
        )
        var grosorLocal by remember(estado.grosorInicial) {
            mutableFloatStateOf(estado.grosorInicial.toFloat())
        }
        Slider(
            value = grosorLocal,
            onValueChange = { grosorLocal = it },
            onValueChangeFinished = { alElegirGrosor(grosorLocal.roundToInt()) },
            valueRange = Lapiz.GROSOR_MIN.toFloat()..Lapiz.GROSOR_MAX.toFloat(),
            steps = Lapiz.GROSOR_MAX - Lapiz.GROSOR_MIN - 1,
        )

        TituloSeccion(stringResource(R.string.config_textura_inicial))
        Column(Modifier.selectableGroup()) {
            TEXTURAS.forEach { (textura, recurso) ->
                FilaOpcion(
                    etiqueta = stringResource(recurso),
                    seleccionada = estado.texturaInicial == textura,
                    alElegir = { alElegirTextura(textura) },
                )
            }
        }
    }
}

private val HERRAMIENTAS = listOf(
    Herramienta.LAPIZ to R.string.herramienta_lapiz,
    Herramienta.PORTAMINA to R.string.herramienta_portamina,
    Herramienta.CARBONCILLO to R.string.herramienta_carboncillo,
    Herramienta.BORRADOR to R.string.herramienta_borrador,
    Herramienta.PINTAR to R.string.herramienta_pintar,
    Herramienta.DOBLE to R.string.herramienta_doble,
    Herramienta.PLUMA to R.string.herramienta_pluma,
)

private val TEXTURAS = listOf(
    Textura.LISA to R.string.textura_lisa,
    Textura.GRANO to R.string.textura_grano,
    Textura.RUGOSA to R.string.textura_rugosa,
    Textura.TRAMA to R.string.textura_trama,
    Textura.SALPICADA to R.string.textura_salpicada,
)
