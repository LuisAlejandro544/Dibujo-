package com.dibujo.android

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dibujo.core.Camara
import com.dibujo.core.Colores
import com.dibujo.core.Herramienta
import com.dibujo.core.Lapiz
import com.dibujo.core.Modo

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                PantallaDibujo()
            }
        }
    }
}

/** Pantalla principal: hoja táctil + barra de herramientas. */
@Composable
fun PantallaDibujo(vm: DibujoViewModel = viewModel()) {
    val estado by vm.estado.collectAsState()

    Scaffold { padding ->
        Column(Modifier.padding(padding)) {
            Hoja(estado, vm, Modifier.weight(1f))
            BarraHerramientas(estado, vm)
        }
    }
}

@Composable
private fun Hoja(estado: DibujoEstado, vm: DibujoViewModel, modifier: Modifier = Modifier) {
    var dimension by remember { mutableStateOf(IntSize.Zero) }

    val buffer = remember(dimension) {
        IntArray((dimension.width * dimension.height).coerceAtLeast(1))
    }
    val bitmap = remember(dimension) {
        if (dimension.width > 0 && dimension.height > 0) {
            Bitmap.createBitmap(dimension.width, dimension.height, Bitmap.Config.ARGB_8888)
        } else {
            null
        }
    }
    val imageBitmap = bitmap?.asImageBitmap()

    val trazo = estado.trazoEnCurso
    val camara = estado.camara
    val lapiz = estado.lapiz
    val colorLapiz = Color(estado.lapiz.color)

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { tamano ->
                if (tamano != dimension) dimension = tamano
                vm.inicializar(tamano.width, tamano.height)
            }
            // EDICION: un dedo dibuja; MOVER: pan y pinch de la cámara.
            .pointerInput(estado.modo) {
                if (estado.modo == Modo.MOVER) {
                    detectTransformGestures { foco, pan, zoom, _ ->
                        if (zoom != 1f) vm.zoomear(zoom, foco.x, foco.y)
                        if (pan != Offset.Zero) vm.panear(pan.x, pan.y)
                    }
                } else {
                    detectDragGestures(
                        onDragStart = { vm.iniciarTrazo(it.x, it.y) },
                        onDragEnd = { vm.soltarTrazo() },
                        onDragCancel = { vm.cancelarTrazo() },
                        onDrag = { cambio, _ ->
                            vm.continuarTrazo(cambio.position.x, cambio.position.y)
                            cambio.consume()
                        },
                    )
                }
            },
    ) {
        val bmp = bitmap ?: return@Canvas
        val vw = size.width.toInt().coerceAtLeast(1)
        val vh = size.height.toInt().coerceAtLeast(1)
        vm.renderizar(buffer, vw, vh)
        bmp.setPixels(buffer, 0, vw, 0, 0, vw, vh)
        drawImage(bmp.asImageBitmap())

        // Vista previa del trazo en curso (el motor la rastrea al soltar).
        val puntos = trazo
        if (puntos != null && puntos.size >= 2) {
            val path = Path()
            path.moveTo(
                puntos[0] * camara.zoom - camara.panX,
                puntos[1] * camara.zoom - camara.panY,
            )
            var i = 2
            while (i + 1 < puntos.size) {
                path.lineTo(
                    puntos[i] * camara.zoom - camara.panX,
                    puntos[i + 1] * camara.zoom - camara.panY,
                )
                i += 2
            }
            drawPath(
                path,
                color = colorLapiz,
                style = Stroke(width = lapiz.grosor * camara.zoom),
            )
        }
    }
}

@Composable
private fun BarraHerramientas(estado: DibujoEstado, vm: DibujoViewModel) {
    Surface(shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth()) {
            // Fila 1: paleta de colores.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Colores.PALETA.forEach { color ->
                    CabezaColor(
                        color = color,
                        seleccionado = estado.lapiz.color == color.argb,
                        onElegir = { vm.elegirColor(color.argb) },
                    )
                }
            }

            // Fila 2: modo, herramientas, grosor y acciones.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val etqModo = if (estado.modo == Modo.EDICION) {
                    stringResource(R.string.modo_editar)
                } else {
                    stringResource(R.string.modo_mover)
                }
                FilterChip(
                    selected = true,
                    onClick = { vm.alternarModo() },
                    label = { Text(etqModo) },
                    modifier = Modifier.semantics {
                        contentDescription =
                            if (estado.modo == Modo.EDICION) ""
                            else etqModo
                    },
                )

                Herramientas(estado, vm)

                BotonTexto(stringResource(R.string.grosor_menos), stringResource(R.string.cd_grosor_menos)) {
                    vm.cambiarGrosor(-1)
                }
                Text(
                    stringResource(R.string.estado_grosor, estado.lapiz.grosor),
                    style = MaterialTheme.typography.bodyMedium,
                )
                BotonTexto(stringResource(R.string.grosor_mas), stringResource(R.string.cd_grosor_mas)) {
                    vm.cambiarGrosor(1)
                }

                BotonTexto(stringResource(R.string.accion_deshacer), stringResource(R.string.cd_deshacer)) { vm.deshacer() }
                BotonTexto(stringResource(R.string.accion_rehacer), stringResource(R.string.cd_rehacer)) { vm.rehacer() }
                BotonTexto(stringResource(R.string.accion_centrar), stringResource(R.string.cd_centrar)) { vm.centrar() }
                BotonTexto(stringResource(R.string.accion_limpiar), stringResource(R.string.cd_limpiar)) { vm.limpiar() }
            }
        }
    }
}

@Composable
private fun CabezaColor(
    color: com.dibujo.core.Color,
    seleccionado: Boolean,
    onElegir: () -> Unit,
) {
    val descripcion = stringResource(R.string.cd_color, color.nombre)
    Box(
        Modifier
            .size(32.dp)
            .background(Color(color.argb), CircleShape)
            .border(
                width = if (seleccionado) 3.dp else 1.dp,
                color = if (seleccionado) MaterialTheme.colorScheme.primary else Color.LightGray,
                shape = CircleShape,
            )
            .clickable(onClick = onElegir)
            .semantics { contentDescription = descripcion },
    )
}

@Composable
private fun BotonTexto(simbolo: String, descripcion: String, alPulsar: () -> Unit) {
    IconButton(
        onClick = alPulsar,
        modifier = Modifier.semantics { contentDescription = descripcion },
    ) {
        Text(simbolo, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Herramientas(
    estado: DibujoEstado,
    vm: DibujoViewModel,
) {
    val pares = listOf(
        Herramienta.LAPIZ to R.string.herramienta_lapiz,
        Herramienta.PORTAMINA to R.string.herramienta_portamina,
        Herramienta.CARBONCILLO to R.string.herramienta_carboncillo,
        Herramienta.BORRADOR to R.string.herramienta_borrador,
    )
    pares.forEach { (herramienta, etiquetaRes) ->
        FilterChip(
            selected = estado.lapiz.herramienta == herramienta,
            onClick = { vm.elegirHerramienta(herramienta) },
            label = { Text(stringResource(etiquetaRes)) },
        )
    }
}
