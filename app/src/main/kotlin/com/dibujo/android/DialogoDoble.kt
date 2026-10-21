package com.dibujo.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dibujo.core.Colores
import com.dibujo.core.Herramienta
import com.dibujo.core.HerramientaDoble
import com.dibujo.core.LadoDoble
import com.dibujo.core.Lapiz

/**
 * Diálogo de configuración del **trazo doble** (herramienta DOBLE).
 *
 * El doble dibuja dos trazos con un solo gesto:
 * - el **principal**, con el lápiz elegido (lápiz, portamina o carbóncillo);
 * - el **acompañante** ("la doble"), con otra herramienta, del lado elegido
 *   (sobre el trazo, a la izquierda o a la derecha) y con su propio color de
 *   sombra.
 *
 * Con el pincel vectorial el acompañante lo dibuja la capa de vectores (no el
 * motor), pero el resultado es el mismo: dos trazos con un solo gesto.
 */
@Composable
fun DialogoDoble(
    lapiz: Lapiz,
    coloresRecientes: List<Int>,
    alElegirPrincipal: (Herramienta) -> Unit,
    alElegirDoble: (HerramientaDoble) -> Unit,
    alElegirLado: (LadoDoble) -> Unit,
    alElegirColorDoble: (Int) -> Unit,
    alAbrirRuedaColor: () -> Unit,
    alCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.doble_titulo)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.doble_descripcion),
                    style = MaterialTheme.typography.bodySmall,
                )
                // Herramienta del trazo principal.
                SeccionDoble(stringResource(R.string.doble_principal))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChipHerramienta(
                        etiqueta = stringResource(R.string.herramienta_lapiz),
                        icono = Icons.Rounded.Draw,
                        seleccionado = lapiz.herramientaPrincipal == Herramienta.LAPIZ,
                        alPulsar = { alElegirPrincipal(Herramienta.LAPIZ) },
                    )
                    ChipHerramienta(
                        etiqueta = stringResource(R.string.herramienta_portamina),
                        icono = Icons.Rounded.Mode,
                        seleccionado = lapiz.herramientaPrincipal == Herramienta.PORTAMINA,
                        alPulsar = { alElegirPrincipal(Herramienta.PORTAMINA) },
                    )
                    ChipHerramienta(
                        etiqueta = stringResource(R.string.herramienta_carboncillo),
                        icono = Icons.Rounded.Brush,
                        seleccionado = lapiz.herramientaPrincipal == Herramienta.CARBONCILLO,
                        alPulsar = { alElegirPrincipal(Herramienta.CARBONCILLO) },
                    )
                }
                // Herramienta del trazo acompañante.
                SeccionDoble(stringResource(R.string.doble_acompante))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChipHerramienta(
                        etiqueta = stringResource(R.string.herramienta_lapiz),
                        icono = Icons.Rounded.Draw,
                        seleccionado = lapiz.herramientaDoble == HerramientaDoble.LAPIZ,
                        alPulsar = { alElegirDoble(HerramientaDoble.LAPIZ) },
                    )
                    ChipHerramienta(
                        etiqueta = stringResource(R.string.herramienta_portamina),
                        icono = Icons.Rounded.Mode,
                        seleccionado = lapiz.herramientaDoble == HerramientaDoble.PORTAMINA,
                        alPulsar = { alElegirDoble(HerramientaDoble.PORTAMINA) },
                    )
                    ChipHerramienta(
                        etiqueta = stringResource(R.string.herramienta_carboncillo),
                        icono = Icons.Rounded.Brush,
                        seleccionado = lapiz.herramientaDoble == HerramientaDoble.CARBONCILLO,
                        alPulsar = { alElegirDoble(HerramientaDoble.CARBONCILLO) },
                    )
                    ChipHerramienta(
                        etiqueta = stringResource(R.string.doble_vectorial),
                        icono = Icons.Rounded.Edit,
                        seleccionado = lapiz.herramientaDoble == HerramientaDoble.PLUMA,
                        alPulsar = { alElegirDoble(HerramientaDoble.PLUMA) },
                    )
                }
                // Lado donde se dibuja el acompañante.
                SeccionDoble(stringResource(R.string.doble_lado))
                Text(
                    stringResource(R.string.doble_lado_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChipHerramienta(
                        etiqueta = stringResource(R.string.doble_lado_mismo),
                        seleccionado = lapiz.ladoDoble == LadoDoble.MISMO,
                        alPulsar = { alElegirLado(LadoDoble.MISMO) },
                    )
                    ChipHerramienta(
                        etiqueta = stringResource(R.string.doble_lado_izquierda),
                        seleccionado = lapiz.ladoDoble == LadoDoble.IZQUIERDA,
                        alPulsar = { alElegirLado(LadoDoble.IZQUIERDA) },
                    )
                    ChipHerramienta(
                        etiqueta = stringResource(R.string.doble_lado_derecha),
                        seleccionado = lapiz.ladoDoble == LadoDoble.DERECHA,
                        alPulsar = { alElegirLado(LadoDoble.DERECHA) },
                    )
                }
                // Color de sombra del acompañante.
                SeccionDoble(stringResource(R.string.doble_color_sombra))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    coloresRecientes.forEach { argb ->
                        MuestraColor(
                            argb = argb,
                            nombre = "#%06X".format(argb and 0xFFFFFF),
                            seleccionado = lapiz.colorDoble == argb,
                            alPulsar = { alElegirColorDoble(argb) },
                        )
                    }
                    Colores.PALETA.forEach { color ->
                        MuestraColor(
                            argb = color.argb,
                            nombre = color.nombre,
                            seleccionado = lapiz.colorDoble == color.argb,
                            alPulsar = { alElegirColorDoble(color.argb) },
                        )
                    }
                }
                TextButton(onClick = alAbrirRuedaColor) {
                    Icon(
                        Icons.Rounded.Colorize,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.doble_abrir_rueda))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = alCerrar) {
                Text(stringResource(R.string.doble_listo))
            }
        },
    )
}

/** Rótulo de una sección del diálogo del doble. */
@Composable
private fun SeccionDoble(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Ficha de una opción del diálogo del doble. */
@Composable
private fun ChipHerramienta(
    etiqueta: String,
    seleccionado: Boolean,
    alPulsar: () -> Unit,
    icono: ImageVector? = null,
) {
    FilterChip(
        selected = seleccionado,
        onClick = alPulsar,
        label = { Text(etiqueta) },
        leadingIcon = if (icono != null) {
            {
                Icon(icono, contentDescription = null, Modifier.size(18.dp))
            }
        } else {
            null
        },
        modifier = Modifier.semantics { contentDescription = etiqueta },
        colors = FilterChipDefaults.filterChipColors(),
    )
}

/** Muestra de color (paleta o color reciente) del diálogo del doble. */
@Composable
private fun MuestraColor(
    argb: Int,
    nombre: String,
    seleccionado: Boolean,
    alPulsar: () -> Unit,
) {
    Box(
        Modifier
            .size(30.dp)
            .background(Color(argb), CircleShape)
            .border(
                width = if (seleccionado) 3.dp else 1.dp,
                color = if (seleccionado) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape,
            )
            .clickable(onClick = alPulsar)
            .semantics { contentDescription = nombre },
    )
}
